package com.entio.core

import kotlin.test.Test
import kotlin.test.assertEquals

class CoreTypesModuleTest {
    @Test
    fun exposesModuleName(): Unit {
        assertEquals("core-types", CoreTypesModule.NAME)
    }
}
