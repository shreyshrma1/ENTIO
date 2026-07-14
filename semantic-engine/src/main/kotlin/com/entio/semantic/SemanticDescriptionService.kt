package com.entio.semantic

import com.entio.core.EntioProject
import com.entio.core.EntioResult
import com.entio.core.Iri
import com.entio.core.OntologyEntityDescriptor
import com.entio.core.SemanticDescriptorKind
import com.entio.core.SemanticMatchReason
import com.entio.core.SemanticSearchQuery
import com.entio.core.SemanticSearchResult
import com.entio.core.ValidationIssue
import com.entio.core.ValidationSeverity
import java.util.Locale

/** Provides deterministic descriptor lookup and label-aware search over a loaded project. */
public class SemanticDescriptionService(
    private val assembler: SemanticDescriptorAssembler = SemanticDescriptorAssembler(),
) {
    public fun describe(
        project: EntioProject,
        entity: Iri,
        preferredLanguage: String? = null,
    ): EntioResult<OntologyEntityDescriptor> {
        val descriptor = assembler.assemble(project, preferredLanguage)
            .firstOrNull { it.common.entity == entity }
            ?: return EntioResult.Failure(
                message = "No semantic descriptor was found for '${entity.value}'.",
                issues = listOf(
                    ValidationIssue(
                        severity = ValidationSeverity.Error,
                        code = "missing-semantic-descriptor",
                        message = "No semantic descriptor was found for '${entity.value}'.",
                        source = entity.value,
                    ),
                ),
            )

        return EntioResult.Success(descriptor)
    }

    public fun describeAll(
        project: EntioProject,
        preferredLanguage: String? = null,
    ): List<OntologyEntityDescriptor> = assembler.assemble(project, preferredLanguage)

    public fun search(
        project: EntioProject,
        query: SemanticSearchQuery,
    ): List<SemanticSearchResult> {
        val normalizedQuery = query.text.trim().lowercase(Locale.ROOT)
        if (normalizedQuery.isEmpty()) return emptyList()

        return assembler.assemble(project, query.preferredLanguage)
            .asSequence()
            .filter { descriptor -> query.kind == null || descriptor.common.kind == query.kind }
            .filter { descriptor -> query.sourceId == null || descriptor.common.sourceId == query.sourceId }
            .mapNotNull { descriptor -> descriptor.match(normalizedQuery) }
            .sortedWith(
                compareBy<SemanticSearchResult> { it.rank }
                    .thenBy { it.descriptor.common.preferredLabel?.stableKey.orEmpty() }
                    .thenBy { it.descriptor.common.entity.value }
                    .thenBy { it.descriptor.common.sourceId },
            )
            .toList()
    }

    private fun OntologyEntityDescriptor.match(normalizedQuery: String): SemanticSearchResult? {
        val common = common
        val preferred = common.preferredLabel?.lexicalForm?.contains(normalizedQuery, ignoreCase = true) == true
        if (preferred) return SemanticSearchResult(this, SemanticMatchReason.PreferredLabel, PREFERRED_LABEL_RANK)

        val alternate = common.alternateLabels.any { label ->
            label.lexicalForm.contains(normalizedQuery, ignoreCase = true)
        }
        if (alternate) return SemanticSearchResult(this, SemanticMatchReason.AlternateLabel, ALTERNATE_LABEL_RANK)

        if (common.entity.value.lowercase(Locale.ROOT).contains(normalizedQuery)) {
            return SemanticSearchResult(this, SemanticMatchReason.Iri, IRI_RANK)
        }

        val annotationMatch = common.annotations.any { statement ->
            statement.value.term.toSearchText().lowercase(Locale.ROOT).contains(normalizedQuery)
        }
        if (annotationMatch) return SemanticSearchResult(this, SemanticMatchReason.Annotation, ANNOTATION_RANK)

        return null
    }

    private fun com.entio.core.RdfTerm.toSearchText(): String = when (this) {
        is com.entio.core.RdfResource -> value
        is com.entio.core.RdfLiteral -> lexicalForm
    }

    private companion object {
        private const val PREFERRED_LABEL_RANK: Int = 0
        private const val ALTERNATE_LABEL_RANK: Int = 1
        private const val IRI_RANK: Int = 2
        private const val ANNOTATION_RANK: Int = 3
    }
}
