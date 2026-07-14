package com.entio.validation

import com.entio.core.GraphChange
import com.entio.core.GraphChangeKind
import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.RdfLiteral
import com.entio.core.RdfResource
import com.entio.core.ValidationIssue
import com.entio.core.ValidationSeverity
import com.entio.semantic.AnnotationVocabulary

/** Deterministic validation for semantic metadata changes before proposal approval. */
public class SemanticMetadataValidator {
    public fun validate(
        currentGraph: GraphState,
        changes: List<GraphChange>,
    ): List<ValidationIssue> {
        val additions = changes.filter { it.kind == GraphChangeKind.Addition }
        val removals = changes.filter { it.kind == GraphChangeKind.Removal }
        val allTriples = (currentGraph.triples - removals.map { it.triple }.toSet()) + additions.map { it.triple }
        val annotationProperties = allTriples
            .filter { it.predicate == RDF_TYPE && it.objectTerm == OWL_ANNOTATION_PROPERTY }
            .map { it.subjectResource }
            .toSet()
        val knownResources = currentGraph.triples.flatMap(::resources).toSet() +
            additions.filter { it.triple.predicate == RDF_TYPE }.map { it.triple.subjectResource }

        val issues = mutableListOf<ValidationIssue>()
        changes.forEachIndexed { index, change ->
            val property = change.triple.predicate
            val isStandardMetadata = property in STANDARD_METADATA_PROPERTIES
            val isAnnotationProperty = property in annotationProperties
            if (!isStandardMetadata && !isAnnotationProperty) return@forEachIndexed

            val source = "changeSet.changes[$index]"
            if (change.triple.subjectResource !in knownResources) {
                issues += issue(
                    code = "missing-semantic-target",
                    message = "Semantic metadata target '${change.triple.subjectResource.value}' is not present in the current graph.",
                    source = source,
                )
            }

            if (isStandardMetadata && property in LITERAL_METADATA_PROPERTIES && change.triple.objectTerm !is RdfLiteral) {
                issues += issue(
                    code = if (property == AnnotationVocabulary.rdfsLabel) "invalid-label" else "invalid-semantic-value",
                    message = if (property == AnnotationVocabulary.rdfsLabel) {
                        "Entity labels must be RDF literals."
                    } else {
                        "Metadata property '${property.value}' requires a literal value."
                    },
                    source = source,
                )
            }
            (change.triple.objectTerm as? RdfLiteral)?.let { literal ->
                issues += validateLiteral(literal, source)
            }

            if (!isStandardMetadata) {
                val hasIncompatibleType = allTriples.any {
                    it.subjectResource == property && it.predicate == RDF_TYPE &&
                        (it.objectTerm == OWL_OBJECT_PROPERTY || it.objectTerm == OWL_DATATYPE_PROPERTY)
                }
                if (hasIncompatibleType || property !in annotationProperties) {
                    issues += issue(
                        code = if (hasIncompatibleType) "incompatible-annotation-property" else "missing-annotation-property",
                        message = if (hasIncompatibleType) {
                            "Property '${property.value}' is not an annotation property."
                        } else {
                            "Annotation property '${property.value}' is not declared."
                        },
                        source = source,
                    )
                }
            }
        }

        additions
            .filter { it.triple.predicate == AnnotationVocabulary.skosPrefLabel }
            .map { it.triple.subjectResource }
            .distinct()
            .forEach { subject ->
                val labelsByLanguage = allTriples
                    .filter { it.subjectResource == subject && it.predicate == AnnotationVocabulary.skosPrefLabel }
                    .mapNotNull { it.objectTerm as? RdfLiteral }
                    .groupBy { it.languageTag.orEmpty().lowercase() }
                labelsByLanguage
                    .filterValues { values -> values.distinct().size > 1 }
                    .keys
                    .sorted()
                    .forEach { language ->
                        issues += issue(
                            code = "ambiguous-preferred-label",
                            message = "Multiple distinct preferred labels exist for '${subject.value}' in language '${language.ifBlank { "none" }}'.",
                            source = subject.value,
                        )
                    }
            }

        return issues
    }

    private fun resources(triple: GraphTriple): List<RdfResource> = buildList {
        add(triple.subjectResource)
        (triple.objectTerm as? RdfResource)?.let(::add)
    }

    private fun validateLiteral(literal: RdfLiteral, source: String): List<ValidationIssue> = buildList {
        val languageTag = literal.languageTag
        if (languageTag != null && !LANGUAGE_TAG.matches(languageTag)) {
            add(issue("invalid-language-tag", "Language tag '$languageTag' is not valid.", source))
        }
        if (languageTag != null && literal.datatypeIri != null && literal.datatypeIri != RDF_LANG_STRING) {
            add(issue("incompatible-literal", "A literal cannot contain both a language tag and an explicit datatype.", source))
        }
        val datatype = literal.datatypeIri
        if (datatype != null && datatype != RDF_LANG_STRING && datatype.value !in SUPPORTED_DATATYPES) {
            add(issue("unsupported-datatype", "Datatype '${datatype.value}' is not supported.", source))
        }
    }

    private fun issue(code: String, message: String, source: String): ValidationIssue =
        ValidationIssue(ValidationSeverity.Error, code, message, source)

    private companion object {
        private val RDF_TYPE = Iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
        private val RDF_LANG_STRING = Iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#langString")
        private val OWL_ANNOTATION_PROPERTY = Iri("http://www.w3.org/2002/07/owl#AnnotationProperty")
        private val OWL_OBJECT_PROPERTY = Iri("http://www.w3.org/2002/07/owl#ObjectProperty")
        private val OWL_DATATYPE_PROPERTY = Iri("http://www.w3.org/2002/07/owl#DatatypeProperty")
        private val STANDARD_METADATA_PROPERTIES = AnnotationVocabulary.recognizedProperties
        private val LITERAL_METADATA_PROPERTIES = setOf(
            AnnotationVocabulary.rdfsLabel,
            AnnotationVocabulary.rdfsComment,
            AnnotationVocabulary.skosPrefLabel,
            AnnotationVocabulary.skosAltLabel,
            AnnotationVocabulary.skosDefinition,
        )
        private val LANGUAGE_TAG = Regex("[A-Za-z]{1,8}(?:-[A-Za-z0-9]{1,8})*")
        private val SUPPORTED_DATATYPES = setOf(
            "http://www.w3.org/2001/XMLSchema#string",
            "http://www.w3.org/2001/XMLSchema#boolean",
            "http://www.w3.org/2001/XMLSchema#integer",
            "http://www.w3.org/2001/XMLSchema#decimal",
            "http://www.w3.org/2001/XMLSchema#date",
            "http://www.w3.org/2001/XMLSchema#dateTime",
        )
    }
}
