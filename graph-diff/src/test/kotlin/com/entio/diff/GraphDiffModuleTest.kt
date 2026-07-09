package com.entio.diff

import kotlin.test.Test
import kotlin.test.assertEquals

class GraphDiffModuleTest {
    @Test
    fun exposesModuleName(): Unit {
        assertEquals("graph-diff", GraphDiffModule.NAME)
    }
}
