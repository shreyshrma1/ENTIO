package com.entio.semantic

import com.entio.core.DocumentAnalysisPipelineVersions
import com.entio.core.DocumentCandidateExtractionCategory
import com.entio.core.DocumentCandidateOrigin
import com.entio.core.DocumentEvidenceId
import com.entio.core.DocumentGroundedCandidate
import com.entio.core.DocumentGroundedEvidenceSpan
import com.entio.core.DocumentId
import com.entio.core.DocumentMatchScope
import com.entio.core.DocumentRetrievalFingerprints
import com.entio.core.DocumentTextBlockId
import com.entio.core.EntioProject
import com.entio.core.EntioProjectConfig
import com.entio.core.EntioResult
import com.entio.core.Iri
import com.entio.core.OntologySourceReference
import com.entio.core.SemanticDescriptorKind
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocumentOntologyRetrievalServiceTest {
    private val fingerprints = DocumentRetrievalFingerprints(hash('1'), hash('2'), hash('3'), hash('4'))
    private val project = project()

    @Test
    fun `retrieves applied current-work same-task provenance and pinned FIBO in stable scope order`(): Unit {
        val candidate = candidate("Payment")
        val current = listOf(
            record(DocumentMatchScope.PrivateDraft, "draft"),
            record(DocumentMatchScope.SharedStaging, "staging"),
            record(DocumentMatchScope.CurrentProposal, "proposal"),
        )
        val sameTask = listOf(record(DocumentMatchScope.SameTask, "same-task"))
        val provenance = listOf(record(DocumentMatchScope.DurableProvenance, "provenance"))
        val fibo = (FiboCatalogLoader(Path.of("..", "external-ontologies", "fibo").toAbsolutePath().normalize()).load()
            as EntioResult.Success).value

        val first = service().retrieve(
            DocumentOntologyRetrievalInput(
                projectId = "project-1",
                candidates = listOf(candidate),
                project = project,
                importedRecords = listOf(record(DocumentMatchScope.Imported, "imported")),
                currentWorkRecords = current,
                sameTaskRecords = sameTask,
                provenanceRecords = provenance,
                fiboSession = fibo,
                fingerprints = fingerprints,
            ),
        )
        val repeated = service().retrieve(
            DocumentOntologyRetrievalInput(
                projectId = "project-1",
                candidates = listOf(candidate),
                project = project,
                importedRecords = listOf(record(DocumentMatchScope.Imported, "imported")),
                currentWorkRecords = current,
                sameTaskRecords = sameTask,
                provenanceRecords = provenance,
                fiboSession = fibo,
                fingerprints = fingerprints,
            ),
        )
        val scopes = first.results.single().selections.map { it.scope }

        assertEquals(first, repeated)
        assertTrue(DocumentMatchScope.AppliedLocal in scopes)
        assertTrue(DocumentMatchScope.Imported in scopes)
        assertTrue(DocumentMatchScope.PrivateDraft in scopes)
        assertTrue(DocumentMatchScope.SharedStaging in scopes)
        assertTrue(DocumentMatchScope.CurrentProposal in scopes)
        assertTrue(DocumentMatchScope.SameTask in scopes)
        assertTrue(DocumentMatchScope.DurableProvenance in scopes)
        assertTrue(first.results.single().selections.all { selection ->
            selection.matchReasons == selection.matchReasons.sortedBy { it.stableOrderingKey }
        })
        assertTrue(first.results.single().selections.filter { it.scope == DocumentMatchScope.CuratedFibo }.all {
            !it.writable && it.sourceOntologyIris.isNotEmpty()
        })
    }

    @Test
    fun `bounds prompt results independently from full-state exact duplicate checks`(): Unit {
        val candidate = candidate("Payment")
        val records = (1..25).map { index ->
            record(DocumentMatchScope.PrivateDraft, "draft-$index", iri = "https://example.com/Payment$index")
        }
        val result = service().retrieve(
            DocumentOntologyRetrievalInput(
                projectId = "project-1",
                candidates = listOf(candidate),
                project = project,
                currentWorkRecords = records,
                fingerprints = fingerprints,
            ),
        )

        assertEquals(20, result.results.single().selections.size)
        assertEquals(26, result.fullStateMatches.size)
        assertTrue(result.results.single().completeAuthorizedScopeSearch)
    }

    @Test
    fun `searches compatible kinds and returns empty results successfully`(): Unit {
        val relationship = candidate("receives", DocumentCandidateExtractionCategory.RelationshipPhrase)
        val noMatch = service().retrieve(
            DocumentOntologyRetrievalInput(
                projectId = "project-1",
                candidates = listOf(relationship),
                project = project,
                fingerprints = fingerprints,
            ),
        ).results.single()

        assertTrue(noMatch.selections.isEmpty())
        assertTrue(noMatch.completeAuthorizedScopeSearch)
    }

    @Test
    fun `rejects cross-project wrong-scope and writable imported records`(): Unit {
        val current = record(DocumentMatchScope.PrivateDraft, "draft")
        assertFailsWith<IllegalArgumentException> {
            DocumentOntologyRetrievalInput(
                projectId = "other-project",
                candidates = listOf(candidate("Payment")),
                project = project,
                currentWorkRecords = listOf(current),
                fingerprints = fingerprints,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DocumentOntologyRetrievalInput(
                projectId = "project-1",
                candidates = listOf(candidate("Payment")),
                project = project,
                sameTaskRecords = listOf(current),
                fingerprints = fingerprints,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            record(DocumentMatchScope.Imported, "imported", writable = true)
        }
    }

    @Test
    fun `derives stable opaque IDs from ranking and fingerprints`(): Unit {
        val candidate = candidate("Payment")
        val input = DocumentOntologyRetrievalInput(
            projectId = "project-1",
            candidates = listOf(candidate),
            project = project,
            currentWorkRecords = listOf(record(DocumentMatchScope.PrivateDraft, "draft")),
            fingerprints = fingerprints,
        )
        val first = service().retrieve(input).results.single().selections
        val changed = service().retrieve(
            input.copy(
                fingerprints = fingerprints.copy(currentWorkSha256 = hash('9')),
                currentWorkRecords = listOf(
                    record(
                        DocumentMatchScope.PrivateDraft,
                        "draft",
                        fingerprints = fingerprints.copy(currentWorkSha256 = hash('9')),
                    ),
                ),
            ),
        ).results.single().selections

        assertFalse(first.map { it.selectionId } == changed.map { it.selectionId })
        assertTrue(first.all { it.selectionId.startsWith("selection-") && "https" !in it.selectionId })
    }

    private fun service(): DocumentOntologyRetrievalService = DocumentOntologyRetrievalService()

    private fun candidate(
        text: String,
        category: DocumentCandidateExtractionCategory = DocumentCandidateExtractionCategory.ConceptTerm,
    ): DocumentGroundedCandidate = DocumentGroundedCandidate(
        id = "candidate-${text.lowercase()}",
        origin = DocumentCandidateOrigin.LocalNlp,
        category = category,
        displayText = text,
        normalizedText = text.lowercase(),
        documentId = DocumentId("document-1"),
        documentChecksumSha256 = hash('a'),
        evidenceSpans = listOf(
            DocumentGroundedEvidenceSpan(
                evidenceId = DocumentEvidenceId("evidence-1"),
                referenceId = DocumentEvidenceId("reference-1"),
                documentId = DocumentId("document-1"),
                blockId = DocumentTextBlockId("block-1"),
                startOffsetInBlock = 0,
                endOffsetInBlock = text.length,
                exactText = text,
            ),
        ),
        extractorContractVersion = DocumentAnalysisPipelineVersions.CANDIDATE_EXTRACTION_CONTRACT,
        resourceVersion = DocumentAnalysisPipelineVersions.NLP_RESOURCE_SET,
    )

    private fun record(
        scope: DocumentMatchScope,
        sourceId: String,
        iri: String = "https://example.com/Payment-$sourceId",
        writable: Boolean = scope !in setOf(DocumentMatchScope.Imported, DocumentMatchScope.CuratedFibo),
        fingerprints: DocumentRetrievalFingerprints = this.fingerprints,
    ): DocumentOntologyRetrievalRecord = DocumentOntologyRetrievalRecord(
        projectId = "project-1",
        matcherRecord = DocumentSemanticRecord(
            scope = scope,
            entityIri = Iri(iri),
            sourceId = sourceId,
            preferredLabel = "Payment",
            aliases = emptyList(),
            category = null,
            normalizedIdentityKey = "payment",
            normalizedTypedOperationKey = null,
        ),
        kind = SemanticDescriptorKind.Class,
        writable = writable,
        fingerprints = fingerprints,
    )

    private fun project(): EntioProject {
        val ontology = (OntologyParser().parse(
            SemanticEngineTestFixtures.resolvedSource(
                """
                @prefix ex: <https://example.com/> .
                @prefix owl: <http://www.w3.org/2002/07/owl#> .
                @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
                ex:Payment a owl:Class ; rdfs:label "Payment" .
                """.trimIndent(),
            ),
        ) as EntioResult.Success).value
        return EntioProject(
            config = EntioProjectConfig(
                "simple",
                listOf(OntologySourceReference("simple", ontology.source.path.toString(), ontology.source.format)),
            ),
            resolvedSources = listOf(ontology.source),
            ontologies = listOf(ontology),
            symbols = emptyList(),
            graph = ontology.graph,
        )
    }

    private fun hash(character: Char): String = character.toString().repeat(64)
}
