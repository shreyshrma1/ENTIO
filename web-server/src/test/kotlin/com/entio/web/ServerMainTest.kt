package com.entio.web

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ServerMainTest {
    @Test
    fun developmentDependenciesRegistersTheExampleProject() {
        val examplesRoot = Files.createTempDirectory("entio-web-examples")
        val projectRoot = Files.createDirectory(examplesRoot.resolve("simple-ontology"))
        val demoRoot = Files.createDirectory(examplesRoot.resolve("demo"))

        val dependencies = developmentDependencies(projectRoot)

        val simpleProject = dependencies.projectRegistry.find("simple")
        assertNotNull(simpleProject)
        assertEquals("Simple ontology", simpleProject.displayName)
        assertEquals(projectRoot.toAbsolutePath().normalize(), dependencies.projectRegistry.rootFor("simple"))
        val demo = dependencies.projectRegistry.find("demo")
        assertNotNull(demo)
        assertEquals("Demo", demo.displayName)
        assertEquals(demoRoot.toAbsolutePath().normalize(), dependencies.projectRegistry.rootFor("demo"))
    }
}
