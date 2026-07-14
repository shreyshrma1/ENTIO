package com.entio.cli

import com.entio.core.LoadedSymbol
import com.entio.core.RdfLiteral
import com.entio.core.RdfResource
import com.entio.core.AnnotationStatement
import com.entio.core.DatatypePropertyAssertion
import com.entio.core.LocalizedText
import com.entio.core.ObjectPropertyAssertion
import com.entio.core.OntologyEntityDescriptor
import com.entio.core.SemanticDiff
import com.entio.core.SemanticSearchResult
import com.entio.core.SymbolDetails
import com.entio.core.SymbolRelationship
import com.entio.core.ValidationIssue
import com.entio.core.ValidationReport
import java.io.PrintWriter

internal fun PrintWriter.printValidationReport(report: ValidationReport): Unit {
    println("Validation: ${report.status.name.lowercase()}")
    report.issues.forEach { issue -> printValidationIssue(issue) }
}

internal fun PrintWriter.printValidationReportJson(
    command: String,
    report: ValidationReport,
): Unit = println(
    jsonObject(
        "command" to command,
        "ok" to report.ok,
        "validation" to validationReportJson(report),
    ).encoded,
)

internal fun PrintWriter.printValidationIssues(issues: List<ValidationIssue>): Unit {
    issues.forEach { issue -> printValidationIssue(issue) }
}

internal fun PrintWriter.printSymbols(symbols: List<LoadedSymbol>): Unit {
    if (symbols.isEmpty()) {
        println("No symbols found.")
        return
    }

    symbols.forEach { symbol ->
        val label = symbol.label?.let { " \"$it\"" }.orEmpty()
        println("${symbol.kind.name} ${symbol.iri.value}$label [${symbol.sourceId}]")
    }
}

internal fun PrintWriter.printSymbolsJson(
    command: String,
    symbols: List<LoadedSymbol>,
): Unit = println(
    jsonObject(
        "command" to command,
        "ok" to true,
        "symbols" to jsonArray(symbols.map(::symbolJson)),
    ).encoded,
)

internal fun PrintWriter.printSemanticDiffJson(
    command: String,
    diff: SemanticDiff,
): Unit = println(
    jsonObject(
        "command" to command,
        "ok" to diff.entries.isNotEmpty().not(),
        "diff" to semanticDiffJson(diff),
    ).encoded,
)

internal fun validationReportJson(report: ValidationReport): JsonFragment =
    jsonObject(
        "status" to report.status.name.lowercase(),
        "ok" to report.ok,
        "issues" to jsonArray(report.issues.map(::validationIssueJson)),
    )

internal fun validationIssueJson(issue: ValidationIssue): JsonFragment =
    jsonObject(
        "severity" to issue.severity.name.lowercase(),
        "code" to issue.code,
        "message" to issue.message,
        "source" to issue.source,
    )

internal fun symbolJson(symbol: LoadedSymbol): JsonFragment =
    jsonObject(
        "iri" to symbol.iri.value,
        "label" to symbol.label,
        "kind" to symbol.kind.name,
        "sourceId" to symbol.sourceId,
    )

internal fun symbolDetailsJson(details: SymbolDetails): JsonFragment =
    jsonObject(
        "iri" to details.symbol.iri.value,
        "label" to details.symbol.label,
        "kind" to details.symbol.kind.name,
        "sourceId" to details.symbol.sourceId,
        "relationships" to jsonArray(details.relationships.map(::symbolRelationshipJson)),
    )

private fun symbolRelationshipJson(relationship: SymbolRelationship): JsonFragment =
    jsonObject(
        "direction" to relationship.direction.name.lowercase(),
        "kind" to relationship.kind.name.lowercase(),
        "predicate" to relationship.predicate.value,
        "predicateLabel" to relationship.predicateLabel,
        "value" to rdfTermJson(relationship.value),
        "valueLabel" to relationship.valueLabel,
        "sourceId" to relationship.sourceId,
    )

internal fun rdfTermJson(term: com.entio.core.RdfTerm): JsonFragment =
    when (term) {
        is RdfResource -> jsonObject(
            "kind" to if (term is com.entio.core.BlankNodeResource) "blank-node" else "iri",
            "value" to term.value,
            "datatype" to null,
            "language" to null,
        )
        is RdfLiteral -> jsonObject(
            "kind" to "literal",
            "value" to term.lexicalForm,
            "datatype" to term.datatypeIri?.value,
            "language" to term.languageTag,
        )
    }

internal fun semanticDescriptorJson(descriptor: OntologyEntityDescriptor): JsonFragment {
    val common = descriptor.common
    val fields = mutableListOf<Pair<String, Any?>>(
        "iri" to common.entity.value,
        "kind" to common.kind.name,
        "sourceId" to common.sourceId,
        "sourceOntologyId" to common.sourceOntologyId,
        "locality" to common.locality.name,
        "preferredLabelSource" to common.preferredLabelSource.name,
        "preferredLabel" to common.preferredLabel?.let(::localizedTextJson),
        "ambiguousPreferredLabelLanguages" to jsonArray(common.ambiguousPreferredLabelLanguages),
        "alternateLabels" to jsonArray(common.alternateLabels.map(::localizedTextJson)),
        "definitions" to jsonArray(common.definitions.map(::localizedTextJson)),
        "annotations" to jsonArray(common.annotations.map(::annotationStatementJson)),
    )

    when (descriptor) {
        is OntologyEntityDescriptor.Class -> fields += listOf(
            "directSuperclasses" to jsonArray(descriptor.directSuperclasses.map { it.value }),
            "directSubclasses" to jsonArray(descriptor.directSubclasses.map { it.value }),
            "directlyTypedIndividuals" to jsonArray(descriptor.directlyTypedIndividuals.map { it.value }),
        )
        is OntologyEntityDescriptor.ObjectProperty -> fields += listOf(
            "domains" to jsonArray(descriptor.domains.map { it.value }),
            "ranges" to jsonArray(descriptor.ranges.map { it.value }),
            "directAssertions" to jsonArray(descriptor.directAssertions.map(::objectAssertionJson)),
        )
        is OntologyEntityDescriptor.DatatypeProperty -> fields += listOf(
            "domains" to jsonArray(descriptor.domains.map { it.value }),
            "datatypeRanges" to jsonArray(descriptor.datatypeRanges.map { it.value }),
            "directAssertions" to jsonArray(descriptor.directAssertions.map(::datatypeAssertionJson)),
        )
        is OntologyEntityDescriptor.AnnotationProperty -> fields += listOf(
            "statementsUsingProperty" to jsonArray(descriptor.statementsUsingProperty.map(::annotationStatementJson)),
        )
        is OntologyEntityDescriptor.Individual -> fields += listOf(
            "assertedTypes" to jsonArray(descriptor.assertedTypes.map { it.value }),
            "objectPropertyAssertions" to jsonArray(descriptor.objectPropertyAssertions.map(::objectAssertionJson)),
            "datatypePropertyAssertions" to jsonArray(descriptor.datatypePropertyAssertions.map(::datatypeAssertionJson)),
        )
    }

    return jsonObject(*fields.toTypedArray())
}

internal fun semanticSearchResultJson(result: SemanticSearchResult): JsonFragment =
    jsonObject(
        "reason" to result.reason.name,
        "rank" to result.rank,
        "descriptor" to semanticDescriptorJson(result.descriptor),
    )

private fun localizedTextJson(text: LocalizedText): JsonFragment =
    jsonObject(
        "value" to text.lexicalForm,
        "language" to text.languageTag,
        "datatype" to text.datatypeIri?.value,
    )

private fun annotationStatementJson(statement: AnnotationStatement): JsonFragment =
    jsonObject(
        "subject" to statement.subject.value,
        "property" to statement.property.value,
        "value" to rdfTermJson(statement.value.term),
        "sourceId" to statement.sourceId,
    )

private fun objectAssertionJson(assertion: ObjectPropertyAssertion): JsonFragment =
    jsonObject(
        "subject" to assertion.subject.value,
        "property" to assertion.property.value,
        "value" to assertion.value.value,
        "sourceId" to assertion.sourceId,
    )

private fun datatypeAssertionJson(assertion: DatatypePropertyAssertion): JsonFragment =
    jsonObject(
        "subject" to assertion.subject.value,
        "property" to assertion.property.value,
        "value" to rdfTermJson(assertion.value),
        "sourceId" to assertion.sourceId,
    )

internal fun semanticDiffJson(diff: SemanticDiff): JsonFragment =
    jsonObject(
        "entryCount" to diff.entries.size,
        "entries" to jsonArray(
            diff.entries.map { entry ->
                jsonObject(
                    "kind" to entry.kind.name.lowercase(),
                    "subject" to entry.subject.value,
                    "predicate" to entry.predicate?.value,
                    "objectValue" to entry.objectValue,
                    "description" to entry.description,
                )
            },
        ),
    )

private fun PrintWriter.printValidationIssue(issue: ValidationIssue): Unit {
    val source = issue.source?.let { " $it" }.orEmpty()
    println("${issue.severity.name.uppercase()} ${issue.code}$source ${issue.message}")
}
