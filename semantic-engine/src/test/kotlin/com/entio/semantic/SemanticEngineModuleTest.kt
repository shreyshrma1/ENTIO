package com.entio.semantic

import kotlin.test.Test
import kotlin.test.assertEquals

class SemanticEngineModuleTest {
    @Test
    fun exposesModuleName(): Unit {
        assertEquals("semantic-engine", SemanticEngineModule.NAME)
    }
}
