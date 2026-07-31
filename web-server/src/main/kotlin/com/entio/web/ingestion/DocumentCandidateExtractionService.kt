package com.entio.web.ingestion

import com.entio.core.DocumentCandidateExtractionCategory
import com.entio.core.DocumentCandidateHint
import com.entio.core.DocumentCandidateHintRole
import com.entio.core.DocumentCandidateOrigin
import com.entio.core.DocumentEvidenceId
import com.entio.core.DocumentGroundedCandidate
import com.entio.core.DocumentGroundedEvidenceSpan
import com.entio.core.DocumentTextBlockId
import com.entio.core.IngestionDocument
import com.entio.core.LocatedDocumentTextBlock
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import opennlp.tools.lemmatizer.LemmatizerME
import opennlp.tools.lemmatizer.LemmatizerModel
import opennlp.tools.postag.POSModel
import opennlp.tools.postag.POSTaggerME
import opennlp.tools.sentdetect.SentenceDetectorME
import opennlp.tools.sentdetect.SentenceModel
import opennlp.tools.tokenize.TokenizerME
import opennlp.tools.tokenize.TokenizerModel
import opennlp.tools.util.Span

internal fun interface DocumentNlpResourceLoader {
    fun open(name: String): java.io.InputStream?
}

private data class NlpToken(
    val text: String,
    val lemma: String,
    val tag: String,
    val start: Int,
    val end: Int,
)

private data class ExtractedCandidateSpan(
    val category: DocumentCandidateExtractionCategory,
    val displayText: String,
    val start: Int,
    val end: Int,
    val hints: List<DocumentCandidateHint> = emptyList(),
)

/** Deterministic local candidate extraction over already verified English text blocks. */
internal class DocumentCandidateExtractionService(
    private val configuration: DocumentIngestionConfiguration,
    private val resourceLoader: DocumentNlpResourceLoader = DocumentNlpResourceLoader { name ->
        DocumentCandidateExtractionService::class.java.classLoader.getResourceAsStream(name)
    },
) {
    private val pipeline: NlpPipeline by lazy(LazyThreadSafetyMode.SYNCHRONIZED, ::loadPipeline)

    fun extract(document: IngestionDocument, blocks: List<LocatedDocumentTextBlock>): List<DocumentGroundedCandidate> {
        try {
            require(blocks.isNotEmpty())
            require(blocks.all { it.documentId == document.id })
            require(blocks == blocks.sortedBy(LocatedDocumentTextBlock::stableOrderingKey))
            val raw = blocks.flatMap { block -> extractBlock(document, block) }
            return raw.distinctBy { candidate ->
                listOf(
                    candidate.category.name,
                    candidate.documentId.value,
                    candidate.evidenceSpans.single().stableOrderingKey,
                    candidate.normalizedText,
                )
            }.sortedBy(DocumentGroundedCandidate::stableOrderingKey)
        } catch (failure: DocumentIngestionFailure) {
            throw failure
        } catch (_: Exception) {
            throw DocumentIngestionFailure(
                "document-candidate-extraction-failed",
                "Local document candidate extraction could not complete.",
            )
        }
    }

    private fun extractBlock(
        document: IngestionDocument,
        block: LocatedDocumentTextBlock,
    ): List<DocumentGroundedCandidate> {
        val candidates = mutableListOf<ExtractedCandidateSpan>()
        pipeline.sentences(block.exactText).forEach { sentence ->
            val sentenceText = block.exactText.substring(sentence.start, sentence.end)
            val tokens = pipeline.tokens(sentenceText, sentence.start)
            candidates += regexCandidates(sentenceText, sentence.start)
            candidates += namedCandidates(tokens)
            candidates += conceptCandidates(tokens)
            candidates += relationshipCandidates(tokens)
            candidates += ruleCandidates(sentenceText, sentence.start)
        }
        candidates += contextualClassificationCandidate(block)
        return candidates
            .filter { it.start >= 0 && it.end <= block.exactText.length && it.start < it.end }
            .map { span -> toCandidate(document, block, span) }
    }

    private fun regexCandidates(text: String, baseOffset: Int): List<ExtractedCandidateSpan> = buildList {
        findAll(MONEY, text, DocumentCandidateExtractionCategory.MonetaryAmount, baseOffset, this)
        findAll(DATE, text, DocumentCandidateExtractionCategory.Date, baseOffset, this)
        findAll(IDENTIFIER, text, DocumentCandidateExtractionCategory.Identifier, baseOffset, this)
        findAll(ATTRIBUTE_VALUE, text, DocumentCandidateExtractionCategory.AttributeValue, baseOffset, this)
    }

    private fun namedCandidates(tokens: List<NlpToken>): List<ExtractedCandidateSpan> = buildList {
        var index = 0
        while (index < tokens.size) {
            if (tokens[index].tag !in properNounTags) {
                index += 1
                continue
            }
            val start = index
            while (index + 1 < tokens.size && tokens[index + 1].tag in properNounTags) index += 1
            val group = tokens.subList(start, index + 1)
            val text = group.joinToString(" ", transform = NlpToken::text)
            val category = when {
                group.any { it.lemma.lowercase(Locale.ROOT) in organizationSuffixes } ->
                    DocumentCandidateExtractionCategory.Organization
                tokens.getOrNull(start - 1)?.lemma?.lowercase(Locale.ROOT) in locationPrepositions ->
                    DocumentCandidateExtractionCategory.Location
                group.size >= 2 -> DocumentCandidateExtractionCategory.Person
                else -> DocumentCandidateExtractionCategory.ConceptTerm
            }
            add(ExtractedCandidateSpan(category, text, group.first().start, group.last().end))
            index += 1
        }
    }

    private fun conceptCandidates(tokens: List<NlpToken>): List<ExtractedCandidateSpan> = buildList {
        var index = 0
        while (index < tokens.size) {
            if (!tokens[index].isNoun && !tokens[index].isAdjective) {
                index += 1
                continue
            }
            val start = index
            while (index + 1 < tokens.size &&
                (tokens[index + 1].isNoun || tokens[index + 1].isAdjective)
            ) index += 1
            val group = tokens.subList(start, index + 1)
            if (group.any(NlpToken::isNoun)) {
                add(
                    ExtractedCandidateSpan(
                        DocumentCandidateExtractionCategory.ConceptTerm,
                        group.joinToString(" ", transform = NlpToken::text),
                        group.first().start,
                        group.last().end,
                    ),
                )
            }
            index += 1
        }
    }

    private fun relationshipCandidates(tokens: List<NlpToken>): List<ExtractedCandidateSpan> = tokens.mapIndexedNotNull { index, token ->
        if (!token.isVerb) return@mapIndexedNotNull null
        val subject = tokens.take(index).lastOrNull(NlpToken::isNoun)
        val target = tokens.drop(index + 1).firstOrNull(NlpToken::isNoun)
        val hints = listOfNotNull(
            subject?.let { DocumentCandidateHint(DocumentCandidateHintRole.Subject, it.text) },
            DocumentCandidateHint(DocumentCandidateHintRole.Predicate, token.lemma),
            target?.let { DocumentCandidateHint(DocumentCandidateHintRole.Object, it.text) },
        ).sortedBy(DocumentCandidateHint::stableOrderingKey)
        ExtractedCandidateSpan(
            DocumentCandidateExtractionCategory.RelationshipPhrase,
            token.text,
            token.start,
            token.end,
            hints,
        )
    }

    private fun ruleCandidates(text: String, baseOffset: Int): List<ExtractedCandidateSpan> = RULE.findAll(text).map { match ->
        val end = text.indexOfAny(charArrayOf('.', ';'), match.range.first).let { if (it < 0) text.length else it }
        ExtractedCandidateSpan(
            DocumentCandidateExtractionCategory.RuleCue,
            text.substring(match.range.first, end).trim(),
            baseOffset + match.range.first,
            baseOffset + end,
        )
    }.toList()

    private fun contextualClassificationCandidate(block: LocatedDocumentTextBlock): List<ExtractedCandidateSpan> {
        val heading = block.sectionHeading?.lowercase(Locale.ROOT).orEmpty()
        val category = when {
            heading in administrativeHeadings -> DocumentCandidateExtractionCategory.Administrative
            heading in illustrativeHeadings || block.exactText.trimStart().startsWith("For example", ignoreCase = true) ->
                DocumentCandidateExtractionCategory.Illustrative
            else -> return emptyList()
        }
        return listOf(
            ExtractedCandidateSpan(
                category,
                block.exactText,
                0,
                block.exactText.length,
            ),
        )
    }

    private fun toCandidate(
        document: IngestionDocument,
        block: LocatedDocumentTextBlock,
        span: ExtractedCandidateSpan,
    ): DocumentGroundedCandidate {
        val exact = block.exactText.substring(span.start, span.end)
        val normalized = normalize(span.displayText)
        val referenceId = DocumentEvidenceId(
            "reference-${stableId(document.checksumSha256, block.id.value, span.start.toString(), span.end.toString(), exact)}",
        )
        val evidenceId = DocumentEvidenceId("evidence-${stableId(document.checksumSha256, referenceId.value)}")
        val evidenceSpan = DocumentGroundedEvidenceSpan(
            evidenceId = evidenceId,
            referenceId = referenceId,
            documentId = document.id,
            blockId = block.id,
            pageNumber = block.pageNumber,
            section = block.sectionHeading,
            startOffsetInBlock = span.start,
            endOffsetInBlock = span.end,
            exactText = exact,
        )
        val id = "candidate-${stableId(
            document.checksumSha256,
            evidenceSpan.stableOrderingKey,
            normalized,
            span.category.name,
            configuration.candidateExtractorContractVersion,
            configuration.nlpResourceVersion,
        )}"
        return DocumentGroundedCandidate(
            id = id,
            origin = DocumentCandidateOrigin.LocalNlp,
            category = span.category,
            displayText = span.displayText.trim(),
            normalizedText = normalized,
            documentId = document.id,
            documentChecksumSha256 = document.checksumSha256,
            evidenceSpans = listOf(evidenceSpan),
            hints = span.hints,
            extractorContractVersion = configuration.candidateExtractorContractVersion,
            resourceVersion = configuration.nlpResourceVersion,
        )
    }

    private fun loadPipeline(): NlpPipeline = try {
        NlpPipeline(
            sentenceModel = resource(SENTENCE_MODEL) { SentenceModel(it) },
            tokenizerModel = resource(TOKENIZER_MODEL) { TokenizerModel(it) },
            posModel = resource(POS_MODEL) { POSModel(it) },
            lemmatizerModel = resource(LEMMATIZER_MODEL) { LemmatizerModel(it) },
        )
    } catch (_: Exception) {
        throw DocumentIngestionFailure(
            "document-candidate-extraction-failed",
            "Local document candidate extraction resources are unavailable.",
        )
    }

    private fun <T> resource(name: String, create: (java.io.InputStream) -> T): T =
        resourceLoader.open(name)?.use(create)
            ?: throw IllegalStateException("Missing approved NLP resource.")

    private class NlpPipeline(
        sentenceModel: SentenceModel,
        tokenizerModel: TokenizerModel,
        posModel: POSModel,
        lemmatizerModel: LemmatizerModel,
    ) {
        private val sentenceDetector = SentenceDetectorME(sentenceModel)
        private val tokenizer = TokenizerME(tokenizerModel)
        private val posTagger = POSTaggerME(posModel)
        private val lemmatizer = LemmatizerME(lemmatizerModel)

        @Synchronized
        fun sentences(text: String): Array<Span> = sentenceDetector.sentPosDetect(text)

        @Synchronized
        fun tokens(text: String, baseOffset: Int): List<NlpToken> {
            val spans = tokenizer.tokenizePos(text)
            val values = spans.map { text.substring(it.start, it.end) }.toTypedArray()
            val tags = posTagger.tag(values)
            val lemmas = lemmatizer.lemmatize(values, tags)
            return spans.indices.map { index ->
                NlpToken(
                    text = values[index],
                    lemma = lemmas[index].takeUnless { it == "O" } ?: values[index],
                    tag = tags[index],
                    start = baseOffset + spans[index].start,
                    end = baseOffset + spans[index].end,
                )
            }
        }
    }

    private companion object {
        private const val SENTENCE_MODEL = "opennlp-en-ud-ewt-sentence-1.3-2.5.4.bin"
        private const val TOKENIZER_MODEL = "opennlp-en-ud-ewt-tokens-1.3-2.5.4.bin"
        private const val POS_MODEL = "opennlp-en-ud-ewt-pos-1.3-2.5.4.bin"
        private const val LEMMATIZER_MODEL = "opennlp-en-ud-ewt-lemmas-1.3-2.5.4.bin"
        private val MONEY = Regex("(?:USD\\s*)?[$€£]\\s?\\d[\\d,]*(?:\\.\\d{2})?|USD\\s+\\d[\\d,]*(?:\\.\\d{2})?", RegexOption.IGNORE_CASE)
        private val DATE = Regex("\\b(?:\\d{4}-\\d{2}-\\d{2}|(?:January|February|March|April|May|June|July|August|September|October|November|December)\\s+\\d{1,2},?\\s+\\d{4})\\b", RegexOption.IGNORE_CASE)
        private val IDENTIFIER = Regex("\\b(?=[A-Z0-9-]*[A-Z])(?=[A-Z0-9-]*\\d)[A-Z][A-Z0-9]*(?:-[A-Z0-9]+)+\\b")
        private val ATTRIBUTE_VALUE = Regex("\\b[A-Za-z][A-Za-z ]{1,40}:\\s*[^.;\\n]{1,80}")
        private val RULE = Regex("\\b(?:must|shall|may not|must not|prohibited|requires?|only if|at least|at most)\\b", RegexOption.IGNORE_CASE)
        private val properNounTags = setOf("NNP", "NNPS", "PROPN")
        private val organizationSuffixes = setOf("llc", "inc", "bank", "group", "company", "corporation", "association")
        private val locationPrepositions = setOf("in", "at", "from", "within")
        private val administrativeHeadings = setOf("metadata", "document control", "revision history", "table of contents")
        private val illustrativeHeadings = setOf("example", "examples", "illustration", "scenario")

        private fun findAll(
            regex: Regex,
            text: String,
            category: DocumentCandidateExtractionCategory,
            baseOffset: Int,
            destination: MutableList<ExtractedCandidateSpan>,
        ): Unit = regex.findAll(text).forEach { match ->
            destination += ExtractedCandidateSpan(
                category,
                match.value,
                baseOffset + match.range.first,
                baseOffset + match.range.last + 1,
            )
        }

        private fun normalize(value: String): String = value
            .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
            .lowercase(Locale.ROOT)

        private fun stableId(vararg values: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            values.forEach { value ->
                val bytes = value.toByteArray(StandardCharsets.UTF_8)
                digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
                digest.update(bytes)
            }
            return digest.digest().joinToString("") { "%02x".format(it) }.take(32)
        }
    }
}

private val NlpToken.isNoun: Boolean
    get() = tag.startsWith("NN") || tag in setOf("NOUN", "PROPN")

private val NlpToken.isAdjective: Boolean
    get() = tag.startsWith("JJ") || tag == "ADJ"

private val NlpToken.isVerb: Boolean
    get() = tag.startsWith("VB") || tag in setOf("VERB", "AUX")
