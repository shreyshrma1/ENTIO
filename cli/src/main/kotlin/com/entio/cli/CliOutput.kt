package com.entio.cli

import com.entio.core.LoadedSymbol
import com.entio.core.SemanticDiff
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
