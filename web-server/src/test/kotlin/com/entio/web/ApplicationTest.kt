package com.entio.web

import com.entio.web.contract.InMemoryProjectRegistry
import com.entio.web.contract.WebApplicationDependencies
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.websocket.Frame
import io.ktor.websocket.send
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApplicationTest {
    @Test
    fun healthEndpointReportsThatTheServerIsAlive(): Unit = testApplication {
        application { module() }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ok", response.bodyAsText())
    }

    @Test
    fun readinessEndpointReportsThatTheBoundaryIsReady(): Unit = testApplication {
        application { module() }

        val response = client.get("/ready")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ready", response.bodyAsText())
    }

    @Test
    fun sessionEndpointReturnsDeterministicDevelopmentIdentity(): Unit = testApplication {
        application { module() }

        val response = client.get("/api/v1/session")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(body, "Alice Contributor")
        assertContains(body, "CONTRIBUTOR")
    }

    @Test
    fun projectRoutesExposeRegisteredDescriptorsWithoutFilesystemRoots(): Unit = testApplication {
        val allowedRoot = Files.createTempDirectory("entio-web-route-allowlist")
        val projectRoot = Files.createDirectory(allowedRoot.resolve("simple"))
        val registry = InMemoryProjectRegistry(setOf(allowedRoot))
        registry.register("simple", "Simple ontology", projectRoot)

        application {
            module(WebApplicationDependencies(projectRegistry = registry))
        }

        val listResponse = client.get("/api/v1/projects")
        val listBody = listResponse.bodyAsText()
        val missingResponse = client.get("/api/v1/projects/missing")

        assertEquals(HttpStatusCode.OK, listResponse.status)
        assertContains(listBody, "Simple ontology")
        assertFalse(listBody.contains(projectRoot.toString()))
        assertEquals(HttpStatusCode.NotFound, missingResponse.status)
        assertContains(missingResponse.bodyAsText(), "unknown-project")
    }

    @Test
    fun readOnlyRoutesExposeSummaryHierarchySearchAndEntityRelationships(): Unit = testApplication {
        val allowedRoot = Files.createTempDirectory("entio-web-read-only")
        val projectRoot = createReadOnlyFixture(allowedRoot)
        val registry = InMemoryProjectRegistry(setOf(allowedRoot))
        registry.register("simple", "Simple ontology", projectRoot)

        application {
            module(WebApplicationDependencies(projectRegistry = registry))
        }

        val summary = client.get("/api/v1/projects/simple/summary")
        val hierarchy = client.get("/api/v1/projects/simple/hierarchy?limit=1")
        val outline = client.get("/api/v1/projects/simple/outline?sourceId=simple&limit=20")
        val search = client.get("/api/v1/projects/simple/search?q=customer")
        val detail = client.get(
            "/api/v1/projects/simple/entities?iri=" +
                "https%3A%2F%2Fexample.com%2Fentio%2Fsimple%23Shrey",
        )
        val customerDetail = client.get(
            "/api/v1/projects/simple/entities?iri=" +
                "https%3A%2F%2Fexample.com%2Fentio%2Fsimple%23Customer",
        )

        assertEquals(HttpStatusCode.OK, summary.status)
        assertContains(summary.bodyAsText(), "graphTripleCount")
        assertContains(summary.bodyAsText(), "ontology/simple.ttl")
        assertEquals(HttpStatusCode.OK, hierarchy.status)
        assertContains(hierarchy.bodyAsText(), "Invoice")
        assertContains(hierarchy.bodyAsText(), "nextOffset")
        assertEquals(HttpStatusCode.OK, outline.status)
        val outlineBody = outline.bodyAsText()
        assertContains(outlineBody, "received invoice")
        assertContains(outlineBody, "ObjectProperty")
        assertContains(outlineBody, "Shrey")
        assertContains(outlineBody, "Individual")
        assertContains(
            outlineBody,
            "\"directType\":{\"iri\":\"https://example.com/entio/simple#Customer\",\"label\":\"Customer\"",
        )
        assertContains(
            outlineBody,
            "\"directType\":{\"iri\":\"https://example.com/entio/simple#Invoice\",\"label\":\"Invoice\"",
        )
        assertEquals(HttpStatusCode.OK, search.status)
        assertContains(search.bodyAsText(), "Customer")
        assertContains(search.bodyAsText(), "PreferredLabel")
        assertContains(search.bodyAsText(), "Shrey")
        assertContains(search.bodyAsText(), "AssertedType")
        assertEquals(HttpStatusCode.OK, detail.status)
        assertContains(detail.bodyAsText(), "Shrey")
        assertContains(detail.bodyAsText(), "received invoice")
        assertContains(detail.bodyAsText(), "Invoice 20874")
        assertEquals(HttpStatusCode.OK, customerDetail.status)
        assertContains(customerDetail.bodyAsText(), "\"direction\":\"incoming\"")
        assertContains(customerDetail.bodyAsText(), "\"value\":\"https://example.com/entio/simple#receivedInvoice\"")
        assertContains(customerDetail.bodyAsText(), "\"label\":\"received invoice\"")
        assertContains(customerDetail.bodyAsText(), "\"entityKind\":\"ObjectProperty\"")
    }

    @Test
    fun shaclShapeRouteExposesReadableTargetsPathsAndConstraints(): Unit = testApplication {
        val allowedRoot = Files.createTempDirectory("entio-web-shacl-shapes")
        val projectRoot = createReadOnlyFixture(allowedRoot)
        Files.writeString(
            projectRoot.resolve("entio.yaml"),
            Files.readString(projectRoot.resolve("entio.yaml")) +
                "\n  - id: shapes\n" +
                "    path: ontology/shapes.ttl\n" +
                "    format: turtle\n" +
                "    roles:\n" +
                "      - shapes\n",
        )
        Files.writeString(
            projectRoot.resolve("ontology/shapes.ttl"),
            """
            @prefix ex: <https://example.com/entio/simple#> .
            @prefix sh: <http://www.w3.org/ns/shacl#> .

            ex:CustomerShape
                a sh:NodeShape ;
                sh:targetClass ex:Customer ;
                sh:property ex:CustomerInvoiceShape .

            ex:CustomerInvoiceShape
                sh:path ex:receivedInvoice ;
                sh:minCount 1 ;
                sh:message "Each customer needs an invoice." .
            """.trimIndent(),
        )
        val registry = InMemoryProjectRegistry(setOf(allowedRoot))
        registry.register("simple", "Simple ontology", projectRoot)

        application { module(WebApplicationDependencies(projectRegistry = registry)) }

        val response = client.get("/api/v1/projects/simple/shacl/shapes")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status, body)
        assertContains(body, "Customer Shape")
        assertContains(body, "Customer")
        assertContains(body, "received invoice")
        assertContains(body, "MinCount")
        assertContains(body, "Each customer needs an invoice.")
    }

    @Test
    fun readOnlyRoutesReturnStructuredErrorsForMissingEntitiesAndInvalidQueries(): Unit = testApplication {
        val allowedRoot = Files.createTempDirectory("entio-web-read-only-errors")
        val projectRoot = createReadOnlyFixture(allowedRoot)
        val registry = InMemoryProjectRegistry(setOf(allowedRoot))
        registry.register("simple", "Simple ontology", projectRoot)

        application {
            module(WebApplicationDependencies(projectRegistry = registry))
        }

        val missing = client.get("/api/v1/projects/simple/entities?iri=https%3A%2F%2Fexample.com%2Fmissing")
        val invalidSearch = client.get("/api/v1/projects/simple/search?q=")

        assertEquals(HttpStatusCode.NotFound, missing.status)
        assertContains(missing.bodyAsText(), "missing-entity")
        assertEquals(HttpStatusCode.BadRequest, invalidSearch.status)
        assertContains(invalidSearch.bodyAsText(), "invalid-search-query")
    }

    @Test
    fun fiboRoutesExposePagedModulesSearchAndLabelledDetailsWithoutMutatingAssets(): Unit = testApplication {
        val allowedRoot = Files.createTempDirectory("entio-web-fibo")
        val projectRoot = createReadOnlyFixture(allowedRoot)
        val registry = InMemoryProjectRegistry(setOf(allowedRoot))
        registry.register("simple", "Simple ontology", projectRoot)

        application { module(WebApplicationDependencies(projectRegistry = registry)) }

        val modules = client.get("/api/v1/projects/simple/external/fibo/modules?curated=true&limit=2")
        val moduleBody = modules.bodyAsText()
        assertEquals(HttpStatusCode.OK, modules.status)
        assertContains(moduleBody, "sourceId")
        assertContains(moduleBody, "ontologyIri")
        assertContains(moduleBody, "nextOffset")

        val elements = client.get(
            "/api/v1/projects/simple/external/fibo/module-elements?moduleIri=" +
                encoded("https://spec.edmcouncil.org/fibo/ontology/FND/Agreements/Agreements/") +
                "&limit=2",
        )
        val elementBody = elements.bodyAsText()
        assertEquals(HttpStatusCode.OK, elements.status)
        assertContains(elementBody, "iri")
        assertContains(elementBody, "label")
        assertContains(elementBody, "definitions")

        val search = client.get("/api/v1/projects/simple/external/fibo/search?q=agreement&curated=true&limit=2")
        assertEquals(HttpStatusCode.OK, search.status)
        assertContains(search.bodyAsText(), "agreement")
        assertContains(search.bodyAsText(), "page")

        val details = client.get(
            "/api/v1/projects/simple/external/fibo/details?iri=" +
                encoded("https://www.omg.org/spec/Commons/ContextualIdentifiers/ContextualIdentifier"),
        )
        val detailBody = details.bodyAsText()
        assertEquals(HttpStatusCode.OK, details.status)
        assertContains(detailBody, "contextual identifier")
        assertContains(detailBody, "sequence of characters uniquely identifying")
        assertContains(detailBody, "https://www.omg.org/spec/Commons/ContextualIdentifiers/ContextualIdentifier")
        assertContains(detailBody, "dependencies")

        val asset = listOf(
            Path.of("external-ontologies/fibo/indexes/catalog-v1.jsonl"),
            Path.of("..", "external-ontologies/fibo/indexes/catalog-v1.jsonl"),
        ).firstOrNull(Files::isRegularFile)
        assertTrue(asset != null)
    }

    @Test
    fun deletionDependenciesAreReviewableBeforeDeletionIsStaged(): Unit = testApplication {
        val allowedRoot = Files.createTempDirectory("entio-web-deletion")
        val projectRoot = createReadOnlyFixture(allowedRoot)
        val registry = InMemoryProjectRegistry(setOf(allowedRoot))
        registry.register("simple", "Simple ontology", projectRoot)

        application { module(WebApplicationDependencies(projectRegistry = registry)) }

        val dependencies = client.post("/api/v1/projects/simple/deletion-dependencies") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"sourceId":"simple","targetIri":"https://example.com/entio/simple#receivedInvoice","targetLabel":"received invoice"}""",
            )
        }
        val dependencyBody = dependencies.bodyAsText()
        assertEquals(HttpStatusCode.OK, dependencies.status)
        assertContains(dependencyBody, "RequiresExplicitDependencies")
        assertContains(dependencyBody, "Shrey")
        assertContains(dependencyBody, "received invoice")
        assertContains(dependencyBody, "Invoice 20874")

        val dependencyKey = com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(dependencyBody)
            .path("dependentStatements")
            .first()
            .path("key")
            .asText()
        val stage = client.post("/api/v1/projects/simple/staged") {
            contentType(ContentType.Application.Json)
            setBody(
                com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                    mapOf(
                        "sourceId" to "simple",
                        "editType" to "delete",
                        "targetIri" to "https://example.com/entio/simple#receivedInvoice",
                        "targetLabel" to "received invoice",
                        "dependencyKeys" to listOf(dependencyKey),
                    ),
                ),
            )
        }
        assertEquals(HttpStatusCode.OK, stage.status)
        assertContains(stage.bodyAsText(), "delete · received invoice")

        val preview = client.post("/api/v1/projects/simple/proposal/preview")
        assertEquals(HttpStatusCode.OK, preview.status)
        assertContains(preview.bodyAsText(), "READYFORREVIEW")
    }

    @Test
    fun stagingWorkflowKeepsDraftsPrivateUntilPreviewAndAppliesOnlyAfterReview(): Unit = testApplication {
        val allowedRoot = Files.createTempDirectory("entio-web-staging")
        val projectRoot = createReadOnlyFixture(allowedRoot)
        val registry = InMemoryProjectRegistry(setOf(allowedRoot))
        registry.register("simple", "Simple ontology", projectRoot)

        application { module(WebApplicationDependencies(projectRegistry = registry)) }

        val stage = client.post("/api/v1/projects/simple/staged") {
            contentType(ContentType.Application.Json)
            setBody("""
                {"sourceId":"simple","editType":"create-class","classIri":"https://example.com/entio/simple#Account","label":"Account","idempotencyKey":"stage-account"}
            """.trimIndent())
        }
        assertEquals(HttpStatusCode.OK, stage.status)
        assertContains(stage.bodyAsText(), "stage-1")
        assertContains(stage.bodyAsText(), "alice")

        val preview = client.post("/api/v1/projects/simple/proposal/preview")
        assertEquals(HttpStatusCode.OK, preview.status)
        assertContains(preview.bodyAsText(), "READYFORREVIEW")
        assertContains(preview.bodyAsText(), "Account")

        val contributorApproval = client.post("/api/v1/projects/simple/proposal/approve") {
            headers.append("X-Entio-User", "alice")
        }
        assertEquals(HttpStatusCode.Forbidden, contributorApproval.status)

        val approval = client.post("/api/v1/projects/simple/proposal/approve") {
            headers.append("X-Entio-User", "bob")
        }
        assertEquals(HttpStatusCode.OK, approval.status)
        assertContains(approval.bodyAsText(), "APPROVED")

        val applied = client.post("/api/v1/projects/simple/proposal/apply") {
            headers.append("X-Entio-User", "bob")
        }
        assertEquals(HttpStatusCode.OK, applied.status)
        assertContains(applied.bodyAsText(), "APPLIED")
        assertContains(Files.readString(projectRoot.resolve("ontology/simple.ttl")), "Account")
    }

    @Test
    fun stagingResolvesExistingLabelsAndGeneratesNewEntityIrisOnTheServer(): Unit = testApplication {
        val allowedRoot = Files.createTempDirectory("entio-web-label-staging")
        val projectRoot = createReadOnlyFixture(allowedRoot)
        val registry = InMemoryProjectRegistry(setOf(allowedRoot))
        registry.register("simple", "Simple ontology", projectRoot)

        application { module(WebApplicationDependencies(projectRegistry = registry)) }

        val generated = client.post("/api/v1/projects/simple/staged") {
            contentType(ContentType.Application.Json)
            setBody("""{"sourceId":"simple","editType":"create-class","label":"Account"}""")
        }
        assertEquals(HttpStatusCode.OK, generated.status)
        assertContains(generated.bodyAsText(), "https://example.com/entio/simple#Account")

        val resolved = client.post("/api/v1/projects/simple/staged") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "sourceId":"simple",
                  "editType":"add-object-property-assertion",
                  "subjectLabel":"Shrey",
                  "propertyLabel":"received invoice",
                  "objectLabel":"Invoice 20874"
                }
                """.trimIndent(),
            )
        }
        val body = resolved.bodyAsText()
        assertEquals(HttpStatusCode.OK, resolved.status)
        assertContains(body, "https://example.com/entio/simple#Shrey")
        assertContains(body, "https://example.com/entio/simple#receivedInvoice")
        assertContains(body, "https://example.com/entio/simple#Invoice20874")
    }

    @Test
    fun stagingAcceptsAnExistingRdfPropertyDomain(): Unit = testApplication {
        val allowedRoot = Files.createTempDirectory("entio-web-property-domain")
        val projectRoot = createReadOnlyFixture(allowedRoot)
        Files.writeString(
            projectRoot.resolve("ontology/simple.ttl"),
            Files.readString(projectRoot.resolve("ontology/simple.ttl")) +
                """

                <https://example.com/entio/simple#Account>
                    a <http://www.w3.org/2000/01/rdf-schema#Class> ;
                    <http://www.w3.org/2000/01/rdf-schema#label> "Account" .
                <https://example.com/entio/simple#ownsAccount>
                    a <http://www.w3.org/1999/02/22-rdf-syntax-ns#Property> ;
                    <http://www.w3.org/2000/01/rdf-schema#label> "owns account" .
                """.trimIndent(),
        )
        val registry = InMemoryProjectRegistry(setOf(allowedRoot))
        registry.register("simple", "Simple ontology", projectRoot)

        application { module(WebApplicationDependencies(projectRegistry = registry)) }

        val staged = client.post("/api/v1/projects/simple/staged") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "sourceId":"simple",
                  "editType":"set-property-domain",
                  "propertyIri":"https://example.com/entio/simple#ownsAccount",
                  "propertyLabel":"owns account",
                  "domainClassIri":"https://example.com/entio/simple#Account",
                  "domainClassLabel":"Account"
                }
                """.trimIndent(),
            )
        }

        val body = staged.bodyAsText()
        assertEquals(HttpStatusCode.OK, staged.status, body)
        assertContains(body, "\"editType\":\"set-property-domain\"")
        assertContains(body, "https://example.com/entio/simple#ownsAccount")
        assertContains(body, "https://example.com/entio/simple#Account")

        val range = client.post("/api/v1/projects/simple/staged") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "sourceId":"simple",
                  "editType":"set-property-range",
                  "propertyIri":"https://example.com/entio/simple#ownsAccount",
                  "propertyLabel":"owns account",
                  "rangeIri":"https://example.com/entio/simple#Customer",
                  "rangeLabel":"Customer"
                }
                """.trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.OK, range.status, range.bodyAsText())

        val preview = client.post("/api/v1/projects/simple/proposal/preview")
        val previewBody = preview.bodyAsText()
        assertEquals(HttpStatusCode.OK, preview.status, previewBody)
        assertContains(previewBody, "READYFORREVIEW")
        assertFalse(previewBody.contains("missing-property"))
    }

    @Test
    fun stagingPreservesTheRequestedDatatypeForLiteralAssertions(): Unit = testApplication {
        val allowedRoot = Files.createTempDirectory("entio-web-typed-literal")
        val projectRoot = createReadOnlyFixture(allowedRoot)
        Files.writeString(
            projectRoot.resolve("ontology/simple.ttl"),
            Files.readString(projectRoot.resolve("ontology/simple.ttl")) +
                """

                <https://example.com/entio/simple#balance>
                    a <http://www.w3.org/2002/07/owl#DatatypeProperty> ;
                    <http://www.w3.org/2000/01/rdf-schema#label> "balance" .
                """.trimIndent(),
        )
        val registry = InMemoryProjectRegistry(setOf(allowedRoot))
        registry.register("simple", "Simple ontology", projectRoot)
        application { module(WebApplicationDependencies(projectRegistry = registry)) }

        val staged = client.post("/api/v1/projects/simple/staged") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "sourceId":"simple",
                  "editType":"add-datatype-property-assertion",
                  "subjectIri":"https://example.com/entio/simple#Shrey",
                  "subjectLabel":"Shrey",
                  "propertyIri":"https://example.com/entio/simple#balance",
                  "propertyLabel":"balance",
                  "value":"42",
                  "datatypeIri":"http://www.w3.org/2001/XMLSchema#integer"
                }
                """.trimIndent(),
            )
        }
        val stagedBody = staged.bodyAsText()
        assertEquals(HttpStatusCode.OK, staged.status, stagedBody)
        assertContains(stagedBody, "http://www.w3.org/2001/XMLSchema#integer")

        val preview = client.post("/api/v1/projects/simple/proposal/preview")
        val previewBody = preview.bodyAsText()
        assertEquals(HttpStatusCode.OK, preview.status, previewBody)
        assertContains(previewBody, "http://www.w3.org/2001/XMLSchema#integer")
    }

    @Test
    fun synchronizedFieldEditsReplaceAndRemoveTheSameStagedEntry(): Unit = testApplication {
        val allowedRoot = Files.createTempDirectory("entio-web-synchronized-fields")
        val projectRoot = createReadOnlyFixture(allowedRoot)
        val registry = InMemoryProjectRegistry(setOf(allowedRoot))
        registry.register("simple", "Simple ontology", projectRoot)

        application { module(WebApplicationDependencies(projectRegistry = registry)) }

        val initial = client.post("/api/v1/projects/simple/staged") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "sourceId":"simple",
                  "editType":"add-definition",
                  "targetIri":"https://example.com/entio/simple#Customer",
                  "targetLabel":"Customer",
                  "value":"An initial definition."
                }
                """.trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.OK, initial.status)
        assertContains(initial.bodyAsText(), "stage-1")
        assertContains(initial.bodyAsText(), "An initial definition.")

        val replacement = client.post("/api/v1/projects/simple/staged") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "sourceId":"simple",
                  "editType":"add-definition",
                  "targetIri":"https://example.com/entio/simple#Customer",
                  "targetLabel":"Customer",
                  "value":"The edited definition.",
                  "replacesStagedId":"stage-1"
                }
                """.trimIndent(),
            )
        }
        val replacementBody = replacement.bodyAsText()
        assertEquals(HttpStatusCode.OK, replacement.status)
        assertEquals(1, Regex("\\\"id\\\":\\\"stage-1\\\"").findAll(replacementBody).count())
        assertContains(replacementBody, "The edited definition.")
        assertFalse(replacementBody.contains("An initial definition."))

        val removed = client.delete("/api/v1/projects/simple/staged/stage-1")
        assertEquals(HttpStatusCode.OK, removed.status)
        assertContains(removed.bodyAsText(), "\"entries\":[]")
    }

    @Test
    fun rejectingAProposalLeavesItsStagedEntriesForCorrection(): Unit = testApplication {
        val allowedRoot = Files.createTempDirectory("entio-web-reject")
        val projectRoot = createReadOnlyFixture(allowedRoot)
        val registry = InMemoryProjectRegistry(setOf(allowedRoot))
        registry.register("simple", "Simple ontology", projectRoot)

        application { module(WebApplicationDependencies(projectRegistry = registry)) }

        client.post("/api/v1/projects/simple/staged") {
            contentType(ContentType.Application.Json)
            setBody("""{"sourceId":"simple","editType":"create-class","classIri":"https://example.com/entio/simple#Account","label":"Account"}""")
        }
        assertEquals(HttpStatusCode.OK, client.post("/api/v1/projects/simple/proposal/preview").status)
        val rejected = client.post("/api/v1/projects/simple/proposal/reject") {
            headers.append("X-Entio-User", "bob")
        }
        assertEquals(HttpStatusCode.OK, rejected.status)
        assertContains(rejected.bodyAsText(), "stage-1")
        assertContains(rejected.bodyAsText(), "READY")
        assertFalse(Files.readString(projectRoot.resolve("ontology/simple.ttl")).contains("Account"))
    }

    @Test
    fun creatingAnIndividualRequiresAnInitialClass(): Unit = testApplication {
        val allowedRoot = Files.createTempDirectory("entio-web-individual-class")
        val projectRoot = createReadOnlyFixture(allowedRoot)
        val registry = InMemoryProjectRegistry(setOf(allowedRoot))
        registry.register("simple", "Simple ontology", projectRoot)

        application { module(WebApplicationDependencies(projectRegistry = registry)) }

        val response = client.post("/api/v1/projects/simple/staged") {
            contentType(ContentType.Application.Json)
            setBody("""{"sourceId":"simple","editType":"create-individual","label":"Account 101"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertContains(response.bodyAsText(), "A class label is required.")
        assertContains(response.bodyAsText(), "missing-field")
    }

    @Test
    fun proposalRejectsSuperclassAssignmentAfterItsStagedSuperclassIsRemoved(): Unit = testApplication {
        val allowedRoot = Files.createTempDirectory("entio-web-staged-superclass")
        val projectRoot = createReadOnlyFixture(allowedRoot)
        val registry = InMemoryProjectRegistry(setOf(allowedRoot))
        registry.register("simple", "Simple ontology", projectRoot)

        application { module(WebApplicationDependencies(projectRegistry = registry)) }

        client.post("/api/v1/projects/simple/staged") {
            contentType(ContentType.Application.Json)
            setBody("""{"sourceId":"simple","editType":"create-class","classIri":"https://example.com/entio/simple#FinancialProduct","label":"Financial Product"}""")
        }
        client.post("/api/v1/projects/simple/staged") {
            contentType(ContentType.Application.Json)
            setBody("""{"sourceId":"simple","editType":"create-class","classIri":"https://example.com/entio/simple#CheckingAccount","label":"Checking Account"}""")
        }
        client.post("/api/v1/projects/simple/staged") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "sourceId":"simple",
                  "editType":"add-superclass",
                  "classIri":"https://example.com/entio/simple#CheckingAccount",
                  "classLabel":"Checking Account",
                  "superclassIri":"https://example.com/entio/simple#FinancialProduct",
                  "superclassLabel":"Financial Product"
                }
                """.trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.OK, client.delete("/api/v1/projects/simple/staged/stage-1").status)
        val preview = client.post("/api/v1/projects/simple/proposal/preview")
        val body = preview.bodyAsText()

        assertEquals(HttpStatusCode.OK, preview.status)
        assertContains(body, "VERIFICATIONFAILED")
        assertContains(body, "dangling-staged-superclass")
        assertContains(body, "Checking Account cannot use Financial Product as a superclass")
        assertContains(body, "REMOVE_SUPERCLASS_ASSIGNMENT")
        assertContains(body, "REMOVE_STAGED_SUBCLASS")
        assertContains(body, "stage-2")
        assertContains(body, "stage-3")
    }

    @Test
    fun collaborationClientsReceiveOrderedPresenceActivityAndMutationEvents(): Unit = testApplication {
        val allowedRoot = Files.createTempDirectory("entio-web-collaboration")
        val projectRoot = createReadOnlyFixture(allowedRoot)
        val registry = InMemoryProjectRegistry(setOf(allowedRoot))
        registry.register("simple", "Simple ontology", projectRoot)

        application { module(WebApplicationDependencies(projectRegistry = registry)) }
        val clientA = createClient { install(WebSockets) }
        val clientB = createClient { install(WebSockets) }

        clientA.webSocket("/api/v1/projects/simple/collaboration?userId=alice") {
            val snapshotA = nextEvent(incoming.receive())
            val joinedA = nextEvent(incoming.receive())
            val incomingA = incoming
            assertEquals("collaboration.snapshot", snapshotA["eventType"])
            assertEquals("presence.joined", joinedA["eventType"])
            assertEquals(2, joinedA["sequence"])

            clientB.webSocket("/api/v1/projects/simple/collaboration?userId=bob") {
                val snapshotB = nextEvent(incoming.receive())
                val joinedB = nextEvent(incoming.receive())
                assertEquals("collaboration.snapshot", snapshotB["eventType"])
                assertEquals("presence.joined", joinedB["eventType"])
                assertEquals(3, snapshotB["sequence"])
                assertEquals(4, joinedB["sequence"])

                val joinedForA = nextEvent(incomingA.receive())
                assertEquals("presence.joined", joinedForA["eventType"])

                send(Frame.Text("""{"type":"entity-opened","entityIri":"https://example.com/Customer"}"""))
                val activityB = nextEvent(incoming.receive())
                val activityA = nextEvent(incomingA.receive())
                assertEquals("entity.activity", activityB["eventType"])
                assertEquals("https://example.com/Customer", activityB["entityIri"])
                assertEquals(activityB["sequence"], activityA["sequence"])

                send(Frame.Text("""{"type":"stage-change"}"""))
                assertEquals("mutation.rejected", nextEvent(incoming.receive())["eventType"])
                assertEquals("mutation.rejected", nextEvent(incomingA.receive())["eventType"])

                val staged = client.post("/api/v1/projects/simple/staged") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"sourceId":"simple","editType":"create-class","label":"Account"}""")
                }
                assertEquals(HttpStatusCode.OK, staged.status, staged.bodyAsText())
                assertEquals("staged-change.updated", nextEvent(incoming.receive())["eventType"])
                assertEquals("staged-change.updated", nextEvent(incomingA.receive())["eventType"])
            }

            val leftA = nextEvent(incomingA.receive())
            assertEquals("presence.left", leftA["eventType"])
        }
    }

    @Test
    fun semanticJobsReturnImmediatelyAndExposeReasoningAndShaclLifecycle(): Unit = testApplication {
        val allowedRoot = Files.createTempDirectory("entio-web-semantic-jobs")
        val projectRoot = createReadOnlyFixture(allowedRoot)
        val registry = InMemoryProjectRegistry(setOf(allowedRoot))
        registry.register("simple", "Simple ontology", projectRoot)

        application { module(WebApplicationDependencies(projectRegistry = registry)) }

        val reasoning = client.post("/api/v1/projects/simple/semantic-jobs") {
            contentType(ContentType.Application.Json)
            setBody("""{"kind":"reasoning","scope":"applied"}""")
        }
        assertEquals(HttpStatusCode.OK, reasoning.status)
        val reasoningStart = reasoning.bodyAsText()
        assertContains(reasoningStart, "job-")
        assertContains(reasoningStart, "graphFingerprint")
        val reasoningJobId = Regex("\\\"id\\\":\\\"([^\\\"]+)\\\"").find(reasoningStart)?.groupValues?.get(1)
            ?: error("A reasoning job id was not returned.")
        val reasoningFinal = pollJob(client, reasoningJobId)
        assertTrue(
            reasoningFinal.lowercase().contains("completed") || reasoningFinal.lowercase().contains("incomplete"),
            reasoningFinal,
        )
        assertContains(reasoningFinal, "consistency")

        val shacl = client.post("/api/v1/projects/simple/semantic-jobs") {
            contentType(ContentType.Application.Json)
            setBody("""{"kind":"shacl","scope":"applied","mode":"asserted-only"}""")
        }
        assertEquals(HttpStatusCode.OK, shacl.status)
        val shaclStart = shacl.bodyAsText()
        assertContains(shaclStart, "graphFingerprint")
        val shaclJobId = Regex("\\\"id\\\":\\\"([^\\\"]+)\\\"").find(shaclStart)?.groupValues?.get(1)
            ?: error("A SHACL job id was not returned.")
        val shaclFinal = pollJob(client, shaclJobId)
        assertTrue(shaclFinal.lowercase().contains("completed") || shaclFinal.lowercase().contains("failed"), shaclFinal)
        assertContains(shaclFinal, "AssertedOnly")
    }

    @Test
    fun staleProposalApplicationReturnsAnExplicitConflictState(): Unit = testApplication {
        val allowedRoot = Files.createTempDirectory("entio-web-stale")
        val projectRoot = createReadOnlyFixture(allowedRoot)
        val registry = InMemoryProjectRegistry(setOf(allowedRoot))
        registry.register("simple", "Simple ontology", projectRoot)

        application { module(WebApplicationDependencies(projectRegistry = registry)) }
        client.post("/api/v1/projects/simple/staged") {
            contentType(ContentType.Application.Json)
            setBody("""{"sourceId":"simple","editType":"create-class","classIri":"https://example.com/entio/simple#Account","label":"Account"}""")
        }
        assertEquals(HttpStatusCode.OK, client.post("/api/v1/projects/simple/proposal/preview").status)
        assertEquals(HttpStatusCode.OK, client.post("/api/v1/projects/simple/proposal/approve") { headers.append("X-Entio-User", "bob") }.status)
        Files.writeString(projectRoot.resolve("ontology/simple.ttl"), Files.readString(projectRoot.resolve("ontology/simple.ttl")) + "\n")

        val applied = client.post("/api/v1/projects/simple/proposal/apply") { headers.append("X-Entio-User", "bob") }
        assertEquals(HttpStatusCode.OK, applied.status)
        assertContains(applied.bodyAsText(), "APPLYFAILED")
        assertContains(applied.bodyAsText(), "stale")
    }

    private fun createReadOnlyFixture(allowedRoot: Path): Path {
        val projectRoot = Files.createDirectory(allowedRoot.resolve("simple"))
        val ontologyDirectory = Files.createDirectories(projectRoot.resolve("ontology"))
        Files.writeString(
            projectRoot.resolve("entio.yaml"),
            """
            name: simple-ontology
            iriNamespace: https://example.com/entio/simple#
            ontologySources:
              - id: simple
                path: ontology/simple.ttl
                format: turtle
            """.trimIndent(),
        )
        Files.writeString(
            ontologyDirectory.resolve("simple.ttl"),
            """
            @prefix ex: <https://example.com/entio/simple#> .
            @prefix owl: <http://www.w3.org/2002/07/owl#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

            ex:Party a owl:Class ; rdfs:label "Party" .
            ex:Customer a owl:Class ; rdfs:label "Customer" ; rdfs:subClassOf ex:Party .
            ex:Invoice a owl:Class ; rdfs:label "Invoice" .
            ex:receivedInvoice a owl:ObjectProperty ; rdfs:label "received invoice" ;
                rdfs:domain ex:Customer ; rdfs:range ex:Invoice .
            ex:Shrey a ex:Customer ; rdfs:label "Shrey" ; ex:receivedInvoice ex:Invoice20874 .
            ex:Invoice20874 a ex:Invoice ; rdfs:label "Invoice 20874" .
            """.trimIndent(),
        )
        assertTrue(Files.isRegularFile(ontologyDirectory.resolve("simple.ttl")))
        return projectRoot
    }

    private suspend fun pollJob(client: HttpClient, jobId: String): String {
        repeat(30) {
            val response = client.get("/api/v1/projects/simple/semantic-jobs/$jobId")
            val body = response.bodyAsText()
            val status = Regex("\\\"status\\\":\\\"([^\\\"]+)\\\"").find(body)?.groupValues?.get(1).orEmpty()
            if (status.lowercase() in setOf("completed", "failed", "incomplete", "cancelled", "stale")) return body
            Thread.sleep(50)
        }
        return client.get("/api/v1/projects/simple/semantic-jobs/$jobId").bodyAsText()
    }

    private fun encoded(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8)

    private fun nextEvent(frame: Frame): Map<String, Any?> {
        require(frame is Frame.Text)
        val text = frame.data.decodeToString()
        return com.fasterxml.jackson.databind.ObjectMapper().readValue(text, Map::class.java) as Map<String, Any?>
    }
}
