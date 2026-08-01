package com.entio.web.ingestion

import com.entio.core.DocumentCandidateExtractionCategory
import com.entio.core.DocumentCandidateExtractionResult
import com.entio.core.DocumentCandidateHint
import com.entio.core.DocumentCandidateHintRole
import com.entio.core.DocumentCandidateOrigin
import com.entio.core.DocumentCandidatePromotionReason
import com.entio.core.DocumentEvidenceId
import com.entio.core.DocumentEvidenceMention
import com.entio.core.DocumentGroundedCandidate
import com.entio.core.DocumentGroundedEvidenceSpan
import com.entio.core.DocumentMentionCoverageDisposition
import com.entio.core.DocumentMentionCoverageKind
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

private data class ExtractedMentionSpan(
    val category: DocumentCandidateExtractionCategory,
    val displayText: String,
    val normalizedText: String,
    val start: Int,
    val end: Int,
    val hints: List<DocumentCandidateHint> = emptyList(),
    val promotionSignals: List<DocumentCandidatePromotionReason> = emptyList(),
)

private data class DocumentTextSegment(
    val text: String,
    val start: Int,
)

/** Deterministic mention extraction, safe grouping, and ontology-candidate promotion. */
internal class DocumentCandidateExtractionService(
    private val configuration: DocumentIngestionConfiguration,
    private val resourceLoader: DocumentNlpResourceLoader = DocumentNlpResourceLoader { name ->
        DocumentCandidateExtractionService::class.java.classLoader.getResourceAsStream(name)
    },
) {
    private val pipeline: NlpPipeline by lazy(LazyThreadSafetyMode.SYNCHRONIZED, ::loadPipeline)

    fun extractMentions(
        document: IngestionDocument,
        blocks: List<LocatedDocumentTextBlock>,
    ): List<DocumentEvidenceMention> {
        try {
            require(blocks.isNotEmpty())
            require(blocks.all { it.documentId == document.id })
            require(blocks == blocks.sortedBy(LocatedDocumentTextBlock::stableOrderingKey))
            val repeatedBoilerplate = repeatedBoilerplateLines(blocks)
            return blocks.flatMap { block -> extractBlock(document, block, repeatedBoilerplate) }
                .distinctBy { mention ->
                    listOf(mention.category.name, mention.evidenceSpan.stableOrderingKey, mention.normalizedText)
                }
                .sortedBy(DocumentEvidenceMention::stableOrderingKey)
        } catch (failure: DocumentIngestionFailure) {
            throw failure
        } catch (failure: Exception) {
            if (System.getenv("ENTIO_DOCUMENT_ANALYSIS_DEBUG") == "true") {
                val site = failure.stackTrace.firstOrNull { it.className.startsWith("com.entio.") }
                val contractCode = when (failure.message) {
                    "Document evidence mention normalized text must be trimmed, nonblank, and at most 2000 characters." ->
                        "document-evidence-mention-normalized-text-invalid"
                    else -> "unclassified"
                }
                System.err.println(
                    "entio-document-analysis candidate-extraction-failure=${failure::class.simpleName} " +
                        "site=${site?.className.orEmpty()}:${site?.lineNumber ?: -1} " +
                        "contract=$contractCode",
                )
            }
            throw DocumentIngestionFailure(
                "document-candidate-extraction-failed",
                "Local document mention extraction could not complete.",
            )
        }
    }

    fun promoteCandidates(
        mentions: List<DocumentEvidenceMention>,
        strongOntologyLabels: Set<String> = emptySet(),
    ): DocumentCandidateExtractionResult {
        require(mentions.isNotEmpty())
        require(mentions == mentions.distinctBy(DocumentEvidenceMention::id).sortedBy(DocumentEvidenceMention::stableOrderingKey))
        val normalizedLabels = strongOntologyLabels.map(::normalizeOntologyLabel).filter(String::isNotBlank).toSet()
        val relationshipParticipants = mentions
            .filter { it.category == DocumentCandidateExtractionCategory.RelationshipPhrase }
            .flatMap { relation -> relation.hints.filter { it.role in setOf(DocumentCandidateHintRole.Subject, DocumentCandidateHintRole.Object) } }
            .map { normalize(it.text) }
            .toSet()
        val grouped = mentions.groupBy { mention -> groupingFamily(mention.category) to mention.normalizedText }
            .toSortedMap(compareBy<Pair<String, String>> { it.first }.thenBy { it.second })
        val candidates = mutableListOf<DocumentGroundedCandidate>()
        val coverage = mutableListOf<DocumentMentionCoverageDisposition>()

        grouped.values.forEach { unsortedGroup ->
            val group = unsortedGroup.sortedBy(DocumentEvidenceMention::stableOrderingKey)
            val first = group.minWith(
                compareBy<DocumentEvidenceMention> { candidateCategoryPriority.getValue(it.category) }
                    .thenBy(DocumentEvidenceMention::stableOrderingKey),
            )
            val reasons = buildList {
                addAll(group.flatMap(DocumentEvidenceMention::promotionSignals))
                if (first.normalizedText in normalizedLabels) add(DocumentCandidatePromotionReason.StrongOntologyMatch)
                if (first.normalizedText in relationshipParticipants) add(DocumentCandidatePromotionReason.RelationshipParticipant)
                if (group.map { it.evidenceSpan.blockId }.distinct().size >= 2 && group.any { it.category.isMeaningBearing }) {
                    add(DocumentCandidatePromotionReason.RepeatedMeaningfulContext)
                }
            }.distinct().sortedBy { it.ordinal }
            val singleWordConceptIsGrounded =
                first.category != DocumentCandidateExtractionCategory.ConceptTerm ||
                    first.normalizedText.split(' ').count(String::isNotBlank) > 1 ||
                    DocumentCandidatePromotionReason.StrongOntologyMatch in reasons ||
                    DocumentCandidatePromotionReason.NamedEntity in reasons ||
                    DocumentCandidatePromotionReason.DefinedOrDescribed in reasons ||
                    group.flatMap(DocumentEvidenceMention::hints).any {
                        it.role == DocumentCandidateHintRole.Attribute
                    } ||
                    DocumentCandidatePromotionReason.RelationshipParticipant in reasons
            val relationshipIsGrounded =
                first.category != DocumentCandidateExtractionCategory.RelationshipPhrase ||
                    DocumentCandidatePromotionReason.ConnectedRelationship in reasons ||
                    DocumentCandidatePromotionReason.RepeatedMeaningfulContext in reasons ||
                    DocumentCandidatePromotionReason.RuleOrRequirement in reasons ||
                    group.flatMap(DocumentEvidenceMention::hints)
                        .filter { it.role in setOf(DocumentCandidateHintRole.Subject, DocumentCandidateHintRole.Object) }
                        .any { hint -> normalizedLabels.any { label -> normalize(hint.text).containsNormalizedPhrase(label) } }
            val disposition = when {
                group.all { it.category in supportingValueCategories } -> DocumentMentionCoverageKind.SupportingValue
                group.all { it.category in documentOnlyCategories } -> DocumentMentionCoverageKind.DocumentOnly
                first.isLowValue && DocumentCandidatePromotionReason.StrongOntologyMatch !in reasons ->
                    DocumentMentionCoverageKind.Rejected
                !singleWordConceptIsGrounded || !relationshipIsGrounded -> DocumentMentionCoverageKind.Rejected
                reasons.isEmpty() -> DocumentMentionCoverageKind.Rejected
                else -> null
            }
            if (disposition != null) {
                val reason = when (disposition) {
                    DocumentMentionCoverageKind.SupportingValue -> "supporting-value"
                    DocumentMentionCoverageKind.DocumentOnly -> "document-only"
                    else -> if (first.isLowValue) "low-value-mention" else "candidate-promotion-gate-not-met"
                }
                group.forEach { mention -> coverage += DocumentMentionCoverageDisposition(mention.id, disposition, reasonCode = reason) }
                return@forEach
            }

            val mentionIds = group.map(DocumentEvidenceMention::id).sorted()
            val representativeEvidence = representativeEvidence(group)
            val id = "candidate-${stableId(
                first.category.name,
                first.normalizedText,
                mentionIds.joinToString("|"),
                configuration.candidateExtractorContractVersion,
                configuration.nlpResourceVersion,
            )}"
            candidates += DocumentGroundedCandidate(
                id = id,
                origin = DocumentCandidateOrigin.LocalNlp,
                category = first.category,
                displayText = group.first().displayText.trim(),
                normalizedText = first.normalizedText,
                documentId = representativeEvidence.first().documentId,
                documentChecksumSha256 = group.first { it.evidenceSpan.documentId == representativeEvidence.first().documentId }
                    .documentChecksumSha256,
                evidenceSpans = representativeEvidence,
                hints = group.flatMap(DocumentEvidenceMention::hints).distinct().sortedBy(DocumentCandidateHint::stableOrderingKey),
                extractorContractVersion = configuration.candidateExtractorContractVersion,
                resourceVersion = configuration.nlpResourceVersion,
                mentionIds = mentionIds,
                promotionReasons = reasons,
            )
            group.forEachIndexed { index, mention ->
                coverage += DocumentMentionCoverageDisposition(
                    mention.id,
                    if (index == 0) DocumentMentionCoverageKind.Promoted else DocumentMentionCoverageKind.MergedIntoCandidate,
                    id,
                    if (index == 0) "candidate-promoted" else "safe-normalized-equivalent",
                )
            }
        }
        val linkedCandidates = linkRelatedCandidates(candidates)
        return DocumentCandidateExtractionResult(
            mentions,
            linkedCandidates,
            coverage.sortedBy(DocumentMentionCoverageDisposition::stableOrderingKey),
        )
    }

    private fun extractBlock(
        document: IngestionDocument,
        block: LocatedDocumentTextBlock,
        repeatedBoilerplate: Set<String>,
    ): List<DocumentEvidenceMention> {
        contextualClassificationMention(block)?.let { return listOf(toMention(document, block, it)) }
        val mentions = mutableListOf<ExtractedMentionSpan>()
        var activeDocumentOnlySection: DocumentCandidateExtractionCategory? = null
        contentLines(block.exactText).forEach { line ->
            documentOnlySectionCategory(line.text)?.let { category ->
                activeDocumentOnlySection = category
                mentions += documentOnlyMention(line, category)
                return@forEach
            }
            if (isSectionHeadingLine(line.text)) {
                activeDocumentOnlySection = null
                return@forEach
            }
            activeDocumentOnlySection?.let { category ->
                mentions += documentOnlyMention(line, category)
                return@forEach
            }
            if (normalize(line.text) in repeatedBoilerplate || isLikelyNonBodyLine(line.text)) return@forEach
            pipeline.sentences(line.text).forEach { sentence ->
                val sentenceText = line.text.substring(sentence.start, sentence.end)
                val sentenceStart = line.start + sentence.start
                if (isIllustrativeSentence(sentenceText)) {
                    mentions += ExtractedMentionSpan(
                        DocumentCandidateExtractionCategory.Illustrative,
                        sentenceText.trim(),
                        normalize(sentenceText),
                        sentenceStart,
                        sentenceStart + sentenceText.length,
                    )
                    return@forEach
                }
                val tokens = pipeline.tokens(sentenceText, sentenceStart)
                val ruleContext = RULE.containsMatchIn(sentenceText)
                mentions += regexMentions(sentenceText, sentenceStart)
                mentions += namedMentions(tokens)
                mentions += conceptMentions(tokens)
                mentions += relationshipMentions(tokens, ruleContext)
                mentions += ruleMentions(sentenceText, sentenceStart)
            }
        }
        val supportingRanges = mentions.filter { it.category in supportingValueCategories }.map { it.start until it.end }
        return mentions
            .filter { mention ->
                mention.category in supportingValueCategories || supportingRanges.none { range ->
                    mention.start >= range.first && mention.end <= range.last + 1
                }
            }
            .filter { it.normalizedText.isNotBlank() }
            .filter { it.start >= 0 && it.end <= block.exactText.length && it.start < it.end }
            .map { span -> toMention(document, block, span) }
    }

    private fun regexMentions(text: String, baseOffset: Int): List<ExtractedMentionSpan> = buildList {
        findAll(MONEY, text, DocumentCandidateExtractionCategory.MonetaryAmount, baseOffset, this)
        findAll(DATE, text, DocumentCandidateExtractionCategory.Date, baseOffset, this)
        findAll(IDENTIFIER, text, DocumentCandidateExtractionCategory.Identifier, baseOffset, this)
        findAll(DURATION, text, DocumentCandidateExtractionCategory.AttributeValue, baseOffset, this)
        findAll(PERCENTAGE, text, DocumentCandidateExtractionCategory.AttributeValue, baseOffset, this)
        addAll(attributeMentions(text, baseOffset))
    }

    private fun attributeMentions(text: String, baseOffset: Int): List<ExtractedMentionSpan> =
        ATTRIBUTE_VALUE.findAll(text).flatMap { match ->
            val attributeGroup = requireNotNull(match.groups[1])
            val valueGroup = requireNotNull(match.groups[2])
            val attribute = attributeGroup.value.trim()
            val value = valueGroup.value.trim()
            val attributeStart = baseOffset + attributeGroup.range.first + attributeGroup.value.indexOf(attribute)
            val valueStart = baseOffset + valueGroup.range.first + valueGroup.value.indexOf(value)
            sequenceOf(
                ExtractedMentionSpan(
                    category = DocumentCandidateExtractionCategory.ConceptTerm,
                    displayText = attribute,
                    normalizedText = normalize(attribute),
                    start = attributeStart,
                    end = attributeStart + attribute.length,
                    hints = listOf(
                        DocumentCandidateHint(DocumentCandidateHintRole.Attribute, attribute),
                        DocumentCandidateHint(DocumentCandidateHintRole.Value, value),
                    ).sortedBy(DocumentCandidateHint::stableOrderingKey),
                    promotionSignals = listOf(DocumentCandidatePromotionReason.ConnectedRelationship),
                ),
                ExtractedMentionSpan(
                    category = DocumentCandidateExtractionCategory.AttributeValue,
                    displayText = value,
                    normalizedText = normalize(value),
                    start = valueStart,
                    end = valueStart + value.length,
                    hints = listOf(
                        DocumentCandidateHint(DocumentCandidateHintRole.Attribute, attribute),
                        DocumentCandidateHint(DocumentCandidateHintRole.Value, value),
                    ).sortedBy(DocumentCandidateHint::stableOrderingKey),
                ),
            )
        }.toList()

    private fun linkRelatedCandidates(candidates: List<DocumentGroundedCandidate>): List<DocumentGroundedCandidate> {
        val aliases = buildMap<String, MutableSet<String>> {
            candidates.filter { it.category != DocumentCandidateExtractionCategory.RelationshipPhrase }.forEach { candidate ->
                safeCandidateAliases(candidate.normalizedText).forEach { alias ->
                    getOrPut(alias) { mutableSetOf() }.add(candidate.id)
                }
            }
        }
        return candidates.map { candidate ->
            if (candidate.category != DocumentCandidateExtractionCategory.RelationshipPhrase) return@map candidate
            candidate.copy(
                hints = candidate.hints.map { hint ->
                    if (hint.role !in setOf(DocumentCandidateHintRole.Subject, DocumentCandidateHintRole.Object)) {
                        return@map hint
                    }
                    val relatedIds = safeCandidateAliases(hint.text)
                        .flatMap { aliases[it].orEmpty() }
                        .distinct()
                        .toList()
                    if (relatedIds.size == 1) hint.copy(relatedCandidateId = relatedIds.single()) else hint
                }.sortedBy(DocumentCandidateHint::stableOrderingKey),
            )
        }.sortedBy(DocumentGroundedCandidate::stableOrderingKey)
    }

    private fun namedMentions(tokens: List<NlpToken>): List<ExtractedMentionSpan> = nounPhrases(tokens)
        .filter { group ->
            group.all { it.tag in properNounTags } ||
                group.any { it.lemma.lowercase(Locale.ROOT) in organizationSuffixes }
        }
        .map { group ->
            val namedEntity = group.size >= 2 ||
                group.any { it.lemma.lowercase(Locale.ROOT) in organizationSuffixes } ||
                group.singleOrNull()?.text?.matches(ACRONYM) == true
            val category = when {
                group.any { it.lemma.lowercase(Locale.ROOT) in organizationSuffixes } -> DocumentCandidateExtractionCategory.Organization
                tokens.getOrNull(tokens.indexOf(group.first()) - 1)?.lemma?.lowercase(Locale.ROOT) in locationPrepositions ->
                    DocumentCandidateExtractionCategory.Location
                group.size >= 2 -> DocumentCandidateExtractionCategory.Person
                else -> DocumentCandidateExtractionCategory.ConceptTerm
            }
            mention(
                group,
                category,
                if (namedEntity) listOf(DocumentCandidatePromotionReason.NamedEntity) else emptyList(),
            )
        }

    private fun conceptMentions(tokens: List<NlpToken>): List<ExtractedMentionSpan> {
        val phrases = nounPhrases(tokens).filter { it.any(NlpToken::isNoun) && it.size <= MAX_NOUN_PHRASE_TOKENS }
        val definitionCue = tokens.firstOrNull { it.normalizedLemma in definitionVerbs }
        val definedSubject = definitionCue?.let { cue -> phrases.lastOrNull { it.last().end <= cue.start } }
        return phrases.map { group ->
            val signals = buildList {
                if (group === definedSubject) add(DocumentCandidatePromotionReason.DefinedOrDescribed)
            }
            mention(
                group,
                DocumentCandidateExtractionCategory.ConceptTerm,
                signals,
            )
        }
    }

    private fun relationshipMentions(tokens: List<NlpToken>, ruleContext: Boolean): List<ExtractedMentionSpan> {
        val phrases = nounPhrases(tokens)
        return tokens.mapNotNull { token ->
            if (!token.isVerb || token.lemma.lowercase(Locale.ROOT) in genericRelationVerbs) return@mapNotNull null
            val subject = phrases.lastOrNull { it.last().end <= token.start } ?: return@mapNotNull null
            val target = phrases.firstOrNull { it.first().start >= token.end } ?: return@mapNotNull null
            if (subject.size > MAX_NOUN_PHRASE_TOKENS || target.size > MAX_NOUN_PHRASE_TOKENS) return@mapNotNull null
            if (token.start - subject.last().end > MAX_RELATIONSHIP_GAP_CHARACTERS ||
                target.first().start - token.end > MAX_RELATIONSHIP_GAP_CHARACTERS
            ) return@mapNotNull null
            val predicate = token.lemma.lowercase(Locale.ROOT)
            val objectText = target.joinToString(" ", transform = NlpToken::text)
            ExtractedMentionSpan(
                DocumentCandidateExtractionCategory.RelationshipPhrase,
                token.text,
                predicate,
                token.start,
                token.end,
                listOf(
                    DocumentCandidateHint(DocumentCandidateHintRole.Subject, subject.joinToString(" ", transform = NlpToken::text)),
                    DocumentCandidateHint(DocumentCandidateHintRole.Predicate, predicate),
                    DocumentCandidateHint(DocumentCandidateHintRole.Object, objectText),
                ).sortedBy(DocumentCandidateHint::stableOrderingKey),
                buildList {
                    add(DocumentCandidatePromotionReason.ConnectedRelationship)
                    if (ruleContext) add(DocumentCandidatePromotionReason.RuleOrRequirement)
                },
            )
        }
    }

    private fun documentOnlyMention(
        line: DocumentTextSegment,
        category: DocumentCandidateExtractionCategory,
    ): ExtractedMentionSpan = ExtractedMentionSpan(
        category = category,
        displayText = line.text,
        normalizedText = normalize(line.text),
        start = line.start,
        end = line.start + line.text.length,
    )

    private fun ruleMentions(text: String, baseOffset: Int): List<ExtractedMentionSpan> = RULE.findAll(text).map { match ->
        val end = text.indexOfAny(charArrayOf('.', ';'), match.range.first).let { if (it < 0) text.length else it }
        val display = text.substring(match.range.first, end).trim()
        ExtractedMentionSpan(
            DocumentCandidateExtractionCategory.RuleCue,
            display,
            normalize(display),
            baseOffset + match.range.first,
            baseOffset + end,
            promotionSignals = listOf(DocumentCandidatePromotionReason.RuleOrRequirement),
        )
    }.toList()

    private fun contextualClassificationMention(block: LocatedDocumentTextBlock): ExtractedMentionSpan? {
        val heading = normalize(block.sectionHeading.orEmpty())
        val category = when {
            heading in administrativeHeadings -> DocumentCandidateExtractionCategory.Administrative
            heading in illustrativeHeadings || block.exactText.trimStart().startsWith("For example", ignoreCase = true) ->
                DocumentCandidateExtractionCategory.Illustrative
            else -> return null
        }
        return ExtractedMentionSpan(category, block.exactText, normalize(block.exactText), 0, block.exactText.length)
    }

    private fun toMention(
        document: IngestionDocument,
        block: LocatedDocumentTextBlock,
        span: ExtractedMentionSpan,
    ): DocumentEvidenceMention {
        val exact = block.exactText.substring(span.start, span.end)
        val referenceId = DocumentEvidenceId(
            "reference-${stableId(document.checksumSha256, block.id.value, span.start.toString(), span.end.toString(), exact)}",
        )
        val evidenceSpan = DocumentGroundedEvidenceSpan(
            evidenceId = DocumentEvidenceId("evidence-${stableId(document.checksumSha256, referenceId.value)}"),
            referenceId = referenceId,
            documentId = document.id,
            blockId = block.id,
            pageNumber = block.pageNumber,
            section = block.sectionHeading,
            startOffsetInBlock = span.start,
            endOffsetInBlock = span.end,
            exactText = exact,
        )
        return try {
            DocumentEvidenceMention(
                id = "mention-${stableId(
                    document.checksumSha256,
                    evidenceSpan.stableOrderingKey,
                    span.normalizedText,
                    span.category.name,
                    configuration.candidateExtractorContractVersion,
                    configuration.nlpResourceVersion,
                )}",
                category = span.category,
                displayText = span.displayText.trim(),
                normalizedText = span.normalizedText,
                documentChecksumSha256 = document.checksumSha256,
                evidenceSpan = evidenceSpan,
                hints = span.hints,
                promotionSignals = span.promotionSignals.sortedBy { it.ordinal },
            )
        } catch (failure: IllegalArgumentException) {
            if (System.getenv("ENTIO_DOCUMENT_ANALYSIS_DEBUG") == "true") {
                System.err.println(
                    "entio-document-analysis candidate-span-failure category=${span.category.name} " +
                        "displayLength=${span.displayText.trim().length} normalizedLength=${span.normalizedText.length} " +
                        "normalizedBlank=${span.normalizedText.isBlank()} exactLength=${exact.length}",
                )
            }
            throw failure
        }
    }

    private fun representativeEvidence(group: List<DocumentEvidenceMention>): List<DocumentGroundedEvidenceSpan> {
        val ordered = group.sortedBy(DocumentEvidenceMention::stableOrderingKey)
        val representatives = ordered.distinctBy { it.evidenceSpan.documentId to it.evidenceSpan.blockId }.take(MAX_REPRESENTATIVE_EVIDENCE)
        return representatives.map(DocumentEvidenceMention::evidenceSpan).sortedBy(DocumentGroundedEvidenceSpan::stableOrderingKey)
    }

    private fun normalizeOntologyLabel(value: String): String = normalize(
        pipeline.tokens(value, 0).joinToString(" ") { it.normalizedLemma },
    )

    private fun loadPipeline(): NlpPipeline = try {
        NlpPipeline(
            resource(SENTENCE_MODEL) { SentenceModel(it) },
            resource(TOKENIZER_MODEL) { TokenizerModel(it) },
            resource(POS_MODEL) { POSModel(it) },
            resource(LEMMATIZER_MODEL) { LemmatizerModel(it) },
        )
    } catch (_: Exception) {
        throw DocumentIngestionFailure(
            "document-candidate-extraction-failed",
            "Local document candidate extraction resources are unavailable.",
        )
    }

    private fun <T> resource(name: String, create: (java.io.InputStream) -> T): T =
        resourceLoader.open(name)?.use(create) ?: error("Missing approved NLP resource.")

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

        @Synchronized fun sentences(text: String): Array<Span> = sentenceDetector.sentPosDetect(text)

        @Synchronized fun tokens(text: String, baseOffset: Int): List<NlpToken> {
            val spans = tokenizer.tokenizePos(text)
            val values = spans.map { text.substring(it.start, it.end) }.toTypedArray()
            val tags = posTagger.tag(values)
            val lemmas = lemmatizer.lemmatize(values, tags)
            return spans.indices.map { index ->
                NlpToken(
                    values[index],
                    lemmas[index].takeUnless { it == "O" } ?: values[index],
                    tags[index],
                    baseOffset + spans[index].start,
                    baseOffset + spans[index].end,
                )
            }
        }
    }

    private companion object {
        private const val MAX_REPRESENTATIVE_EVIDENCE = 10
        private const val SENTENCE_MODEL = "opennlp-en-ud-ewt-sentence-1.3-2.5.4.bin"
        private const val TOKENIZER_MODEL = "opennlp-en-ud-ewt-tokens-1.3-2.5.4.bin"
        private const val POS_MODEL = "opennlp-en-ud-ewt-pos-1.3-2.5.4.bin"
        private const val LEMMATIZER_MODEL = "opennlp-en-ud-ewt-lemmas-1.3-2.5.4.bin"
        private val MONEY = Regex("(?:USD\\s*)?[$€£]\\s?\\d[\\d,]*(?:\\.\\d{2})?|USD\\s+\\d[\\d,]*(?:\\.\\d{2})?", RegexOption.IGNORE_CASE)
        private val DATE = Regex("\\b(?:\\d{4}-\\d{2}-\\d{2}|(?:January|February|March|April|May|June|July|August|September|October|November|December)\\s+\\d{1,2},?\\s+\\d{4})\\b", RegexOption.IGNORE_CASE)
        private val IDENTIFIER = Regex("\\b(?=[A-Z0-9-]*[A-Z])(?=[A-Z0-9-]*\\d)[A-Z][A-Z0-9]*(?:-[A-Z0-9]+)+\\b")
        private val DURATION = Regex(
            "\\b(?:\\d+|one|two|three|four|five|six|seven|eight|nine|ten)\\s+(?:business\\s+)?(?:days?|weeks?|months?|years?)\\b",
            RegexOption.IGNORE_CASE,
        )
        private val PERCENTAGE = Regex("\\b\\d+(?:\\.\\d+)?\\s*(?:%|percent(?:age)?)\\b", RegexOption.IGNORE_CASE)
        private val ATTRIBUTE_VALUE = Regex("\\b([A-Za-z][A-Za-z ]{1,40}):\\s*([^.;\\n]{1,80})")
        private val RULE = Regex("\\b(?:must|shall|may not|must not|prohibited|requires?|only if|at least|at most)\\b", RegexOption.IGNORE_CASE)
        private val CAMEL_CASE = Regex("([a-z0-9])([A-Z])")
        private val ACRONYM = Regex("[A-Z][A-Z0-9]{1,9}")
        private val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")
        private val WHITESPACE = Regex("\\s+")
        private val properNounTags = setOf("NNP", "NNPS", "PROPN")
        private val organizationSuffixes = setOf("llc", "inc", "bank", "group", "company", "corporation", "association")
        private val locationPrepositions = setOf("in", "at", "from", "within")
        private val administrativeHeadings = setOf(
            "metadata",
            "document control",
            "revision history",
            "change history",
            "table of contents",
            "contents",
        )
        private val illustrativeHeadings = setOf("example", "examples", "illustration", "scenario")
        private val genericTerms = setOf(
            "action", "alternative", "application", "context", "control", "data", "decision", "detail", "document",
            "evidence", "information", "item", "operation", "page", "policy", "process", "record", "requirement",
            "result", "section", "service", "standard", "thing",
        )
        private val temporalOrCurrencyTerms = setOf(
            "friday", "monday", "saturday", "sunday", "thursday", "tuesday", "wednesday",
            "january", "february", "march", "april", "may", "june", "july", "august", "september",
            "october", "november", "december", "eur", "gbp", "usd",
        )
        private val genericRelationVerbs = setOf(
            "act", "activate", "add", "address", "administer", "age", "appear", "assess", "begin", "be", "can",
            "capture", "change", "complete", "continue", "contract", "create", "define", "describe", "design",
            "distinguish", "do", "enhance", "enter", "escalate", "establish", "exclude", "explain", "fail",
            "follow", "form", "have", "identify", "include", "initiate", "intend", "interpret", "issue", "make",
            "manage", "match", "may", "mean", "miss", "must", "obtain", "open", "operate", "place", "post",
            "proportionate", "receive", "record", "refer", "register", "remain", "report", "resolve", "review",
            "schedule", "screen", "separate", "should", "split", "state", "suspect", "target", "test", "treat",
            "use", "verify",
        )
        private val definitionVerbs = setOf("be", "mean", "refer", "define", "describe")
        private val tableOfContentsLine = Regex(".*(?:\\.{3,}|\\s{3,})\\s*\\d+\\s*$")
        private val standalonePageLine = Regex("^(?:page\\s+)?\\d+(?:\\s+of\\s+\\d+)?$", RegexOption.IGNORE_CASE)
        private val numberedSectionHeading = Regex("^\\d+(?:\\.\\d+)*\\.\\s+\\S.*$")
        private val numberedSectionPrefix = Regex("^\\d+(?:\\.\\d+)*\\.\\s+")
        private const val MAX_NOUN_PHRASE_TOKENS = 6
        private const val MAX_RELATIONSHIP_GAP_CHARACTERS = 40
        private val supportingValueCategories = setOf(
            DocumentCandidateExtractionCategory.Date,
            DocumentCandidateExtractionCategory.Identifier,
            DocumentCandidateExtractionCategory.MonetaryAmount,
            DocumentCandidateExtractionCategory.AttributeValue,
        )
        private val documentOnlyCategories = setOf(
            DocumentCandidateExtractionCategory.Administrative,
            DocumentCandidateExtractionCategory.Illustrative,
            DocumentCandidateExtractionCategory.RuleCue,
        )
        private val candidateCategoryPriority = DocumentCandidateExtractionCategory.entries.associateWith { category ->
            when (category) {
                DocumentCandidateExtractionCategory.Organization -> 0
                DocumentCandidateExtractionCategory.Person -> 1
                DocumentCandidateExtractionCategory.Location -> 2
                DocumentCandidateExtractionCategory.RelationshipPhrase -> 3
                DocumentCandidateExtractionCategory.RuleCue -> 4
                DocumentCandidateExtractionCategory.ConceptTerm -> 5
                else -> 6 + category.ordinal
            }
        }

        private fun repeatedBoilerplateLines(blocks: List<LocatedDocumentTextBlock>): Set<String> = blocks
            .flatMap { block ->
                val lines = contentLines(block.exactText)
                if (lines.size < 4) {
                    emptyList()
                } else {
                    (lines.take(2) + lines.takeLast(2)).distinctBy(DocumentTextSegment::start)
                        .map { line -> normalize(line.text) to block.id }
                }
            }
            .filter { (line, _) -> line.isNotBlank() && line.length <= 160 }
            .groupBy({ it.first }, { it.second })
            .filterValues { blockIds -> blockIds.distinct().size >= 2 }
            .keys

        private fun contentLines(text: String): List<DocumentTextSegment> {
            val result = mutableListOf<DocumentTextSegment>()
            var start = 0
            text.splitToSequence('\n').forEach { raw ->
                val leading = raw.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) raw.length else it }
                val value = raw.trim()
                if (value.isNotEmpty()) result += DocumentTextSegment(value, start + leading)
                start += raw.length + 1
            }
            return result
        }

        private fun isLikelyNonBodyLine(value: String): Boolean {
            val trimmed = value.trim()
            if (standalonePageLine.matches(trimmed) || tableOfContentsLine.matches(trimmed)) return true
            val words = trimmed.split(WHITESPACE).filter(String::isNotBlank)
            if (words.isEmpty() || words.size > 12 || trimmed.length > 120) return false
            val letterWords = words.filter { word -> word.any(Char::isLetter) }
            if (letterWords.isEmpty()) return true
            val titleLike = letterWords.count { word -> word.firstOrNull(Char::isLetter)?.isUpperCase() == true }
            return !trimmed.endsWithAny('.', '?', '!', ';') && titleLike * 4 >= letterWords.size * 3
        }

        private fun isIllustrativeSentence(value: String): Boolean =
            illustrativeSentencePrefix.containsMatchIn(value.trimStart())

        private fun isSectionHeadingLine(value: String): Boolean {
            val trimmed = value.trim()
            if (numberedSectionHeading.matches(trimmed)) return true
            val normalized = normalize(trimmed)
            return normalized in administrativeHeadings || normalized in illustrativeHeadings
        }

        private fun documentOnlySectionCategory(value: String): DocumentCandidateExtractionCategory? {
            val normalizedHeading = normalize(value.trim().replaceFirst(numberedSectionPrefix, ""))
            return when {
                normalizedHeading in administrativeHeadings -> DocumentCandidateExtractionCategory.Administrative
                illustrativeHeadings.any { heading ->
                    normalizedHeading == heading || normalizedHeading.startsWith("$heading ")
                } -> DocumentCandidateExtractionCategory.Illustrative
                else -> null
            }
        }

        private val illustrativeSentencePrefix = Regex(
            "^(?:for\\s+example|example|illustration|illustrative\\s+example|scenario)\\b(?:\\s*[:,-])?",
            RegexOption.IGNORE_CASE,
        )

        private fun String.endsWithAny(vararg suffixes: Char): Boolean = suffixes.any(::endsWith)

        private fun groupingFamily(category: DocumentCandidateExtractionCategory): String = when (category) {
            in supportingValueCategories -> "support-${category.name}"
            in documentOnlyCategories -> "document-${category.name}"
            DocumentCandidateExtractionCategory.RelationshipPhrase -> "relationship"
            DocumentCandidateExtractionCategory.RuleCue -> "rule"
            else -> "entity"
        }

        private fun nounPhrases(tokens: List<NlpToken>): List<List<NlpToken>> = buildList {
            var index = 0
            while (index < tokens.size) {
                if (!tokens[index].isNoun && !tokens[index].isAdjective) {
                    index += 1
                    continue
                }
                val start = index
                while (index + 1 < tokens.size && (tokens[index + 1].isNoun || tokens[index + 1].isAdjective)) index += 1
                add(tokens.subList(start, index + 1))
                index += 1
            }
        }

        private fun mention(
            group: List<NlpToken>,
            category: DocumentCandidateExtractionCategory,
            signals: List<DocumentCandidatePromotionReason>,
        ): ExtractedMentionSpan = ExtractedMentionSpan(
            category,
            group.joinToString(" ", transform = NlpToken::text),
            normalize(group.joinToString(" ") { it.normalizedLemma }),
            group.first().start,
            group.last().end,
            promotionSignals = signals,
        )

        private fun findAll(
            regex: Regex,
            text: String,
            category: DocumentCandidateExtractionCategory,
            baseOffset: Int,
            destination: MutableList<ExtractedMentionSpan>,
        ): Unit = regex.findAll(text).forEach { match ->
            destination += ExtractedMentionSpan(
                category,
                match.value,
                normalize(match.value),
                baseOffset + match.range.first,
                baseOffset + match.range.last + 1,
            )
        }

        private fun normalize(value: String): String = value
            .replace(CAMEL_CASE, "$1 $2")
            .replace(NON_ALPHANUMERIC, " ")
            .trim()
            .replace(WHITESPACE, " ")
            .lowercase(Locale.ROOT)

        private fun String.containsNormalizedPhrase(phrase: String): Boolean =
            this == phrase || startsWith("$phrase ") || endsWith(" $phrase") || contains(" $phrase ")

        private fun safeCandidateAliases(value: String): Sequence<String> {
            val normalized = normalize(value)
            if (normalized.isBlank()) return emptySequence()
            val singular = normalized.split(' ').toMutableList().also { words ->
                val last = words.last()
                if (last.length > 3 && last.endsWith('s') && !last.endsWith("ss")) {
                    words[words.lastIndex] = last.dropLast(1)
                }
            }.joinToString(" ")
            return sequenceOf(normalized, singular).distinct()
        }

        private fun stableId(vararg values: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            values.forEach { value ->
                val bytes = value.toByteArray(StandardCharsets.UTF_8)
                digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
                digest.update(bytes)
            }
            return digest.digest().joinToString("") { "%02x".format(it) }.take(32)
        }

        private val DocumentEvidenceMention.isLowValue: Boolean
            get() {
                val words = normalizedText.split(' ').filter(String::isNotBlank)
                return normalizedText in temporalOrCurrencyTerms || (words.size == 1 && words.single() in genericTerms)
            }

        private val DocumentCandidateExtractionCategory.isMeaningBearing: Boolean
            get() = this !in supportingValueCategories && this !in documentOnlyCategories
    }
}

private val NlpToken.normalizedLemma: String
    get() = lemma.lowercase(Locale.ROOT).trim()

private val NlpToken.isNoun: Boolean
    get() = tag.startsWith("NN") || tag in setOf("NOUN", "PROPN")

private val NlpToken.isAdjective: Boolean
    get() = tag.startsWith("JJ") || tag == "ADJ"

private val NlpToken.isVerb: Boolean
    get() = tag.startsWith("VB") || tag in setOf("VERB", "AUX")
