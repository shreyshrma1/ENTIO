package com.entio.validation

import kotlin.test.Test
import kotlin.test.assertEquals

class ValidationEngineModuleTest {
    @Test
    fun exposesModuleName(): Unit {
        assertEquals("validation-engine", ValidationEngineModule.NAME)
    }
}
