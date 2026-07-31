package com.entio.web.ingestion

import com.entio.core.DocumentGroundedCandidate
import com.entio.core.DocumentMatchScope
import com.entio.core.DocumentRetrievalFingerprints
import com.entio.core.EntioProject
import com.entio.core.EntioResult
import com.entio.core.Iri
import com.entio.core.SemanticDescriptorKind
import com.entio.semantic.DocumentOntologyRetrievalInput
import com.entio.semantic.DocumentOntologyRetrievalRecord
import com.entio.semantic.DocumentSemanticRecord
import com.entio.semantic.FiboCatalogLoader
import com.entio.web.contract.WebStagingResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

internal data class DocumentFrozenRetrievalContext(
    val input: DocumentOntologyRetrievalInput,
    val fingerprints: DocumentRetrievalFingerprints,
)

/** Builds a read-only retrieval view over existing project, workflow, provenance, and pinned FIBO state. */
internal class DocumentRetrievalContextFactory(
    private val fiboRoot: Path = defaultFiboRoot(),
) {
    fun create(
        projectId: String,
        project: EntioProject,
        candidates: List<DocumentGroundedCandidate>,
        ontologyFingerprint: String,
        staging: WebStagingResponse?,
        provenance: List<AppliedDocumentProvenanceSummary>,
    ): DocumentFrozenRetrievalContext {
        val currentWorkFingerprint = hash(staging?.entries.orEmpty().map { it.id to it.normalizedValues })
        val provenanceFingerprint = hash(provenance)
        val fiboFingerprint = hashFiboPackage(fiboRoot)
        val fingerprints = DocumentRetrievalFingerprints(
            ontologyFingerprint,
            currentWorkFingerprint,
            provenanceFingerprint,
            fiboFingerprint,
        )
        val fibo = when (val loaded = FiboCatalogLoader(fiboRoot).load(project)) {
            is EntioResult.Success -> loaded.value
            is EntioResult.Failure -> throw DocumentIngestionFailure(
                "document-fibo-retrieval-failed",
                "The pinned ontology catalog could not be loaded for grounded retrieval.",
            )
        }
        return DocumentFrozenRetrievalContext(
            DocumentOntologyRetrievalInput(
                projectId = projectId,
                candidates = candidates,
                project = project,
                currentWorkRecords = currentWorkRecords(projectId, staging, fingerprints),
                provenanceRecords = provenanceRecords(projectId, provenance, fingerprints),
                fiboSession = fibo,
                fingerprints = fingerprints,
            ),
            fingerprints,
        )
    }

    private fun currentWorkRecords(
        projectId: String,
        staging: WebStagingResponse?,
        fingerprints: DocumentRetrievalFingerprints,
    ): List<DocumentOntologyRetrievalRecord> = staging?.entries.orEmpty().flatMap { entry ->
        entry.generatedIris.mapNotNull { value ->
            val kind = descriptorKind(entry.editType) ?: return@mapNotNull null
            val scope = if (staging?.proposal == null) DocumentMatchScope.SharedStaging else DocumentMatchScope.CurrentProposal
            DocumentOntologyRetrievalRecord(
                projectId,
                DocumentSemanticRecord(
                    scope,
                    Iri(value),
                    entry.sourceId,
                    entry.normalizedValues["label"] ?: entry.summary,
                    category = null,
                    normalizedIdentityKey = (entry.normalizedValues["label"] ?: entry.summary).lowercase(),
                    normalizedTypedOperationKey = entry.documentDraftProvenance?.normalizedTypedOperationKey,
                ),
                kind,
                writable = true,
                fingerprints = fingerprints,
            )
        }
    }.sortedWith(compareBy({ it.matcherRecord.scope.ordinal }, { it.matcherRecord.entityIri.value }))

    private fun provenanceRecords(
        projectId: String,
        summaries: List<AppliedDocumentProvenanceSummary>,
        fingerprints: DocumentRetrievalFingerprints,
    ): List<DocumentOntologyRetrievalRecord> = summaries.mapNotNull { summary ->
        val iri = summary.targetEntityIri?.let(::Iri) ?: return@mapNotNull null
        DocumentOntologyRetrievalRecord(
            projectId,
            DocumentSemanticRecord(
                DocumentMatchScope.DurableProvenance,
                iri,
                summary.recordId,
                preferredLabel = null,
                category = null,
                normalizedIdentityKey = null,
                normalizedTypedOperationKey = summary.normalizedTypedOperationKey,
            ),
            SemanticDescriptorKind.Class,
            writable = false,
            fingerprints = fingerprints,
        )
    }.sortedBy { it.matcherRecord.entityIri.value }

    private fun descriptorKind(editType: String): SemanticDescriptorKind? = when {
        "ObjectProperty" in editType -> SemanticDescriptorKind.ObjectProperty
        "DatatypeProperty" in editType -> SemanticDescriptorKind.DatatypeProperty
        "AnnotationProperty" in editType -> SemanticDescriptorKind.AnnotationProperty
        "Individual" in editType -> SemanticDescriptorKind.Individual
        "Class" in editType -> SemanticDescriptorKind.Class
        else -> null
    }

    private fun hashFiboPackage(root: Path): String {
        val manifest = root.resolve("manifest.yaml")
        val metadata = root.resolve("indexes/catalog-metadata-v1.json")
        require(Files.isRegularFile(manifest) && Files.isRegularFile(metadata))
        return hash(listOf(Files.readString(manifest), Files.readString(metadata)))
    }

    private fun hash(value: Any): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toString().toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        fun defaultFiboRoot(): Path {
            val direct = Path.of("external-ontologies", "fibo")
            return if (Files.isDirectory(direct)) direct else Path.of("..", "external-ontologies", "fibo")
        }
    }
}
