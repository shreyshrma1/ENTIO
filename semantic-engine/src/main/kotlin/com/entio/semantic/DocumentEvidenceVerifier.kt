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
            if (claim.startOffsetInBlock < 0 ||
                claim.endOffsetInBlock <= claim.startOffsetInBlock ||
                claim.endOffsetInBlock > block.exactText.length
            ) {
                throw DocumentEvidenceVerificationFailure("evidence-offset-invalid", "Evidence offsets are outside the server-held block.")
            }
            val exact = block.exactText.substring(claim.startOffsetInBlock, claim.endOffsetInBlock)
            if (exact != claim.claimedExcerpt) {
                throw DocumentEvidenceVerificationFailure("evidence-excerpt-mismatch", "The claimed excerpt does not match server-held text.")
            }
            DocumentEvidenceReference(
                id = DocumentEvidenceId(
                    "evidence-${stableId(block.id.value, claim.startOffsetInBlock.toString(), claim.endOffsetInBlock.toString(), exact)}",
                ),
                documentId = block.documentId,
                blockId = DocumentTextBlockId(block.id.value),
                pageNumber = block.pageNumber,
                sectionHeading = block.sectionHeading,
                startOffsetInBlock = claim.startOffsetInBlock,
                endOffsetInBlock = claim.endOffsetInBlock,
                exactExcerpt = exact,
                extractionMethod = block.extractionMethod,
                ocrConfidence = block.ocrConfidence,
            )
        }
        if (verified.distinctBy(DocumentEvidenceReference::id).size != verified.size) {
            throw DocumentEvidenceVerificationFailure("evidence-duplicate", "Evidence references must be unique.")
        }
        return verified.sortedBy(DocumentEvidenceReference::stableOrderingKey)
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
}
