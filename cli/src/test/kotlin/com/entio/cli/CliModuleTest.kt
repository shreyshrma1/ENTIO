package com.entio.cli

import kotlin.test.Test
import kotlin.test.assertEquals

class CliModuleTest {
    @Test
    fun exposesModuleName(): Unit {
        assertEquals("cli", CliModule.NAME)
    }
}
