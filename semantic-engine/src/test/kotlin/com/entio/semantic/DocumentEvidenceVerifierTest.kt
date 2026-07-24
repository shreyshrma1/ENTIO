package com.entio.semantic

import com.entio.core.DocumentExtractionMethod
import com.entio.core.DocumentId
import com.entio.core.DocumentTextBlockId
import com.entio.core.LocatedDocumentTextBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DocumentEvidenceVerifierTest {
    private val block = LocatedDocumentTextBlock(
        id = DocumentTextBlockId("block-1"),
        documentId = DocumentId("document-1"),
        safeFilename = "policy.txt",
        blockOrder = 0,
        startOffset = 0,
        endOffset = 24,
        exactText = "Customer records matter.",
        extractionMethod = DocumentExtractionMethod.Text,
        extractorVersion = "test-v1",
    )

    @Test
    fun resolvesExactOffsetsFromServerHeldText(): Unit {
        val reference = DocumentEvidenceVerifier().verify(
            listOf(block),
            listOf(UnverifiedDocumentEvidence("document-1", "block-1", 0, 8, "Customer")),
        ).single()

        assertEquals("Customer", reference.exactExcerpt)
        assertEquals("block-1", reference.blockId.value)
        assertEquals(reference.id, DocumentEvidenceVerifier().verify(
            listOf(block),
            listOf(UnverifiedDocumentEvidence("document-1", "block-1", 0, 8, "Customer")),
        ).single().id)
    }

    @Test
    fun rejectsAlteredInventedOutOfRangeAndCrossDocumentClaims(): Unit {
        assertCode("evidence-excerpt-mismatch") {
            verify(UnverifiedDocumentEvidence("document-1", "block-1", 0, 8, "Consumer"))
        }
        assertCode("evidence-block-not-found") {
            verify(UnverifiedDocumentEvidence("document-1", "block-invented", 0, 8, "Customer"))
        }
        assertCode("evidence-offset-invalid") {
            verify(UnverifiedDocumentEvidence("document-1", "block-1", 0, 99, "Customer"))
        }
        assertCode("evidence-cross-document") {
            verify(UnverifiedDocumentEvidence("document-2", "block-1", 0, 8, "Customer"))
        }
    }

    @Test
    fun supportsSortedMultiPassageEvidenceAndRejectsUnsupportedCounts(): Unit {
        val references = DocumentEvidenceVerifier().verify(
            listOf(block),
            listOf(
                UnverifiedDocumentEvidence("document-1", "block-1", 17, 23, "matter"),
                UnverifiedDocumentEvidence("document-1", "block-1", 0, 8, "Customer"),
            ),
        )
        assertEquals(listOf("Customer", "matter"), references.map { it.exactExcerpt })
        assertCode("evidence-count-invalid") { DocumentEvidenceVerifier().verify(listOf(block), emptyList()) }
        assertCode("evidence-duplicate") {
            val claim = UnverifiedDocumentEvidence("document-1", "block-1", 0, 8, "Customer")
            DocumentEvidenceVerifier().verify(listOf(block), listOf(claim, claim))
        }
    }

    private fun verify(claim: UnverifiedDocumentEvidence): Unit {
        DocumentEvidenceVerifier().verify(listOf(block), listOf(claim))
    }

    private fun assertCode(code: String, block: () -> Unit): Unit {
        val failure = assertFailsWith<DocumentEvidenceVerificationFailure> { block() }
        assertEquals(code, failure.code)
    }
}
