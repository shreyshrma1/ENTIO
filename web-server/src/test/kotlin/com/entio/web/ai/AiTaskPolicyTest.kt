package com.entio.web.ai

import kotlin.test.Test
import kotlin.test.assertEquals

class AiTaskPolicyTest {
    @Test
    fun activeTaskLimitsMatchApprovedPolicy(): Unit {
        val policy = AiTaskPolicy()

        assertEquals(1, policy.maxActiveMutatingTasksPerUserProject)
        assertEquals(3, policy.maxConcurrentReadOnlyTasksPerUserProject)
    }
}
