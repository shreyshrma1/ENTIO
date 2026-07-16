package com.entio.web

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ServerMainTest {
    @Test
    fun developmentDependenciesRegistersTheExampleProject() {
        val projectRoot = Files.createTempDirectory("entio-web-project")

        val dependencies = developmentDependencies(projectRoot)

        val project = dependencies.projectRegistry.find("simple")
        assertNotNull(project)
        assertEquals("Simple ontology", project.displayName)
        assertEquals(projectRoot.toAbsolutePath().normalize(), dependencies.projectRegistry.rootFor("simple"))
    }
}
