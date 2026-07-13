package com.entio.semantic

import com.entio.core.CreateClassEdit
import com.entio.core.DeletionPlan
import com.entio.core.DeletionPlanStatus
import com.entio.core.DeletionDependency
import com.entio.core.DeletionDependencyKind
import com.entio.core.EntityCandidate
import com.entio.core.EntitySelector
import com.entio.core.EntioResult
import com.entio.core.GeneratedIri
import com.entio.core.Iri
import com.entio.core.IriCollisionOutcome
import com.entio.core.GraphTriple
import com.entio.core.RdfLiteral
import com.entio.core.NormalizedStagedChangeSet
import com.entio.core.StagedChange
import com.entio.core.StagedChangeOperation
import com.entio.core.StagedChangeSet
import com.entio.core.StagedConflictKind
import com.entio.core.SymbolKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class StagedChangeSetNormalizerTest {
    private val normalizer = StagedChangeSetNormalizer()
    private val customer = Iri("https://example.com/Customer")

    @Test
    fun preservesDeterministicOrderAndTranslatesAllEntries(): Unit {
        val later = staged("later", 2, CreateClassEdit(Iri("https://example.com/Later")))
        val earlier = staged("earlier", 1, CreateClassEdit(customer))

        val result = assertIs<EntioResult.Success<NormalizedStagedChangeSet>>(
            normalizer.normalize(StagedChangeSet(entries = listOf(later, earlier))),
        ).value

        assertEquals(listOf("earlier", "later"), result.entries.map(StagedChange::id))
        assertEquals(2, result.changeSet?.changes?.size)
        assertEquals(emptyList(), result.conflicts)
    }

    @Test
    fun detectsDuplicateGeneratedIrisAndDuplicateEdits(): Unit {
        val generated = GeneratedIri(Iri("https://example.com/Customer"), "Customer", IriCollisionOutcome.New, "v1")
        val first = staged("first", 0, CreateClassEdit(customer), listOf(generated))
        val second = staged("second", 1, CreateClassEdit(customer), listOf(generated))

        val result = assertIs<EntioResult.Success<NormalizedStagedChangeSet>>(
            normalizer.normalize(StagedChangeSet(entries = listOf(first, second))),
        ).value

        assertEquals(2, result.conflicts.count { it.kind == StagedConflictKind.DuplicateTarget })
    }

    @Test
    fun detectsCreateDeleteAndDeleteReferenceConflicts(): Unit {
        val deletion = StagedChange(
            id = "delete",
            order = 0,
            targetSourceId = "simple",
            summary = "Delete Customer",
            operation = StagedChangeOperation.Delete(
                DeletionPlan(
                    target = EntityCandidate(customer, "Customer", SymbolKind.Class, "simple"),
                    directStatements = listOf(
                        DeletionDependency(
                            statement = GraphTriple(customer, Iri("https://example.com/type"), RdfLiteral("Class")),
                            kind = DeletionDependencyKind.DirectDefinition,
                            sourceId = "simple",
                        ),
                    ),
                    status = DeletionPlanStatus.Safe,
                ),
            ),
        )
        val create = staged("create", 1, CreateClassEdit(customer))
        val reference = staged("reference", 2, com.entio.core.AddSuperclassEdit(Iri("https://example.com/Invoice"), customer))

        val result = assertIs<EntioResult.Success<NormalizedStagedChangeSet>>(
            normalizer.normalize(StagedChangeSet(entries = listOf(deletion, create, reference))),
        ).value

        assertEquals(true, result.conflicts.any { it.kind == StagedConflictKind.CreateDeleteSameEntity })
        assertEquals(true, result.conflicts.any { it.kind == StagedConflictKind.DeletedDependency })
    }

    @Test
    fun returnsAnEmptyNormalizedSetWithoutCreatingAnInvalidChangeSet(): Unit {
        val result = assertIs<EntioResult.Success<NormalizedStagedChangeSet>>(
            normalizer.normalize(StagedChangeSet()),
        ).value

        assertEquals(emptyList(), result.entries)
        assertEquals(null, result.changeSet)
    }

    private fun staged(
        id: String,
        order: Int,
        edit: com.entio.core.TypedOntologyEdit,
        generatedIris: List<GeneratedIri> = emptyList(),
    ): StagedChange = StagedChange(
        id = id,
        order = order,
        targetSourceId = "simple",
        summary = id,
        operation = StagedChangeOperation.TypedEdit(edit),
        generatedIris = generatedIris,
    )
}
