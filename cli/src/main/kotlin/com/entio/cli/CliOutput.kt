package com.entio.cli

import com.entio.core.LoadedSymbol
import com.entio.core.ValidationIssue
import com.entio.core.ValidationReport
import java.io.PrintWriter

internal fun PrintWriter.printValidationReport(report: ValidationReport): Unit {
    println("Validation: ${report.status.name.lowercase()}")
    report.issues.forEach { issue -> printValidationIssue(issue) }
}

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

private fun PrintWriter.printValidationIssue(issue: ValidationIssue): Unit {
    val source = issue.source?.let { " $it" }.orEmpty()
    println("${issue.severity.name.uppercase()} ${issue.code}$source ${issue.message}")
}
