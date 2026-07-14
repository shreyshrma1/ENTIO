package com.entio.semantic

import com.entio.core.AnnotationStatement
import com.entio.core.AnnotationValue
import com.entio.core.Iri
import com.entio.core.LocalizedText
import com.entio.core.PreferredLabelSource
import com.entio.core.RdfLiteral
import com.entio.core.RdfResource

/** Deterministic label and descriptive-metadata selection for semantic descriptors. */
public class SemanticLabelPolicy {
    public fun apply(
        entity: RdfResource,
        annotations: ExplicitAnnotationSet,
        preferredLanguage: String? = null,
    ): SemanticLabelMetadata {
        val prefLabels = literals(annotations, AnnotationVocabulary.skosPrefLabel)
        val rdfsLabels = literals(annotations, AnnotationVocabulary.rdfsLabel)
        val alternateLabels = distinctLiterals(annotations, AnnotationVocabulary.skosAltLabel)
        val definitions = distinctLiterals(annotations, AnnotationVocabulary.skosDefinition)
        val allLabels = (prefLabels + rdfsLabels + alternateLabels).distinct().sortedBy { it.stableKey }
        val preferred = choosePreferredLabel(
            entity,
            prefLabels,
            rdfsLabels,
            alternateLabels,
            allLabels,
            preferredLanguage,
        )
        val ambiguousLanguages = prefLabels
            .filter { it.languageTag != null }
            .groupBy { it.languageTag!!.lowercase() }
            .filterValues { values -> values.distinct().size > 1 }
            .keys
            .sorted()

        return SemanticLabelMetadata(
            preferredLabel = preferred?.first,
            preferredLabelSource = preferred?.second ?: PreferredLabelSource.None,
            alternateLabels = alternateLabels,
            definitions = definitions,
            annotations = annotations.all
                .filterNot { it.property in setOf(
                    AnnotationVocabulary.rdfsLabel,
                    AnnotationVocabulary.skosPrefLabel,
                    AnnotationVocabulary.skosAltLabel,
                    AnnotationVocabulary.skosDefinition,
                ) },
            ambiguousPreferredLabelLanguages = ambiguousLanguages,
        )
    }

    private fun choosePreferredLabel(
        entity: RdfResource,
        prefLabels: List<LocalizedText>,
        rdfsLabels: List<LocalizedText>,
        alternateLabels: List<LocalizedText>,
        allLabels: List<LocalizedText>,
        preferredLanguage: String?,
    ): Pair<LocalizedText, PreferredLabelSource>? {
        if (preferredLanguage != null) {
            prefLabels.firstOrNull { it.languageTag.equals(preferredLanguage, ignoreCase = true) }
                ?.let { return it to PreferredLabelSource.SkosPreferredLabel }
            rdfsLabels.firstOrNull { it.languageTag.equals(preferredLanguage, ignoreCase = true) }
                ?.let { return it to PreferredLabelSource.RdfsLabel }
        }

        prefLabels.firstOrNull { it.languageTag == null }
            ?.let { return it to PreferredLabelSource.SkosPreferredLabel }
        rdfsLabels.firstOrNull { it.languageTag == null }
            ?.let { return it to PreferredLabelSource.RdfsLabel }
        allLabels.firstOrNull()?.let { label ->
            val source = when {
                label in prefLabels -> PreferredLabelSource.SkosPreferredLabel
                label in rdfsLabels -> PreferredLabelSource.RdfsLabel
                label in alternateLabels -> PreferredLabelSource.AlternateLabel
                else -> PreferredLabelSource.None
            }
            return label to source
        }

        val fallback = readableLocalName(entity)
        return fallback?.let { LocalizedText(it) to PreferredLabelSource.IriLocalName }
    }

    private fun literals(annotations: ExplicitAnnotationSet, property: Iri): List<LocalizedText> =
        annotations.recognized[property]
            .orEmpty()
            .mapNotNull { it.value.toLocalizedText() }
            .sortedBy { it.stableKey }

    private fun distinctLiterals(annotations: ExplicitAnnotationSet, property: Iri): List<LocalizedText> =
        literals(annotations, property).distinct()

    private fun AnnotationValue.toLocalizedText(): LocalizedText? =
        (this as? AnnotationValue.Literal)?.literal?.toLocalizedText()

    private fun RdfLiteral.toLocalizedText(): LocalizedText =
        LocalizedText(
            lexicalForm = lexicalForm,
            languageTag = languageTag,
            datatypeIri = datatypeIri,
        )

    private fun readableLocalName(resource: RdfResource): String? {
        if (resource !is Iri) return null
        val value = resource.value
        val localName = value.substringAfterLast('#', value.substringAfterLast('/')).takeIf { it.isNotBlank() }
            ?: return null
        return localName
            .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
            .replace('_', ' ')
            .replace('-', ' ')
    }
}

public data class SemanticLabelMetadata(
    public val preferredLabel: LocalizedText?,
    public val preferredLabelSource: PreferredLabelSource,
    public val alternateLabels: List<LocalizedText>,
    public val definitions: List<LocalizedText>,
    public val annotations: List<AnnotationStatement>,
    public val ambiguousPreferredLabelLanguages: List<String>,
)
