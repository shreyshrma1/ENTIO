package com.entio.validation

import com.entio.core.DeletionPlan
import com.entio.core.DeletionPlanStatus
import com.entio.core.EntityCandidate
import com.entio.core.Iri
import com.entio.core.ProposalBaseline
import com.entio.core.SymbolKind
import kotlin.test.Test
import kotlin.test.assertEquals

class DeletionValidatorTest {
    private val validator = DeletionValidator()
    private val target = EntityCandidate(Iri("https://example.com/Customer"), "Customer", SymbolKind.Class, "simple")
    private val validPlan = DeletionPlan(target = target, status = DeletionPlanStatus.Safe)

    @Test
    fun reportsMissingTarget(): Unit {
        val report = validator.validate(null, "simple", "simple")

        assertEquals("missing-deletion-target", report.issues.single().code)
    }

    @Test
    fun reportsWrongSourceAndUnresolvedDependencies(): Unit {
        val plan = validPlan.copy(
            target = target.copy(sourceId = "other"),
            status = DeletionPlanStatus.RequiresExplicitDependencies,
        )

        val report = validator.validate(plan, "simple", "simple")

        assertEquals(
            listOf("wrong-deletion-source", "unresolved-deletion-dependencies"),
            report.issues.map { it.code },
        )
    }

    @Test
    fun reportsStaleBaseline(): Unit {
        val before = ProposalBaseline("project-a", "simple", "simple.ttl", "source-a", "graph-a")
        val after = before.copy(graphFingerprint = "graph-b")

        val report = validator.validate(validPlan, "simple", "simple", before, after)

        assertEquals("stale-deletion-baseline", report.issues.single().code)
    }

    @Test
    fun acceptsSafeCurrentPlan(): Unit {
        val report = validator.validate(validPlan, "simple", "simple")

        assertEquals(true, report.ok)
    }

    @Test
    fun reportsInvalidDependencySelection(): Unit {
        val report = validator.validate(
            validPlan.copy(status = DeletionPlanStatus.InvalidDependencySelection),
            "simple",
            "simple",
        )

        assertEquals("invalid-deletion-dependency-selection", report.issues.single().code)
    }
}
