package com.entio.semantic

import com.entio.core.DocumentEvidenceId
import com.entio.core.DocumentEvidenceReference
import com.entio.core.DocumentTextBlockId
import com.entio.core.LocatedDocumentTextBlock
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

public data class UnverifiedDocumentEvidence(
    val documentId: String,
    val blockId: String,
    val startOffsetInBlock: Int,
    val endOffsetInBlock: Int,
    val claimedExcerpt: String,
)

public class DocumentEvidenceVerificationFailure(
    public val code: String,
    message: String,
) : IllegalArgumentException(message)

/** Resolves provider offsets only against server-held extracted blocks. */
public class DocumentEvidenceVerifier {
    public fun verify(
        availableBlocks: List<LocatedDocumentTextBlock>,
        claims: List<UnverifiedDocumentEvidence>,
    ): List<DocumentEvidenceReference> {
        if (claims.isEmpty() || claims.size > 8) {
            throw DocumentEvidenceVerificationFailure("evidence-count-invalid", "Evidence must contain one to eight exact references.")
        }
        val blocksById = availableBlocks.associateBy { it.id.value }
        if (blocksById.size != availableBlocks.size) {
            throw DocumentEvidenceVerificationFailure("evidence-block-duplicate", "Server-held block identities must be unique.")
        }
        val verified = claims.map { claim ->
            val block = blocksById[claim.blockId]
                ?: throw DocumentEvidenceVerificationFailure("evidence-block-not-found", "The evidence block was not found.")
            if (block.documentId.value != claim.documentId) {
                throw DocumentEvidenceVerificationFailure("evidence-cross-document", "Evidence cannot cross document boundaries.")
            }
            val resolved = resolveAgainstServerText(block.exactText, claim)
            DocumentEvidenceReference(
                id = DocumentEvidenceId(
                    "evidence-${stableId(block.id.value, resolved.start.toString(), resolved.end.toString(), resolved.exactExcerpt)}",
                ),
                documentId = block.documentId,
                blockId = DocumentTextBlockId(block.id.value),
                pageNumber = block.pageNumber,
                sectionHeading = block.sectionHeading,
                startOffsetInBlock = resolved.start,
                endOffsetInBlock = resolved.end,
                exactExcerpt = resolved.exactExcerpt,
                extractionMethod = block.extractionMethod,
                ocrConfidence = block.ocrConfidence,
            )
        }
        if (verified.distinctBy(DocumentEvidenceReference::id).size != verified.size) {
            throw DocumentEvidenceVerificationFailure("evidence-duplicate", "Evidence references must be unique.")
        }
        return verified.sortedBy(DocumentEvidenceReference::stableOrderingKey)
    }

    private fun resolveAgainstServerText(
        serverText: String,
        claim: UnverifiedDocumentEvidence,
    ): ResolvedEvidence {
        if (claim.startOffsetInBlock >= 0 &&
            claim.endOffsetInBlock > claim.startOffsetInBlock &&
            claim.endOffsetInBlock <= serverText.length
        ) {
            val suppliedRangeText = serverText.substring(claim.startOffsetInBlock, claim.endOffsetInBlock)
            if (suppliedRangeText == claim.claimedExcerpt) {
                return ResolvedEvidence(claim.startOffsetInBlock, claim.endOffsetInBlock, suppliedRangeText)
            }
        }
        if (claim.claimedExcerpt.isBlank()) {
            throw DocumentEvidenceVerificationFailure(
                "evidence-excerpt-mismatch",
                "The claimed excerpt does not match server-held text.",
            )
        }

        val normalizedServer = normalizeForEvidenceMatching(serverText)
        val normalizedClaim = normalizeForEvidenceMatching(claim.claimedExcerpt).text
        if (normalizedClaim.isEmpty()) {
            throw DocumentEvidenceVerificationFailure(
                "evidence-excerpt-mismatch",
                "The claimed excerpt does not match server-held text.",
            )
        }
        val normalizedStart = normalizedServer.text.indexOf(normalizedClaim)
        if (normalizedStart < 0 ||
            normalizedServer.text.indexOf(normalizedClaim, normalizedStart + 1) >= 0
        ) {
            throw DocumentEvidenceVerificationFailure(
                "evidence-excerpt-mismatch",
                "The claimed excerpt does not have one unambiguous match in server-held text.",
            )
        }
        val normalizedEnd = normalizedStart + normalizedClaim.length
        val originalStart = normalizedServer.originalStarts[normalizedStart]
        val originalEnd = normalizedServer.originalEnds[normalizedEnd - 1]
        return ResolvedEvidence(
            originalStart,
            originalEnd,
            serverText.substring(originalStart, originalEnd),
        )
    }

    /**
     * Reconciles presentation-only differences while retaining offsets into the original server text.
     * Punctuation is otherwise preserved so similar policy statements cannot collapse into one match.
     */
    private fun normalizeForEvidenceMatching(value: String): NormalizedEvidenceText {
        val normalized = StringBuilder()
        val originalStarts = mutableListOf<Int>()
        val originalEnds = mutableListOf<Int>()
        var index = 0
        while (index < value.length) {
            val character = value[index]
            if (character == '\u00ad') {
                index += 1
                continue
            }
            if (character.isEvidenceHyphen() && isLineWrapHyphen(value, index)) {
                index += 1
                while (index < value.length && value[index].isWhitespace()) index += 1
                continue
            }
            if (character.isWhitespace()) {
                val start = index
                while (index < value.length && value[index].isWhitespace()) index += 1
                if (normalized.isNotEmpty() && normalized.last() != ' ') {
                    normalized.append(' ')
                    originalStarts += start
                    originalEnds += index
                }
                continue
            }
            normalized.append(character.evidenceEquivalent())
            originalStarts += index
            originalEnds += index + 1
            index += 1
        }
        while (normalized.endsWith(" ")) {
            normalized.deleteCharAt(normalized.lastIndex)
            originalStarts.removeLast()
            originalEnds.removeLast()
        }
        return NormalizedEvidenceText(normalized.toString(), originalStarts, originalEnds)
    }

    private fun isLineWrapHyphen(value: String, index: Int): Boolean {
        if (index == 0 || index + 1 >= value.length || !value[index - 1].isLetter()) return false
        var cursor = index + 1
        var containsLineBreak = false
        while (cursor < value.length && value[cursor].isWhitespace()) {
            containsLineBreak = containsLineBreak || value[cursor] == '\n' || value[cursor] == '\r'
            cursor += 1
        }
        return containsLineBreak && cursor < value.length && value[cursor].isLetter()
    }

    private fun Char.isEvidenceHyphen(): Boolean = this in setOf('-', '\u2010', '\u2011', '\u2012', '\u2013', '\u2014', '\u2212')

    private fun Char.evidenceEquivalent(): Char = when (this) {
        '\u2018', '\u2019', '\u201a', '\u201b' -> '\''
        '\u201c', '\u201d', '\u201e', '\u201f' -> '"'
        '\u2010', '\u2011', '\u2012', '\u2013', '\u2014', '\u2212' -> '-'
        else -> this
    }

    private fun stableId(vararg values: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        values.forEach { value ->
            val bytes = value.toByteArray(StandardCharsets.UTF_8)
            digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private data class ResolvedEvidence(
        val start: Int,
        val end: Int,
        val exactExcerpt: String,
    )

    private data class NormalizedEvidenceText(
        val text: String,
        val originalStarts: List<Int>,
        val originalEnds: List<Int>,
    )
}
