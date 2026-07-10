package com.entio.validation

import com.entio.core.ValidationSeverity
import com.entio.core.ValidationStatus
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectValidatorTest {
    private val validator = ProjectValidator()

    @Test
    fun returnsValidReportForSimpleProject(): Unit {
        val projectRoot = validProject()

        val report = validator.validateProject(projectRoot)

        assertEquals(ValidationStatus.Valid, report.status)
        assertTrue(report.ok)
        assertEquals(emptyList(), report.issues)
    }

    @Test
    fun returnsValidReportForProjectWithRdfTermRichOntology(): Unit {
        val projectRoot = projectWithConfig(
            """
            name: rdf-terms
            ontologySources:
              - id: rdf-terms
                path: ontology/rdf-terms.ttl
                format: turtle
            """.trimIndent(),
        )
        projectRoot.resolve("ontology").createDirectories()
        projectRoot.resolve("ontology/rdf-terms.ttl").writeText(
            """
            @prefix ex: <https://example.com/> .
            @prefix owl: <http://www.w3.org/2002/07/owl#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

            ex:Customer a owl:Class ;
              rdfs:label "Customer"@en ;
              rdfs:seeAlso ex:Account ;
              ex:status [ ex:code "active" ] ;
              ex:score "42"^^xsd:integer .

            ex:Account a owl:Class ;
              rdfs:label ex:AccountLabel .

            [] a owl:Class ;
              rdfs:label "Anonymous class" .
            """.trimIndent(),
        )

        val report = validator.validateProject(projectRoot)

        assertEquals(ValidationStatus.Valid, report.status)
        assertTrue(report.ok)
        assertEquals(emptyList(), report.issues)
    }

    @Test
    fun returnsErrorWhenProjectRootIsMissing(): Unit {
        val projectRoot = Files.createTempDirectory("entio-missing-root").resolve("missing")

        val report = validator.validateProject(projectRoot)

        assertEquals(ValidationStatus.Invalid, report.status)
        assertFalse(report.ok)
        assertEquals("missing-project-root", report.issues.single().code)
    }

    @Test
    fun returnsErrorWhenProjectRootIsNotDirectory(): Unit {
        val projectRoot = Files.createTempFile("entio-root-file", ".txt")

        val report = validator.validateProject(projectRoot)

        assertEquals(ValidationStatus.Invalid, report.status)
        assertEquals("project-root-not-directory", report.issues.single().code)
    }

    @Test
    fun returnsErrorForMissingConfigFile(): Unit {
        val projectRoot = Files.createTempDirectory("entio-missing-config")

        val report = validator.validateProject(projectRoot)

        assertEquals(ValidationStatus.Invalid, report.status)
        assertEquals("missing-entio-yaml", report.issues.single().code)
    }

    @Test
    fun returnsErrorForInvalidYaml(): Unit {
        val projectRoot = projectWithConfig(
            """
            name: simple
            ontologySources:
              - id: simple
                path: ontology/simple.ttl
                format: [turtle
            """.trimIndent(),
        )

        val report = validator.validateProject(projectRoot)

        assertEquals(ValidationStatus.Invalid, report.status)
        assertEquals("invalid-entio-yaml", report.issues.single().code)
    }

    @Test
    fun returnsErrorForMissingRequiredConfigField(): Unit {
        val projectRoot = projectWithConfig(
            """
            ontologySources:
              - id: simple
                path: ontology/simple.ttl
                format: turtle
            """.trimIndent(),
        )

        val report = validator.validateProject(projectRoot)

        assertEquals(ValidationStatus.Invalid, report.status)
        assertEquals("missing-project-name", report.issues.single().code)
    }

    @Test
    fun returnsErrorForDuplicateSourceIds(): Unit {
        val projectRoot = projectWithConfig(
            """
            name: simple
            ontologySources:
              - id: simple
                path: ontology/simple.ttl
                format: turtle
              - id: simple
                path: ontology/other.ttl
                format: turtle
            """.trimIndent(),
        )

        val report = validator.validateProject(projectRoot)

        assertEquals(ValidationStatus.Invalid, report.status)
        assertEquals("duplicate-ontology-source-id", report.issues.single().code)
    }

    @Test
    fun returnsErrorForUnsafeSourcePath(): Unit {
        val projectRoot = projectWithConfig(
            """
            name: simple
            ontologySources:
              - id: simple
                path: ../outside.ttl
                format: turtle
            """.trimIndent(),
        )

        val report = validator.validateProject(projectRoot)

        assertEquals(ValidationStatus.Invalid, report.status)
        assertEquals("unsafe-ontology-source-path", report.issues.single().code)
    }

    @Test
    fun returnsErrorForMissingOntologyFile(): Unit {
        val projectRoot = projectWithConfig(
            """
            name: simple
            ontologySources:
              - id: simple
                path: ontology/missing.ttl
                format: turtle
            """.trimIndent(),
        )

        val report = validator.validateProject(projectRoot)

        assertEquals(ValidationStatus.Invalid, report.status)
        assertEquals("missing-ontology-source-file", report.issues.single().code)
    }

    @Test
    fun returnsErrorForInvalidTurtle(): Unit {
        val projectRoot = projectWithConfig(
            """
            name: simple
            ontologySources:
              - id: simple
                path: ontology/simple.ttl
                format: turtle
            """.trimIndent(),
        )
        projectRoot.resolve("ontology").createDirectories()
        projectRoot.resolve("ontology/simple.ttl").writeText(
            """
            @prefix ex: <https://example.com/> .
            ex:Customer ex:relatedTo .
            """.trimIndent(),
        )

        val report = validator.validateProject(projectRoot)

        assertEquals(ValidationStatus.Invalid, report.status)
        assertEquals("invalid-turtle", report.issues.single().code)
    }

    @Test
    fun returnsIssuesInStableOrder(): Unit {
        val first = validator.validateProject(projectWithTwoInvalidTurtleSources())
        val second = validator.validateProject(projectWithTwoInvalidTurtleSources())

        assertEquals(first.issues.map { it.code to it.source }, second.issues.map { it.code to it.source })
        assertEquals(listOf("first", "second"), first.issues.map { it.source })
    }

    private fun validProject(): Path {
        val projectRoot = projectWithConfig(
            """
            name: simple
            ontologySources:
              - id: simple
                path: ontology/simple.ttl
                format: turtle
            """.trimIndent(),
        )
        projectRoot.resolve("ontology").createDirectories()
        projectRoot.resolve("ontology/simple.ttl").writeText(
            """
            @prefix ex: <https://example.com/> .
            @prefix owl: <http://www.w3.org/2002/07/owl#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

            ex:Customer a owl:Class ;
              rdfs:label "Customer" .
            """.trimIndent(),
        )
        return projectRoot
    }

    private fun projectWithTwoInvalidTurtleSources(): Path {
        val projectRoot = projectWithConfig(
            """
            name: simple
            ontologySources:
              - id: second
                path: ontology/second.ttl
                format: turtle
              - id: first
                path: ontology/first.ttl
                format: turtle
            """.trimIndent(),
        )
        projectRoot.resolve("ontology").createDirectories()
        projectRoot.resolve("ontology/first.ttl").writeText("@prefix ex: <https://example.com/> . ex:A ex:p .")
        projectRoot.resolve("ontology/second.ttl").writeText("@prefix ex: <https://example.com/> . ex:B ex:p .")
        return projectRoot
    }

    private fun projectWithConfig(config: String): Path {
        val projectRoot = Files.createTempDirectory("entio-validation")
        projectRoot.resolve("entio.yaml").writeText(config)
        return projectRoot
    }
}
