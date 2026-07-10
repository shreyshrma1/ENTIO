package com.entio.semantic

import com.entio.core.OntologyFormat
import com.entio.core.ResolvedOntologySource
import java.nio.file.Files
import kotlin.io.path.writeText

internal object SemanticEngineTestFixtures {
    fun resolvedSource(
        content: String,
        id: String = "simple",
    ): ResolvedOntologySource {
        val path = Files.createTempFile("entio-semantic", ".ttl")
        path.writeText(content)
        return ResolvedOntologySource(
            id = id,
            path = path,
            format = OntologyFormat.Turtle,
        )
    }
}
