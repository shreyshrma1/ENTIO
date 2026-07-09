package com.entio.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedModuleTest {
    @Test
    fun exposesModuleName(): Unit {
        assertEquals("shared", SharedModule.NAME)
    }
}
