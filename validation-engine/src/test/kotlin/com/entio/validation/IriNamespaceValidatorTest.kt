package com.entio.validation

import com.entio.core.EntioProjectConfig
import com.entio.core.Iri
import com.entio.core.IriNamespaceConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class IriNamespaceValidatorTest {
    private val validator = IriNamespaceValidator()

    @Test
    fun acceptsProjectsWithoutGeneratedIriNamespace(): Unit {
        val report = validator.validate(EntioProjectConfig(name = "simple", ontologySources = emptyList()))

        assertEquals(true, report.ok)
    }

    @Test
    fun acceptsHttpNamespaceWithDelimiter(): Unit {
        val config = EntioProjectConfig(
            name = "simple",
            ontologySources = emptyList(),
            iriNamespace = IriNamespaceConfig(Iri("https://example.com/entio/simple#")),
        )

        assertEquals(true, validator.validate(config).ok)
    }

    @Test
    fun rejectsInvalidNamespaceShape(): Unit {
        val config = EntioProjectConfig(
            name = "simple",
            ontologySources = emptyList(),
            iriNamespace = IriNamespaceConfig(Iri("relative")),
        )

        val report = validator.validate(config)

        assertEquals(false, report.ok)
        assertEquals("invalid-iri-namespace", report.issues.single().code)
    }
}
