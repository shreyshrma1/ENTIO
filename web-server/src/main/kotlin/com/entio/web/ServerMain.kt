package com.entio.web

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import com.entio.web.contract.InMemoryProjectRegistry
import com.entio.web.contract.WebApplicationDependencies
import java.nio.file.Files
import java.nio.file.Path

public fun main(): Unit {
    embeddedServer(
        factory = Netty,
        port = 8080,
        host = "0.0.0.0",
        module = { module(developmentDependencies()) },
    ).start(wait = true)
}

internal fun developmentDependencies(projectRoot: Path = configuredProjectRoot()): WebApplicationDependencies {
    val normalizedRoot = projectRoot.toAbsolutePath().normalize()
    val registry = InMemoryProjectRegistry(setOf(normalizedRoot.parent))
    registry.register("simple", "Simple ontology", normalizedRoot)
    return WebApplicationDependencies(projectRegistry = registry)
}

private fun configuredProjectRoot(): Path {
    val configured = System.getenv("ENTIO_PROJECT_ROOT")?.takeIf(String::isNotBlank)
    if (configured != null) return Path.of(configured)

    val workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
    return generateSequence(workingDirectory) { it.parent }
        .map { it.resolve("examples/simple-ontology") }
        .firstOrNull(Files::isDirectory)
        ?: workingDirectory.resolve("examples/simple-ontology")
}
