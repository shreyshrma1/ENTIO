package com.entio.diff

import com.entio.core.ChangePreview
import com.entio.core.EntioProject
import com.entio.core.EntioProjectConfig
import com.entio.core.EntioResult
import com.entio.core.GraphState
import com.entio.core.Iri
import com.entio.core.LoadedOntology
import com.entio.core.OntologyFormat
import com.entio.core.OntologySourceReference
import com.entio.core.ResolvedOntologySource
import com.entio.core.StagedChange
import com.entio.core.StagedChangeOperation
import com.entio.core.StagedChangeSet
import com.entio.core.CreateClassEdit
import com.entio.core.SymbolKind
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CombinedPreviewServiceTest {
    private val service = CombinedPreviewService()

    @Test
    fun producesOneDiffValidationAndRoundTripResultWithoutChangingSources(): Unit {
        val sourcePath = Files.createTempFile("entio-combined", ".ttl")
        sourcePath.writeText("@prefix ex: <https://example.com/> .\n")
        val project = project(sourcePath)
        val staged = StagedChange(
            id = "create-customer",
            order = 0,
            targetSourceId = "simple",
            summary = "Create Customer",
            operation = StagedChangeOperation.TypedEdit(
                CreateClassEdit(Iri("https://example.com/Customer")),
            ),
        )
        val before = Files.readAllBytes(sourcePath)

        val result = assertIs<EntioResult.Success<com.entio.core.CombinedProposalPreview>>(
            service.preview(project, StagedChangeSet(entries = listOf(staged)), "combined-1"),
        ).value

        assertEquals(true, result.validationReport.ok)
        assertEquals(1, result.diff?.entries?.size)
        assertIs<com.entio.core.SemanticEquivalenceResult.Equivalent>(result.equivalence)
        assertEquals(before.toList(), Files.readAllBytes(sourcePath).toList())
        assertEquals(listOf("create-customer"), result.metadata.stagedChangeIds)
    }

    @Test
    fun returnsConflictsWithoutPreviewingAPartialSet(): Unit {
        val sourcePath = Files.createTempFile("entio-combined-conflict", ".ttl")
        sourcePath.writeText("@prefix ex: <https://example.com/> .\n")
        val project = project(sourcePath)
        val edit = CreateClassEdit(Iri("https://example.com/Customer"))
        val first = staged("first", edit)
        val second = staged("second", edit)

        val result = assertIs<EntioResult.Success<com.entio.core.CombinedProposalPreview>>(
            service.preview(project, StagedChangeSet(entries = listOf(first, second)), "combined-2"),
        ).value

        assertEquals(com.entio.core.CombinedProposalStatus.Conflicted, result.metadata.status)
        assertEquals(null, result.preview)
        assertEquals(true, result.validationReport.issues.isNotEmpty())
    }

    @Test
    fun rejectsEmptyStagedSets(): Unit {
        val sourcePath = Files.createTempFile("entio-combined-empty", ".ttl")
        sourcePath.writeText("@prefix ex: <https://example.com/> .\n")

        val result = service.preview(project(sourcePath), StagedChangeSet(), "combined-3")

        val failure = assertIs<EntioResult.Failure>(result)
        assertEquals("empty-staged-change-set", failure.issues.single().code)
    }

    private fun staged(id: String, edit: com.entio.core.TypedOntologyEdit): StagedChange =
        StagedChange(
            id = id,
            order = if (id == "first") 0 else 1,
            targetSourceId = "simple",
            summary = id,
            operation = StagedChangeOperation.TypedEdit(edit),
        )

    private fun project(sourcePath: java.nio.file.Path): EntioProject {
        val source = ResolvedOntologySource("simple", sourcePath, OntologyFormat.Turtle)
        return EntioProject(
            config = EntioProjectConfig(
                name = "simple",
                ontologySources = listOf(OntologySourceReference("simple", "ontology/simple.ttl", OntologyFormat.Turtle)),
            ),
            resolvedSources = listOf(source),
            ontologies = listOf(LoadedOntology(source, GraphState())),
            symbols = emptyList(),
            graph = GraphState(),
        )
    }
}
