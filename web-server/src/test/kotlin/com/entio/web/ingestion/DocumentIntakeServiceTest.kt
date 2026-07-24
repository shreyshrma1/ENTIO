package com.entio.web.ingestion

import com.entio.core.DocumentTaskId
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocumentIntakeServiceTest {
    @Test
    fun acceptsBoundedSupportedMediaAndMetadata(): Unit {
        val fixture = fixture()
        val cases = listOf(
            Triple("policy.txt", "text/plain", "Policy text".toByteArray()),
            Triple("policy.md", "text/markdown", "# Policy".toByteArray()),
            Triple("policy.pdf", "application/pdf", "%PDF-1.7\n/Type /Page\n%%EOF".toByteArray()),
            Triple("policy.docx", DOCX, docx()),
        )

        cases.forEachIndexed { index, (filename, mediaType, bytes) ->
            val accepted = fixture.service.accept(
                DocumentTaskId("task-$index"),
                fixture.directory,
                "project-a",
                "alice",
                metadata(filename, mediaType),
                ByteArrayInputStream(bytes),
            )

            assertEquals(bytes.size.toLong(), accepted.document.byteSize)
            assertEquals("Authoritative", accepted.document.authority.status.name)
            assertEquals("Governance", accepted.document.authority.businessArea)
            assertTrue(accepted.document.checksumSha256.matches(Regex("[0-9a-f]{64}")))
        }
    }

    @Test
    fun rejectsMismatchesLimitsUnsafeNamesAndInvalidContentWithCleanup(): Unit {
        val fixture = fixture()

        assertCode("document-type-mismatch") {
            fixture.service.accept(
                DocumentTaskId("task-mismatch"),
                fixture.directory,
                "project-a",
                "alice",
                metadata("policy.pdf", "text/plain"),
                ByteArrayInputStream("%PDF-".toByteArray()),
            )
        }
        assertCode("document-too-large") {
            val bounded = fixture(maximumBytes = 8)
            bounded.service.accept(
                DocumentTaskId("task-large"),
                bounded.directory,
                "project-a",
                "alice",
                metadata("policy.txt", "text/plain"),
                ByteArrayInputStream(ByteArray(9) { 1 }),
            )
        }
        assertCode("encrypted-document-unsupported") {
            fixture.service.accept(
                DocumentTaskId("task-encrypted"),
                fixture.directory,
                "project-a",
                "alice",
                metadata("policy.pdf", "application/pdf"),
                ByteArrayInputStream("%PDF-\n/Encrypt".toByteArray()),
            )
        }
        assertCode("unsafe-docx-content") {
            fixture.service.accept(
                DocumentTaskId("task-external"),
                fixture.directory,
                "project-a",
                "alice",
                metadata("policy.docx", DOCX),
                ByteArrayInputStream(docx(externalRelationship = true)),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            fixture.service.accept(
                DocumentTaskId("task-name"),
                fixture.directory,
                "project-a",
                "alice",
                metadata("../policy.txt", "text/plain"),
                ByteArrayInputStream("safe".toByteArray()),
            )
        }

        Files.list(fixture.directory.path).use { files ->
            assertFalse(files.anyMatch { it.fileName.toString().endsWith(".bin") })
        }
    }

    private fun fixture(maximumBytes: Long = 1024 * 1024): IntakeFixture {
        val root = Files.createTempDirectory("entio-intake-test")
        val storage = DocumentTemporaryStorage(root)
        val directory = storage.createTask("task-fixture")
        val configuration = DocumentIngestionConfiguration(
            temporaryRoot = root,
            provenanceRoot = Files.createTempDirectory("entio-provenance-test"),
            maximumDocumentBytes = maximumBytes,
            idFactory = sequenceOf("one", "two", "three", "four", "five", "six").iterator()::next,
        )
        return IntakeFixture(DocumentIntakeService(configuration, storage), directory)
    }

    private fun metadata(filename: String, mediaType: String): DocumentUploadMetadata = DocumentUploadMetadata(
        clientDocumentId = "client-1",
        filename = filename,
        declaredMediaType = mediaType,
        language = "en",
        authorityStatus = "Authoritative",
        businessArea = "Governance",
    )

    private fun docx(externalRelationship: Boolean = false): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            zip.write("""<Types/>""".toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write("""<w:document/>""".toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
            if (externalRelationship) {
                zip.putNextEntry(ZipEntry("word/_rels/document.xml.rels"))
                zip.write("""<Relationship TargetMode="External"/>""".toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun assertCode(code: String, block: () -> Unit): Unit {
        val failure = assertFailsWith<DocumentIngestionFailure> { block() }
        assertEquals(code, failure.code)
    }

    private data class IntakeFixture(
        val service: DocumentIntakeService,
        val directory: TemporaryTaskDirectory,
    )

    private companion object {
        const val DOCX: String = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    }
}
