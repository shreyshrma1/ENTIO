package com.entio.semantic

import com.entio.core.ChangeSet
import com.entio.core.EntioResult
import com.entio.core.EntityCandidate
import com.entio.core.GeneratedIri
import com.entio.core.NormalizedStagedChangeSet
import com.entio.core.StagedChange
import com.entio.core.StagedChangeOperation
import com.entio.core.StagedChangeSet
import com.entio.core.StagedConflict
import com.entio.core.StagedConflictKind
import com.entio.core.TypedOntologyEdit

/** Orders staged entries, translates them to graph changes, and reports combined conflicts. */
public class StagedChangeSetNormalizer(
    private val editTranslator: TypedOntologyEditTranslator = TypedOntologyEditTranslator(),
    private val deletionChangeGenerator: DeletionChangeGenerator = DeletionChangeGenerator(),
) {
    public fun normalize(stagedChangeSet: StagedChangeSet): EntioResult<NormalizedStagedChangeSet> {
        val entries = stagedChangeSet.entries.sortedWith(compareBy<StagedChange>({ it.order }, { it.id }))
        if (entries.isEmpty()) {
            return EntioResult.Success(NormalizedStagedChangeSet(entries = emptyList()))
        }

        val conflicts = detectConflicts(entries)
        val translated = entries.map { entry ->
            when (val operation = entry.operation) {
                is StagedChangeOperation.TypedEdit -> editTranslator.translate(operation.edit)
                is StagedChangeOperation.GraphChanges -> EntioResult.Success(operation.changeSet)
                is StagedChangeOperation.Delete -> deletionChangeGenerator.generate(operation.plan)
            }
        }
        val translationFailure = translated.filterIsInstance<EntioResult.Failure>().firstOrNull()
        if (translationFailure != null) {
            return translationFailure
        }

        val changes = translated
            .filterIsInstance<EntioResult.Success<ChangeSet>>()
            .flatMap { it.value.changes }
        val changeSet = changes.takeIf { it.isNotEmpty() }?.let(::ChangeSet)

        return EntioResult.Success(
            NormalizedStagedChangeSet(
                entries = entries,
                changeSet = changeSet,
                conflicts = conflicts,
            ),
        )
    }

    private fun detectConflicts(entries: List<StagedChange>): List<StagedConflict> = buildList {
        entries.groupBy { it.id }
            .filterValues { it.size > 1 }
            .forEach { (id, duplicateEntries) ->
                add(
                    conflict(
                        kind = StagedConflictKind.DuplicateTarget,
                        ids = duplicateEntries.map(StagedChange::id),
                        message = "Staged entry id '$id' is repeated.",
                    ),
                )
            }

        entries.flatMap { it.generatedIris }
            .groupBy(GeneratedIri::iri)
            .filterValues { it.size > 1 }
            .forEach { (iri, generated) ->
                add(
                    conflict(
                        kind = StagedConflictKind.DuplicateTarget,
                        ids = entries.filter { entry -> entry.generatedIris.any { it.iri == iri } }.map(StagedChange::id),
                        message = "Multiple staged entries generate IRI '${iri.value}'.",
                    ),
                )
            }

        entries.forEachIndexed { index, first ->
            entries.drop(index + 1).forEach { second ->
                if (first.operation == second.operation) {
                    add(conflict(StagedConflictKind.DuplicateTarget, listOf(first.id, second.id), "The same edit is staged more than once."))
                }
                val firstDelete = first.operation.deletedTarget()?.iri
                val secondDelete = second.operation.deletedTarget()?.iri
                val firstCreated = first.operation.createdTarget()
                val secondCreated = second.operation.createdTarget()
                if (firstDelete != null && secondCreated == firstDelete || secondDelete != null && firstCreated == secondDelete) {
                    add(conflict(StagedConflictKind.CreateDeleteSameEntity, listOf(first.id, second.id), "One staged entry creates an entity deleted by another."))
                }
                if (firstDelete != null && second.operation.references(firstDelete) || secondDelete != null && first.operation.references(secondDelete)) {
                    add(conflict(StagedConflictKind.DeletedDependency, listOf(first.id, second.id), "A staged edit references an entity removed by another entry."))
                }
                if (first.operation.incompatibleWith(second.operation)) {
                    add(conflict(StagedConflictKind.IncompatibleEdits, listOf(first.id, second.id), "Staged edits make incompatible changes to the same property."))
                }
            }
        }

        entries.zipWithNext().forEach { (first, second) ->
            if (first.order == second.order && first.id != second.id) {
                add(conflict(StagedConflictKind.IncompatibleEdits, listOf(first.id, second.id), "Staged order is ambiguous for these entries."))
            }
        }
    }.distinct()

    private fun conflict(kind: StagedConflictKind, ids: List<String>, message: String): StagedConflict =
        StagedConflict(kind = kind, stagedChangeIds = ids, message = message)

    private fun StagedChangeOperation.deletedTarget(): EntityCandidate? =
        (this as? StagedChangeOperation.Delete)?.plan?.target

    private fun StagedChangeOperation.createdTarget(): com.entio.core.Iri? {
        val edit = (this as? StagedChangeOperation.TypedEdit)?.edit ?: return null
        val iri = when (edit) {
            is com.entio.core.CreateClassEdit -> edit.classIri
            is com.entio.core.CreateObjectPropertyEdit -> edit.propertyIri
            is com.entio.core.CreateDatatypePropertyEdit -> edit.propertyIri
            is com.entio.core.CreateIndividualEdit -> edit.individualIri
            else -> null
        } ?: return null
        return iri
    }

    private fun StagedChangeOperation.references(target: com.entio.core.Iri): Boolean {
        val edit = (this as? StagedChangeOperation.TypedEdit)?.edit ?: return false
        return edit.resources().any { it.value == target.value }
    }

    private fun TypedOntologyEdit.resources(): List<com.entio.core.RdfResource> =
        when (this) {
            is com.entio.core.CreateClassEdit -> listOf(classIri)
            is com.entio.core.SetEntityLabelEdit -> listOf(entity)
            is com.entio.core.AddSuperclassEdit -> listOf(classIri, superclassIri)
            is com.entio.core.RemoveSuperclassEdit -> listOf(classIri, superclassIri)
            is com.entio.core.CreateObjectPropertyEdit -> listOf(propertyIri)
            is com.entio.core.CreateDatatypePropertyEdit -> listOf(propertyIri)
            is com.entio.core.SetPropertyDomainEdit -> listOf(propertyIri, domainClassIri)
            is com.entio.core.SetPropertyRangeEdit -> listOf(propertyIri, rangeIri)
            is com.entio.core.CreateIndividualEdit -> listOfNotNull(individualIri, classIri)
            is com.entio.core.AssignTypeEdit -> listOf(resource, typeIri)
            is com.entio.core.AddObjectPropertyAssertionEdit -> listOf(subject, propertyIri, objectResource)
            is com.entio.core.AddDatatypePropertyAssertionEdit -> listOf(subject, propertyIri)
        }

    private fun StagedChangeOperation.incompatibleWith(other: StagedChangeOperation): Boolean {
        val first = (this as? StagedChangeOperation.TypedEdit)?.edit
        val second = (other as? StagedChangeOperation.TypedEdit)?.edit
        return when {
            first is com.entio.core.SetPropertyRangeEdit && second is com.entio.core.SetPropertyRangeEdit ->
                first.propertyIri == second.propertyIri && first.rangeIri != second.rangeIri
            first is com.entio.core.SetPropertyDomainEdit && second is com.entio.core.SetPropertyDomainEdit ->
                first.propertyIri == second.propertyIri && first.domainClassIri != second.domainClassIri
            else -> false
        }
    }
}
