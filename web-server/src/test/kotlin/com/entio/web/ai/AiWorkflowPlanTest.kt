package com.entio.web.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AiWorkflowPlanTest {
    private val validator = AiWorkflowPlanValidator()
    private val task = taskFixture().copy(size = AiTaskSize.MEDIUM)

    @Test
    fun validatesAndStablyOrdersDependencyDag(): Unit {
        val ordered = validator.validate(task, listOf(pkg("b", listOf("a")), pkg("a"), pkg("c", listOf("a"))))

        assertEquals(listOf("a", "b", "c"), ordered.map(AiWorkPackage::id))
    }

    @Test
    fun rejectsMissingCyclicDuplicateAndOutOfScopePackages(): Unit {
        assertCode("missing-work-package-dependency") { validator.validate(task, listOf(pkg("a", listOf("missing")))) }
        assertCode("cyclic-work-package-dependency") {
            validator.validate(task, listOf(pkg("a", listOf("b")), pkg("b", listOf("a"))))
        }
        assertCode("duplicate-work-package") { validator.validate(task, listOf(pkg("a"), pkg("a"))) }
        assertCode("work-package-source-scope") { validator.validate(task, listOf(pkg("a", sources = listOf("outside")))) }
        assertCode("work-package-edit-limit") { validator.validate(task, listOf(pkg("a", edits = 21))) }
        assertCode("work-package-bundle-invalid") {
            validator.validate(task, listOf(pkg("a", bundle = AiCapabilityBundleId.HELP)))
        }
    }

    private fun assertCode(code: String, block: () -> Unit) {
        assertEquals(code, assertFailsWith<AiWorkflowPlanFailure> { block() }.code)
    }
}

internal fun pkg(
    id: String,
    dependencies: List<String> = emptyList(),
    sources: List<String> = listOf("simple"),
    edits: Int = 2,
    bundle: AiCapabilityBundleId = AiCapabilityBundleId.ONTOLOGY_EDITING,
    risks: Set<AiPlanRiskFlag> = emptySet(),
) = AiWorkPackage(
    id = id,
    title = "Package $id",
    dependsOn = dependencies,
    expectedSourceIds = sources,
    bundleId = bundle,
    estimate = AiWorkEstimate(edits, if (edits > 10) "LARGE" else "SMALL"),
    riskFlags = risks,
)
