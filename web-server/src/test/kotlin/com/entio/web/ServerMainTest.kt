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
        val emptyDemoRoot = Files.createDirectory(examplesRoot.resolve("empty-demo"))

        val dependencies = developmentDependencies(projectRoot)

        val simpleProject = dependencies.projectRegistry.find("simple")
        assertNotNull(simpleProject)
        assertEquals("Simple ontology", simpleProject.displayName)
        assertEquals(projectRoot.toAbsolutePath().normalize(), dependencies.projectRegistry.rootFor("simple"))
        val emptyDemo = dependencies.projectRegistry.find("empty-demo")
        assertNotNull(emptyDemo)
        assertEquals("Empty demo", emptyDemo.displayName)
        assertEquals(emptyDemoRoot.toAbsolutePath().normalize(), dependencies.projectRegistry.rootFor("empty-demo"))
    }
}
