package com.entio.diff

import com.entio.core.ConsistencyStatus
import com.entio.core.EntioResult
import com.entio.core.FactOrigin
import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.ReasoningClassRelationship
import com.entio.core.ReasoningFingerprints
import com.entio.core.ReasoningPropertyRelationship
import com.entio.core.ReasoningResult
import com.entio.core.ReasoningRunMetadata
import com.entio.core.ReasoningRunStatus
import com.entio.core.RdfLiteral
import com.entio.core.ShaclGraphIdentity
import com.entio.core.ShaclPath
import com.entio.core.ShaclSeverity
import com.entio.core.ShaclShapeId
import com.entio.core.ShaclValidationMode
import com.entio.core.ShaclValidationReport
import com.entio.core.ShaclValidationResult
import com.entio.core.ShaclValidationStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProposalImpactAnalyzerTest {
    private val analyzer = ProposalImpactAnalyzer()
    private val shape = ShaclShapeId(Iri("https://example.com/AccountShape"), "shapes")
    private val identity = ShaclGraphIdentity(listOf("data"), listOf("shapes"), "data", "shapes")

    @Test
    fun distinguishesNewWorsenedUnchangedAndResolvedFindings(): Unit {
        val current = ShaclValidationReport(
            ShaclValidationStatus.Completed,
            ShaclValidationMode.AssertedOnly,
            identity,
            results = listOf(
                result("old", "old-value", ShaclSeverity.Violation),
                result("unchanged", "same", ShaclSeverity.Warning),
                result("resolved", "gone", ShaclSeverity.Violation, Iri("https://example.com/ResolvedShape")),
            ),
        )
        val preview = ShaclValidationReport(
            ShaclValidationStatus.Completed,
            ShaclValidationMode.AssertedOnly,
            identity,
            results = listOf(
                result("old", "new-value", ShaclSeverity.Violation),
                result("unchanged", "same", ShaclSeverity.Warning),
                result("new", "new", ShaclSeverity.Violation, Iri("https://example.com/NewShape")),
            ),
        )

        val report = assertIs<EntioResult.Success<com.entio.core.ProposalImpactReport>>(
            analyzer.analyze(GraphState(), GraphState(), null, null, current, preview),
        ).value

        assertEquals(1, report.shaclImpact.worsenedResults.size)
        assertEquals(1, report.shaclImpact.newResults.size)
        assertEquals(1, report.shaclImpact.unchangedResults.size)
        assertEquals(1, report.shaclImpact.resolvedResults.size)
        assertEquals(com.entio.core.BaselineImpactStatus.BlocksApproval, report.status)
        assertTrue(report.blockingMessages.any { it.contains("introduces") })
    }

    @Test
    fun distinguishesReasoningImprovementsFromNewBlockingIssues(): Unit {
        val currentReasoning = reasoning(ConsistencyStatus.Consistent, emptyList())
        val previewReasoning = reasoning(
            ConsistencyStatus.Inconsistent,
            listOf(Iri("https://example.com/Broken")),
        )
        val before = GraphState(setOf(GraphTriple(Iri("https://example.com/a"), Iri("https://example.com/p"), RdfLiteral("before"))))
        val after = GraphState(setOf(GraphTriple(Iri("https://example.com/a"), Iri("https://example.com/p"), RdfLiteral("after"))))

        val report = assertIs<EntioResult.Success<com.entio.core.ProposalImpactReport>>(
            analyzer.analyze(before, after, currentReasoning, previewReasoning, null, null),
        ).value

        assertEquals(ConsistencyStatus.Consistent, currentReasoning.consistency)
        assertTrue(report.reasoningImpact.consistencyChanged)
        assertEquals(listOf(Iri("https://example.com/Broken")), report.reasoningImpact.unsatisfiableClassesAdded)
        assertEquals(com.entio.core.BaselineImpactStatus.BlocksApproval, report.status)
        assertEquals(2, report.explicitDiff.entries.size)
    }

    @Test
    fun incompleteImpactBlocksWithoutMutatingGraphs(): Unit {
        val report = assertIs<EntioResult.Success<com.entio.core.ProposalImpactReport>>(
            analyzer.analyze(
                GraphState(),
                GraphState(),
                reasoning(ConsistencyStatus.Unknown, emptyList(), ReasoningRunStatus.Incomplete),
                reasoning(ConsistencyStatus.Unknown, emptyList(), ReasoningRunStatus.Incomplete),
                null,
                null,
            ),
        ).value

        assertEquals(com.entio.core.BaselineImpactStatus.Incomplete, report.status)
        assertTrue(report.blockingMessages.any { it.contains("incomplete") })
    }

    private fun result(
        id: String,
        value: String,
        severity: ShaclSeverity,
        shapeIri: Iri = Iri("https://example.com/AccountShape"),
    ): ShaclValidationResult = ShaclValidationResult(
        severity = severity,
        message = id,
        focusNode = Iri("https://example.com/Account1"),
        path = ShaclPath.DirectProperty(Iri("https://example.com/code")),
        shape = ShaclShapeId(shapeIri, "shapes"),
        constraint = com.entio.core.ShaclConstraintKind.Pattern,
        value = RdfLiteral(value),
        sourceId = "data",
        resultId = "$id-$value",
    )

    private fun reasoning(
        consistency: ConsistencyStatus,
        unsatisfiable: List<Iri>,
        status: ReasoningRunStatus = ReasoningRunStatus.Completed,
    ): ReasoningResult = ReasoningResult(
        metadata = ReasoningRunMetadata(
            status = status,
            reasonerName = "test",
            reasonerVersion = "test",
            owlApiVersion = "test",
            fingerprints = ReasoningFingerprints("graph", "imports", "config"),
            importClosureComplete = status == ReasoningRunStatus.Completed,
        ),
        consistency = consistency,
        classRelationships = emptyList<ReasoningClassRelationship>(),
        propertyRelationships = listOf(
            ReasoningPropertyRelationship(Iri("https://example.com/a"), Iri("https://example.com/p"), Iri("https://example.com/b"), FactOrigin.Inferred),
        ),
        unsatisfiableClasses = unsatisfiable,
    )
}
