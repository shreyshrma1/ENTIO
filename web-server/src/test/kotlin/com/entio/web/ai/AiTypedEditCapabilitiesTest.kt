package com.entio.web.ai

import com.entio.core.DeletionPlanStatus
import com.entio.core.StagedChangeOperation
import com.entio.web.StagingWorkflowService
import com.entio.web.contract.InMemoryProjectRegistry
import com.entio.web.contract.WebPermission
import com.entio.web.contract.WebStageChangeRequest
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.io.path.isRegularFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AiTypedEditCapabilitiesTest {
    private val now: Instant = Instant.parse("2026-07-17T12:00:00Z")

    @Test
    fun everyApprovedTypedEditUsesTheExistingPreparationPathWithoutSharedStagingMutation(): Unit {
        val fixture = fixture()
        val deletion = fixture.deletionRequestWithDependencies("Account")
        val requests = listOf(
            WebStageChangeRequest("simple", "create-class", label = "Receivable Account"),
            WebStageChangeRequest("simple", "set-entity-label", resourceLabel = "Customer", label = "Client"),
            WebStageChangeRequest("simple", "add-superclass", classLabel = "Customer", superclassLabel = "Account"),
            WebStageChangeRequest("simple", "remove-superclass", classLabel = "Customer", superclassLabel = "Account"),
            WebStageChangeRequest("simple", "create-object-property", label = "owns invoice"),
            WebStageChangeRequest("simple", "create-datatype-property", label = "account number"),
            WebStageChangeRequest("simple", "set-property-domain", propertyLabel = "owns account", domainClassLabel = "Customer"),
            WebStageChangeRequest("simple", "set-property-range", propertyLabel = "owns account", rangeLabel = "Account"),
            WebStageChangeRequest("simple", "create-individual", individualLabel = "Alex", classLabel = "Customer"),
            WebStageChangeRequest("simple", "assign-type", resourceLabel = "Shrey", typeLabel = "Customer"),
            WebStageChangeRequest(
                "simple",
                "add-object-property-assertion",
                subjectLabel = "Shrey",
                propertyLabel = "owns account",
                objectLabel = "Invoice 20874",
            ),
            WebStageChangeRequest(
                "simple",
                "add-datatype-property-assertion",
                subjectLabel = "Shrey",
                propertyLabel = "owns account",
                value = "A-100",
            ),
            WebStageChangeRequest("simple", "add-definition", targetLabel = "Account", value = "A record used to organize financial activity."),
            WebStageChangeRequest(
                "simple",
                "replace-definition",
                targetLabel = "Customer",
                existingValue = "A person or organization that holds one or more accounts.",
                value = "A party that holds one or more accounts.",
            ),
            WebStageChangeRequest(
                "simple",
                "remove-definition",
                targetLabel = "Invoice",
                value = "A commercial document issued by a seller to a buyer.",
            ),
            deletion,
            WebStageChangeRequest("shapes", "shacl-create-node-shape", shapeLabel = "Invoice Shape", targetClassLabel = "Invoice"),
            WebStageChangeRequest(
                "shapes",
                "shacl-create-property-shape",
                shapeLabel = "Invoice Account Shape",
                targetClassLabel = "Invoice",
                pathLabel = "owns account",
                constraintKind = "min-count",
                constraintValue = "1",
            ),
            WebStageChangeRequest(
                "shapes",
                "shacl-update-constraint",
                shapeLabel = "Minimum Accounts",
                pathLabel = "owns account",
                constraintKind = "min-count",
                constraintValue = "2",
            ),
            WebStageChangeRequest(
                "shapes",
                "shacl-remove-constraint",
                shapeLabel = "Minimum Accounts",
                pathLabel = "owns account",
                constraintKind = "min-count",
            ),
            WebStageChangeRequest("shapes", "shacl-delete-shape", shapeLabel = "Minimum Accounts"),
        )

        assertEquals(AiTypedEditCapabilityInventory.approvedEditTypes, requests.map(WebStageChangeRequest::editType).toSet())
        requests.forEach { request ->
            val capability = if (request.editType.startsWith("shacl-")) {
                AiTypedEditCapabilityAdapter.ADD_SHACL_CAPABILITY
            } else {
                AiTypedEditCapabilityAdapter.ADD_ONTOLOGY_CAPABILITY
            }
            val aiOperation = fixture.adapter.prepare(fixture.scope(), capability, request)
            val ordinaryPrepared = fixture.staging.preparePrivateDraft("simple", request.copy(aiGenerated = true))

            assertEquals(ordinaryPrepared.operation, aiOperation.preparedOperation, request.editType)
            assertEquals(ordinaryPrepared.normalizedValues.toSortedMap(), aiOperation.normalizedValues, request.editType)
            assertTrue(aiOperation.request.aiGenerated)
        }
        assertTrue(fixture.staging.snapshot("simple").entries.isEmpty())
    }

    @Test
    fun privateWorkspaceMutationsKeepDeterministicRevisionHistoryAndAttribution(): Unit {
        val fixture = fixture()
        val workspace = fixture.workspace()
        val scope = fixture.scope()
        workspace.create(scope, "draft-1")

        val first = workspace.add(
            scope,
            "draft-1",
            AiTypedEditCapabilityAdapter.ADD_ONTOLOGY_CAPABILITY,
            addArguments("Receivable Account", "Create the approved receivable concept."),
            runId = "run-1",
        )
        val firstId = first.items.single().id
        val second = workspace.add(
            scope,
            "draft-1",
            AiTypedEditCapabilityAdapter.ADD_ONTOLOGY_CAPABILITY,
            addArguments("Payable Account", "Create the approved payable concept.", listOf(firstId)),
            runId = "run-1",
        )
        val secondId = second.items.last().id
        val updated = workspace.update(
            scope,
            "draft-1",
            AiTypedEditCapabilityAdapter.UPDATE_ONTOLOGY_CAPABILITY,
            AiUpdateDraftItemArguments(
                itemId = secondId,
                sourceId = "simple",
                request = WebStageChangeRequest("simple", "create-class", label = "Ledger Account"),
                rationale = "Use the reviewed ledger terminology.",
                dependencyItemIds = listOf(firstId),
            ),
            runId = "run-2",
        )
        val reordered = workspace.reorder(
            scope,
            "draft-1",
            AiReorderDraftItemsArguments(listOf(secondId, firstId), "Put the dependent concept first for review."),
        )
        val undone = workspace.undo(scope, "draft-1", AiUndoDraftArguments("Restore the prior deterministic order."))
        val removed = workspace.remove(scope, "draft-1", AiRemoveDraftItemArguments(secondId, "Remove the dependent concept."))
        val cleared = workspace.clear(scope, "draft-1", AiClearDraftArguments("Clear the remaining private draft."))

        assertEquals(listOf("add", "add", "update"), updated.revisions.map(AiDraftRevision::action))
        assertEquals(listOf(secondId, firstId), reordered.items.map(AiDraftItem::id))
        assertEquals(listOf(firstId, secondId), undone.items.map(AiDraftItem::id))
        assertEquals(listOf(firstId), removed.items.map(AiDraftItem::id))
        assertEquals(AiDraftStatus.EMPTY, cleared.status)
        assertEquals(listOf("add", "add", "update", "reorder", "undo", "remove", "clear"), cleared.revisions.map(AiDraftRevision::action))
        assertEquals(4, cleared.revisions[4].undoneRevision)
        assertNotNull(updated.draftFingerprint)
        assertEquals("alice", first.items.single().attribution?.acceptingUserId)
        assertEquals("conversation-1", first.items.single().attribution?.conversationId)
        assertEquals("run-1", first.items.single().attribution?.runId)
        assertTrue(fixture.staging.snapshot("simple").entries.isEmpty())
    }

    @Test
    fun ownershipBaselineDuplicateConflictAndDependencyChecksFailWithoutMutation(): Unit {
        val fixture = fixture()
        val workspace = fixture.workspace()
        val scope = fixture.scope()
        workspace.create(scope, "draft-1")
        val first = workspace.add(
            scope,
            "draft-1",
            AiTypedEditCapabilityAdapter.ADD_ONTOLOGY_CAPABILITY,
            addArguments("Receivable Account", "Create the approved concept."),
        )

        assertDraftFailure("draft-scope-violation") { workspace.read(scope.copy(userId = "bob"), "draft-1") }
        assertDraftFailure("draft-scope-violation") { workspace.read(scope.copy(conversationId = "conversation-2"), "draft-1") }
        assertDraftFailure("duplicate-draft-edit") {
            workspace.add(
                scope,
                "draft-1",
                AiTypedEditCapabilityAdapter.ADD_ONTOLOGY_CAPABILITY,
                addArguments("Receivable Account", "Do not duplicate the concept."),
            )
        }
        assertEquals(first.revisions, workspace.read(scope, "draft-1").revisions)

        workspace.add(
            scope,
            "draft-1",
            AiTypedEditCapabilityAdapter.ADD_ONTOLOGY_CAPABILITY,
            AiAddDraftItemArguments(
                "simple",
                WebStageChangeRequest("simple", "set-property-domain", propertyLabel = "owns account", domainClassLabel = "Customer"),
                "Use Customer as the domain.",
            ),
        )
        assertDraftFailure("conflicting-draft-edit") {
            workspace.add(
                scope,
                "draft-1",
                AiTypedEditCapabilityAdapter.ADD_ONTOLOGY_CAPABILITY,
                AiAddDraftItemArguments(
                    "simple",
                    WebStageChangeRequest("simple", "set-property-domain", propertyLabel = "owns account", domainClassLabel = "Invoice"),
                    "Use Invoice as a conflicting domain.",
                ),
            )
        }
        assertDraftFailure("unknown-draft-dependency") {
            workspace.add(
                scope,
                "draft-1",
                AiTypedEditCapabilityAdapter.ADD_ONTOLOGY_CAPABILITY,
                addArguments("Unknown Dependency", "This dependency is invalid.", listOf("missing-item")),
            )
        }
        assertDraftFailure("stale-draft-baseline") {
            workspace.add(
                scope.copy(baselineFingerprint = "changed"),
                "draft-1",
                AiTypedEditCapabilityAdapter.ADD_ONTOLOGY_CAPABILITY,
                addArguments("Stale Concept", "This must not be added."),
            )
        }
        assertEquals(AiDraftStatus.STALE, workspace.read(scope, "draft-1").status)
        assertTrue(fixture.staging.snapshot("simple").entries.isEmpty())
    }

    @Test
    fun unsupportedOutOfScopeAndIncompleteDeletionRequestsNeverMutateTheDraft(): Unit {
        val fixture = fixture()
        val workspace = fixture.workspace()
        val scope = fixture.scope()
        workspace.create(scope, "draft-1")

        assertDraftFailure("unsupported-typed-edit") {
            workspace.add(
                scope,
                "draft-1",
                AiTypedEditCapabilityAdapter.ADD_SHACL_CAPABILITY,
                AiAddDraftItemArguments("shapes", WebStageChangeRequest("shapes", "raw-shacl", value = "raw graph"), "Unsupported SHACL."),
            )
        }
        assertDraftFailure("unsupported-typed-edit") {
            workspace.add(
                scope,
                "draft-1",
                AiTypedEditCapabilityAdapter.ADD_ONTOLOGY_CAPABILITY,
                AiAddDraftItemArguments("fibo", WebStageChangeRequest("fibo", "reuse-class", targetLabel = "Loan"), "Immutable external source."),
            )
        }
        assertDraftFailure("source-scope-violation") {
            workspace.add(
                scope.copy(allowedSourceIds = listOf("simple")),
                "draft-1",
                AiTypedEditCapabilityAdapter.ADD_SHACL_CAPABILITY,
                AiAddDraftItemArguments(
                    "shapes",
                    WebStageChangeRequest("shapes", "shacl-create-node-shape", shapeLabel = "Invoice Shape", targetClassLabel = "Invoice"),
                    "Out of scope source.",
                ),
            )
        }
        assertDraftFailure("deletion-dependencies-required") {
            workspace.add(
                scope,
                "draft-1",
                AiTypedEditCapabilityAdapter.ADD_ONTOLOGY_CAPABILITY,
                AiAddDraftItemArguments("simple", WebStageChangeRequest("simple", "delete", targetLabel = "Customer"), "Delete Customer."),
            )
        }

        assertTrue(workspace.read(scope, "draft-1").items.isEmpty())
        assertTrue(workspace.read(scope, "draft-1").revisions.isEmpty())
        assertTrue(fixture.staging.snapshot("simple").entries.isEmpty())
    }

    private fun addArguments(label: String, rationale: String, dependencies: List<String> = emptyList()): AiAddDraftItemArguments =
        AiAddDraftItemArguments(
            sourceId = "simple",
            request = WebStageChangeRequest("simple", "create-class", label = label),
            rationale = rationale,
            dependencyItemIds = dependencies,
        )

    private fun assertDraftFailure(code: String, block: () -> Unit): Unit {
        val failure = assertFailsWith<IllegalArgumentException>(block = block)
        val actual = when (failure) {
            is AiDraftFailure -> failure.code
            is AiStateAccessFailure -> failure.code
            else -> throw failure
        }
        assertEquals(code, actual)
    }

    private fun fixture(): Fixture {
        val source = listOf(Path.of("examples/simple-ontology"), Path.of("../examples/simple-ontology"))
            .first(Files::isDirectory)
            .toAbsolutePath()
            .normalize()
        val parent = Files.createTempDirectory("entio-ai-private-draft")
        val project = parent.resolve("simple-ontology")
        Files.walk(source).use { paths ->
            paths.forEach { current ->
                val target = project.resolve(source.relativize(current).toString())
                if (current.isRegularFile()) {
                    Files.createDirectories(target.parent)
                    Files.copy(current, target, StandardCopyOption.REPLACE_EXISTING)
                } else {
                    Files.createDirectories(target)
                }
            }
        }
        val registry = InMemoryProjectRegistry(setOf(parent))
        registry.register("simple", "Simple", project)
        val staging = StagingWorkflowService(registry)
        return Fixture(staging, AiTypedEditCapabilityAdapter(staging))
    }

    private inner class Fixture(
        val staging: StagingWorkflowService,
        val adapter: AiTypedEditCapabilityAdapter,
    ) {
        fun scope(): AiCapabilityScope = AiCapabilityScope(
            userId = "alice",
            projectId = "simple",
            conversationId = "conversation-1",
            allowedSourceIds = listOf("simple", "shapes"),
            baselineFingerprint = "baseline-1",
            role = "CONTRIBUTOR",
            permissions = setOf(WebPermission.USE_AI.name, WebPermission.PREPARE_EDIT.name),
            availableFeatures = setOf(AiCapabilityFeatures.PRIVATE_DRAFT),
            createdAt = now,
        )

        fun workspace(): AiPrivateDraftWorkspace {
            var nextId = 1
            return AiPrivateDraftWorkspace(
                store = InMemoryAiDraftStore(),
                adapter = adapter,
                clock = Clock.fixed(now, ZoneOffset.UTC),
                itemIdFactory = { "item-${nextId++}" },
            )
        }

        fun deletionRequestWithDependencies(label: String): WebStageChangeRequest {
            val initial = staging.preparePrivateDraft(
                "simple",
                WebStageChangeRequest("simple", "delete", targetLabel = label, aiGenerated = true),
            )
            val plan = assertIs<StagedChangeOperation.Delete>(initial.operation).plan
            assertTrue(plan.status == DeletionPlanStatus.RequiresExplicitDependencies || plan.status == DeletionPlanStatus.Safe)
            return WebStageChangeRequest(
                "simple",
                "delete",
                targetLabel = label,
                dependencyKeys = plan.dependentStatements.map { it.identityKey }.toSet(),
            )
        }
    }
}
