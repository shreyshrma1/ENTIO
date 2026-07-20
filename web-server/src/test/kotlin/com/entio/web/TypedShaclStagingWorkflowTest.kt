package com.entio.web

import com.entio.core.EntioResult
import com.entio.core.ShaclValidationMode
import com.entio.core.ShaclValidationStatus
import com.entio.semantic.ProjectLoader
import com.entio.semantic.ShaclGraphLoader
import com.entio.semantic.ShaclValidationService
import com.entio.web.contract.InMemoryProjectRegistry
import com.entio.web.contract.WebStageChangeRequest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TypedShaclStagingWorkflowTest {
    @Test
    fun blockingShapePreviewRetainsExactFindingImpactForReview(): Unit {
        val fixture = fixture()
        val service = StagingWorkflowService(fixture.registry)
        service.stage("simple", createBorrowerShape(severity = "Violation"), "alice")

        val snapshot = service.preview("simple", "alice")

        assertEquals("VERIFICATIONFAILED", snapshot.proposal?.status)
        val impact = assertNotNull(snapshot.proposal?.shaclImpact)
        assertEquals(1, impact.newFindings.size)
        assertEquals("Shrey", impact.newFindings.single().focusNode.substringAfterLast('#'))
        assertTrue(snapshot.proposal?.diff.orEmpty().any { it.description.contains("minimum count") })
    }

    @Test
    fun alreadySatisfiedOntologyEditDoesNotBlockAValidShapeProposal(): Unit {
        val fixture = fixture()
        val service = StagingWorkflowService(fixture.registry)
        service.stage(
            "simple",
            WebStageChangeRequest(
                sourceId = "simple",
                editType = "set-entity-label",
                resourceLabel = "Customer",
                label = "Customer",
            ),
            "alice",
        )
        service.stage("simple", createBorrowerShape(severity = "Warning"), "alice")

        val snapshot = service.preview("simple", "alice")

        assertEquals("READYFORREVIEW", snapshot.proposal?.status)
        assertEquals(listOf("shapes"), snapshot.proposal?.targetSourceIds)
        assertTrue(snapshot.proposal?.diff.orEmpty().any { it.description.contains("minimum count") })
        assertTrue(snapshot.proposal?.diff.orEmpty().none { it.description.contains("Customer · label · Customer") })
    }

    @Test
    fun labelReplacementRemovesACompetingLabelWhenTheRequestedLabelAlreadyExists(): Unit {
        val fixture = fixture(duplicateCheckingLabels = true)
        val service = StagingWorkflowService(fixture.registry)
        service.stage(
            "simple",
            WebStageChangeRequest(
                sourceId = "simple",
                editType = "set-entity-label",
                resourceLabel = "Checking",
                label = "Checking Account",
            ),
            "alice",
        )

        val snapshot = service.preview("simple", "alice")

        assertEquals("READYFORREVIEW", snapshot.proposal?.status)
        assertEquals(listOf("simple"), snapshot.proposal?.targetSourceIds)
        assertTrue(snapshot.proposal?.diff.orEmpty().any { it.description.contains("Checking") })
        assertTrue(snapshot.proposal?.diff.orEmpty().none { it.description.contains("Added · Checking Account · label · Checking Account") })
    }

    @Test
    fun labelReplacementAppliesAndReloadsWithExactlyOnePreferredLabel(): Unit {
        val fixture = fixture(checkingLabel = "Checking")
        val service = StagingWorkflowService(fixture.registry)
        service.stage(
            "simple",
            WebStageChangeRequest(
                sourceId = "simple",
                editType = "set-entity-label",
                resourceLabel = "Checking",
                label = "Checking Account",
            ),
            "alice",
        )

        val preview = service.preview("simple", "alice")

        assertEquals("READYFORREVIEW", preview.proposal?.status)
        val descriptions = preview.proposal?.diff.orEmpty().map { it.description }
        assertTrue(
            descriptions.any { it.contains("Changed label") && it.contains("Checking Account") },
            descriptions.joinToString(),
        )

        service.approve("simple", "bob")
        assertEquals("APPLIED", service.apply("simple", "bob").status)

        val appliedSource = Files.readString(fixture.data)
        assertTrue(!appliedSource.contains("\"Checking\""))
        assertEquals(1, "\"Checking Account\"".toRegex().findAll(appliedSource).count())
    }

    @Test
    fun rejectionClearsStagingAndAResubmittedShapeCanStillBeApplied(): Unit {
        val fixture = fixture()
        val service = StagingWorkflowService(fixture.registry)
        val beforeData = fixture.data.readBytes()
        val beforeShapes = fixture.shapes.readBytes()
        service.stage("simple", createBorrowerShape(severity = "Warning"), "alice")

        val preview = service.preview("simple", "alice")
        assertEquals("READYFORREVIEW", preview.proposal?.status)
        assertEquals(listOf("shapes"), preview.proposal?.targetSourceIds)
        assertEquals(1, preview.proposal?.shaclImpact?.newFindings?.size)
        val rejected = service.reject("simple", "bob")
        assertTrue(rejected.entries.isEmpty())
        assertEquals("EMPTY", rejected.status)
        assertEquals(beforeData.toList(), fixture.data.readBytes().toList())
        assertEquals(beforeShapes.toList(), fixture.shapes.readBytes().toList())

        service.stage("simple", createBorrowerShape(severity = "Warning"), "alice")
        service.preview("simple", "alice")
        service.approve("simple", "bob")
        val applied = service.apply("simple", "bob")

        assertEquals("APPLIED", applied.status)
        assertEquals(beforeData.toList(), fixture.data.readBytes().toList())
        assertContains(Files.readString(fixture.shapes), "BorrowerShape")
        val report = validateShacl(fixture.root)
        assertEquals(ShaclValidationStatus.Completed, report.status)
        assertEquals(1, report.results.size)
    }

    @Test
    fun updatesRemovesAndDeletesAnExistingSupportedConstraint(): Unit {
        val fixture = fixture(existingShape = true)
        var service = StagingWorkflowService(fixture.registry)
        service.stage(
            "simple",
            WebStageChangeRequest(
                sourceId = "shapes",
                editType = "shacl-update-constraint",
                shapeLabel = "Customer shape",
                pathLabel = "owns account",
                constraintKind = "min-count",
                constraintValue = "2",
            ),
            "alice",
        )
        service.preview("simple", "alice")
        service.approve("simple", "bob")
        assertEquals("APPLIED", service.apply("simple", "bob").status)
        assertContains(Files.readString(fixture.shapes), "2")

        service = StagingWorkflowService(fixture.registry)
        service.stage(
            "simple",
            WebStageChangeRequest(
                sourceId = "shapes",
                editType = "shacl-remove-constraint",
                shapeLabel = "Customer shape",
                pathLabel = "owns account",
                constraintKind = "min-count",
            ),
            "alice",
        )
        service.preview("simple", "alice")
        service.approve("simple", "bob")
        assertEquals("APPLIED", service.apply("simple", "bob").status)
        assertTrue(!Files.readString(fixture.shapes).contains("minCount"))

        service = StagingWorkflowService(fixture.registry)
        service.stage(
            "simple",
            WebStageChangeRequest(
                sourceId = "shapes",
                editType = "shacl-delete-shape",
                shapeLabel = "Customer shape",
            ),
            "alice",
        )
        service.preview("simple", "alice")
        service.approve("simple", "bob")
        assertEquals("APPLIED", service.apply("simple", "bob").status)
        assertTrue(!Files.readString(fixture.shapes).contains("CustomerShape"))
    }

    @Test
    fun renamesAnExistingShapeThroughTheShapesWorkflow(): Unit {
        val fixture = fixture(existingShape = true)
        val service = StagingWorkflowService(fixture.registry)

        service.stage(
            "simple",
            WebStageChangeRequest(
                sourceId = "shapes",
                editType = "shacl-update-shape-label",
                shapeLabel = "Customer shape",
                label = "Customer account requirement",
            ),
            "alice",
        )

        val preview = service.preview("simple", "alice")
        assertEquals("READYFORREVIEW", preview.proposal?.status)
        assertEquals(listOf("shapes"), preview.proposal?.targetSourceIds)
        assertTrue(preview.proposal?.diff.orEmpty().any { it.description.contains("Customer account requirement") })

        service.approve("simple", "bob")
        assertEquals("APPLIED", service.apply("simple", "bob").status)
        assertContains(Files.readString(fixture.shapes), "Customer account requirement")
    }

    @Test
    fun combinedOntologyAndShaclApplicationRollsBackEverySourceOnVerificationFailure(): Unit {
        val fixture = fixture()
        val beforeData = fixture.data.readBytes()
        val beforeShapes = fixture.shapes.readBytes()
        val failing = StagingWorkflowService(
            projectRegistry = fixture.registry,
            additionalPostApplyVerification = { _, _ -> EntioResult.Failure("forced verification failure") },
        )
        stageCombined(failing)
        failing.preview("simple", "alice")
        failing.approve("simple", "bob")

        val rolledBack = failing.apply("simple", "bob")

        assertEquals("ROLLEDBACK", rolledBack.status)
        assertEquals(beforeData.toList(), fixture.data.readBytes().toList())
        assertEquals(beforeShapes.toList(), fixture.shapes.readBytes().toList())

        val succeeding = StagingWorkflowService(fixture.registry)
        stageCombined(succeeding)
        val preview = succeeding.preview("simple", "alice")
        assertEquals(listOf("shapes", "simple"), preview.proposal?.targetSourceIds)
        succeeding.approve("simple", "bob")
        assertEquals("APPLIED", succeeding.apply("simple", "bob").status)
        assertContains(Files.readString(fixture.data), "Account")
        assertContains(Files.readString(fixture.shapes), "BorrowerShape")
    }

    @Test
    fun rejectsIncorrectSourcesUnsupportedPathsAndStaleBaselines(): Unit {
        val fixture = fixture()
        val service = StagingWorkflowService(fixture.registry)
        val wrongRole = createBorrowerShape("Warning").copy(sourceId = "simple")
        assertEquals(
            "invalid-shacl-source-role",
            assertFailsWith<WebWorkflowFailure> { service.stage("simple", wrongRole, "alice") }.code,
        )
        assertEquals(
            "unknown-source",
            assertFailsWith<WebWorkflowFailure> {
                service.stage("simple", createBorrowerShape("Warning").copy(sourceId = "missing"), "alice")
            }.code,
        )
        assertEquals(
            "unknown-entity",
            assertFailsWith<WebWorkflowFailure> {
                service.stage("simple", createBorrowerShape("Warning").copy(pathIri = "_:complex", pathLabel = null), "alice")
            }.code,
        )

        service.stage("simple", createBorrowerShape("Warning"), "alice")
        service.preview("simple", "alice")
        Files.writeString(fixture.shapes, Files.readString(fixture.shapes) + "\n")
        assertEquals(
            "stale-proposal-baseline",
            assertFailsWith<WebWorkflowFailure> { service.approve("simple", "bob") }.code,
        )
    }

    private fun stageCombined(service: StagingWorkflowService): Unit {
        service.stage(
            "simple",
            WebStageChangeRequest(sourceId = "simple", editType = "create-class", label = "Account"),
            "alice",
        )
        service.stage("simple", createBorrowerShape("Warning"), "alice")
    }

    private fun createBorrowerShape(severity: String): WebStageChangeRequest = WebStageChangeRequest(
        sourceId = "shapes",
        editType = "shacl-create-property-shape",
        shapeLabel = "Borrower shape",
        targetClassLabel = "Customer",
        pathLabel = "owns account",
        constraintKind = "min-count",
        constraintValue = "1",
        severity = severity,
        validationMessage = "Each customer should own at least one account.",
    )

    private fun validateShacl(root: Path): com.entio.core.ShaclValidationReport {
        val project = when (val result = ProjectLoader().loadProject(root)) {
            is EntioResult.Failure -> error(result.message)
            is EntioResult.Success -> result.value
        }
        val graphs = when (val result = ShaclGraphLoader().load(project.ontologies)) {
            is EntioResult.Failure -> error(result.message)
            is EntioResult.Success -> result.value
        }
        return when (val result = ShaclValidationService().validate(graphs, ShaclValidationMode.AssertedOnly)) {
            is EntioResult.Failure -> error(result.message)
            is EntioResult.Success -> result.value
        }
    }

    private fun fixture(
        existingShape: Boolean = false,
        duplicateCheckingLabels: Boolean = false,
        checkingLabel: String? = null,
    ): Fixture {
        val allowed = Files.createTempDirectory("entio-shacl-workflow")
        val root = Files.createDirectory(allowed.resolve("simple"))
        val ontology = Files.createDirectories(root.resolve("ontology"))
        Files.writeString(
            root.resolve("entio.yaml"),
            """
            name: simple-ontology
            iriNamespace: https://example.com/entio/simple#
            ontologySources:
              - id: simple
                path: ontology/simple.ttl
                format: turtle
                roles: [ontology, data]
              - id: shapes
                path: ontology/shapes.ttl
                format: turtle
                roles: [shapes]
            """.trimIndent(),
        )
        val data = ontology.resolve("simple.ttl")
        Files.writeString(
            data,
            """
            @prefix ex: <https://example.com/entio/simple#> .
            @prefix owl: <http://www.w3.org/2002/07/owl#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            ex:Customer a owl:Class ; rdfs:label "Customer" .
            ${when {
                duplicateCheckingLabels -> "ex:Checking a owl:Class ; rdfs:label \"Checking\", \"Checking Account\" ."
                checkingLabel != null -> "ex:Checking a owl:Class ; rdfs:label \"$checkingLabel\" ."
                else -> ""
            }}
            ex:ownsAccount a owl:ObjectProperty ; rdfs:label "owns account" ; rdfs:domain ex:Customer .
            ex:Shrey a ex:Customer ; rdfs:label "Shrey" .
            """.trimIndent(),
        )
        val shapes = ontology.resolve("shapes.ttl")
        Files.writeString(
            shapes,
            if (existingShape) {
                """
                @prefix ex: <https://example.com/entio/simple#> .
                @prefix sh: <http://www.w3.org/ns/shacl#> .
                @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
                ex:CustomerShape a sh:NodeShape ; rdfs:label "Customer shape" ; sh:targetClass ex:Customer ;
                    sh:property [ sh:path ex:ownsAccount ; sh:minCount 1 ; sh:severity sh:Warning ] .
                """.trimIndent()
            } else {
                "@prefix ex: <https://example.com/entio/simple#> .\n@prefix sh: <http://www.w3.org/ns/shacl#> ."
            },
        )
        val registry = InMemoryProjectRegistry(setOf(allowed))
        registry.register("simple", "Simple ontology", root)
        return Fixture(root, data, shapes, registry)
    }

    private data class Fixture(
        val root: Path,
        val data: Path,
        val shapes: Path,
        val registry: InMemoryProjectRegistry,
    )
}
