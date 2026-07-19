package com.entio.web.ai

import com.entio.web.contract.DevelopmentAuthorization
import com.entio.web.contract.DevelopmentIdentityProvider
import com.entio.web.contract.InMemoryProjectRegistry
import com.entio.web.contract.WebPermission
import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AiCapabilityRegistryTest {
    private val mapper: ObjectMapper = ObjectMapper()
    private val now: Instant = Instant.parse("2026-07-17T12:00:00Z")
    private val registry: AiCapabilityRegistry = AiCapabilityRegistry()

    @Test
    fun strictSchemasDecodeValidTypedArguments(): Unit {
        val scope = scope()
        val snapshot = registry.snapshot(scope)
        val decoded = registry.decode(
            invocation(
                snapshot,
                "entio_search_local_entities",
                """{"query":"account","kinds":["CLASS","INDIVIDUAL"],"sourceId":"simple","limit":10}""",
            ),
            snapshot,
            scope,
        )

        val arguments = assertIs<AiLocalSearchArguments>(decoded.arguments)
        assertEquals("account", arguments.query)
        assertEquals(listOf(AiEntityKindFilter.CLASS, AiEntityKindFilter.INDIVIDUAL), arguments.kinds)
        assertEquals("simple", arguments.sourceId)
        assertEquals(10, arguments.limit)
        assertTrue(decoded.definition.inputSchema.additionalProperties.not())
    }

    @Test
    fun strictSchemasRejectUnknownMissingMalformedAndOversizedValues(): Unit {
        assertDecodeFails("""{"entityIri":"https://example.com/Account","extra":true}""", "entio_entity_detail", "unknown-argument")
        assertDecodeFails("{}", "entio_entity_detail", "missing-argument")
        assertDecodeFails("""{"entityIri":"not-an-iri"}""", "entio_entity_detail", "invalid-iri")
        assertDecodeFails(
            """{"query":"account","kinds":["CLASS","OBJECT_PROPERTY","DATATYPE_PROPERTY","ANNOTATION_PROPERTY","INDIVIDUAL","SHAPE","CLASS"],"limit":10}""",
            "entio_search_local_entities",
            "array-limit",
        )
        assertDecodeFails("""{"query":"account","kinds":["UNKNOWN"],"limit":10}""", "entio_search_local_entities", "invalid-enum-value")
        assertDecodeFails("""{"query":"account","limit":"many"}""", "entio_search_local_entities", "malformed-argument")
        assertDecodeFails("[]", "entio_search_local_entities", "malformed-arguments")
    }

    @Test
    fun privateDraftSchemasExposeOnlyApprovedTypedEditsAndRejectRawFallbacks(): Unit {
        val privateScope = scope(
            features = scope().availableFeatures + AiCapabilityFeatures.PRIVATE_DRAFT,
            permissions = scope().permissions + WebPermission.PREPARE_EDIT.name,
        )
        val snapshot = registry.snapshot(privateScope)
        val decoded = registry.decode(
            invocation(
                snapshot,
                AiTypedEditCapabilityAdapter.ADD_SHACL_CAPABILITY,
                """{"sourceId":"shapes","editType":"shacl-create-node-shape","shapeLabel":"Customer shape","targetClassLabel":"Customer","rationale":"Require a reviewed customer shape."}""",
            ),
            snapshot,
            privateScope,
        )

        val arguments = assertIs<AiAddDraftItemArguments>(decoded.arguments)
        assertEquals("shacl-create-node-shape", arguments.request.editType)
        assertEquals("Customer", arguments.request.targetClassLabel)
        assertTrue(arguments.request.aiGenerated)
        assertTrue(decoded.definition.inputSchema.additionalProperties.not())

        assertFailureCode("invalid-enum-value") {
            registry.decode(
                invocation(
                    snapshot,
                    AiTypedEditCapabilityAdapter.ADD_SHACL_CAPABILITY,
                    """{"sourceId":"shapes","editType":"raw-shacl","value":"raw graph","rationale":"Bypass typed tools."}""",
                ),
                snapshot,
                privateScope,
            )
        }
        assertFailureCode("unknown-argument") {
            registry.decode(
                invocation(
                    snapshot,
                    AiTypedEditCapabilityAdapter.ADD_ONTOLOGY_CAPABILITY,
                    """{"sourceId":"simple","editType":"create-class","label":"Account","rationale":"Create it.","turtle":"raw"}""",
                ),
                snapshot,
                privateScope,
            )
        }
    }

    @Test
    fun scopeFactoryUsesRegisteredProjectIdentitySourcesPermissionsAndFeatures(): Unit {
        val projectRoot = Files.createTempDirectory("entio-ai-scope")
        val projects = InMemoryProjectRegistry(setOf(projectRoot.parent))
        projects.register("simple", "Simple", projectRoot)
        val factory = AiCapabilityScopeFactory(projects, DevelopmentIdentityProvider(), DevelopmentAuthorization())

        val scope = factory.create(
            AiCapabilityScopeRequest(
                userId = "alice",
                projectId = "simple",
                conversationId = "conversation-1",
                allowedSourceIds = setOf("simple"),
                availableSourceIds = setOf("simple", "shapes"),
                baselineFingerprint = "baseline",
                availableFeatures = setOf(AiCapabilityFeatures.LOCAL_SEMANTIC_READ),
                createdAt = now,
            ),
        )

        assertEquals("CONTRIBUTOR", scope.role)
        assertTrue(WebPermission.USE_AI.name in scope.permissions)
        assertEquals(listOf("simple"), scope.allowedSourceIds)
        assertFailsWith<AiCapabilityFailure> {
            factory.create(
                AiCapabilityScopeRequest(
                    userId = "alice",
                    projectId = "simple",
                    conversationId = "conversation-1",
                    allowedSourceIds = setOf("outside"),
                    availableSourceIds = setOf("simple"),
                    baselineFingerprint = "baseline",
                    availableFeatures = emptySet(),
                    createdAt = now,
                ),
            )
        }
    }

    @Test
    fun registryContainsOnlyCurrentlyAuthorizedCapabilities(): Unit {
        val all = registry.snapshot(scope()).definitions.map(AiCapabilityDefinition::name)
        val noHelp = registry.snapshot(scope(features = setOf(AiCapabilityFeatures.LOCAL_SEMANTIC_READ)))
            .definitions.map(AiCapabilityDefinition::name)
        val noPermission = registry.snapshot(scope(permissions = setOf(WebPermission.BROWSE.name)))
            .definitions.map(AiCapabilityDefinition::name)

        assertEquals(
            listOf(
                "entio_available_actions",
                "entio_compare_entities",
                "entio_entity_detail",
                "entio_entity_usage",
                "entio_error_help",
                "entio_fibo_entity",
                "entio_fibo_search",
                "entio_help",
                "entio_hierarchy_neighborhood",
                "entio_project_summary",
                "entio_proposal_summary",
                "entio_recent_activity",
                "entio_screen_context",
                "entio_search_local_entities",
                "entio_semantic_job",
                "entio_workflow_state",
            ),
            all,
        )
        assertFalse("entio_help" in noHelp)
        assertTrue(noPermission.isEmpty())
        assertTrue(all.none(::isForbiddenName))
    }

    @Test
    fun everyInvocationRevalidatesRegistryUserProjectConversationAndSourceScope(): Unit {
        val scope = scope()
        val snapshot = registry.snapshot(scope)
        val valid = invocation(snapshot, "entio_entity_detail", """{"entityIri":"https://example.com/Account","sourceId":"simple"}""")

        assertEquals("https://example.com/Account", assertIs<AiEntityDetailArguments>(registry.decode(valid, snapshot, scope).arguments).entityIri)
        assertFailureCode("user-scope-violation") { registry.decode(valid.copy(userId = "bob"), snapshot, scope) }
        assertFailureCode("project-scope-violation") { registry.decode(valid.copy(projectId = "other"), snapshot, scope) }
        assertFailureCode("conversation-scope-violation") { registry.decode(valid.copy(conversationId = "other"), snapshot, scope) }
        assertDecodeFails(
            """{"entityIri":"https://example.com/Account","sourceId":"outside"}""",
            "entio_entity_detail",
            "source-scope-violation",
        )
    }

    @Test
    fun staleRegistryCannotInvokeCapabilityRemovedByCurrentScope(): Unit {
        val originalScope = scope()
        val issued = registry.snapshot(originalScope)
        val invocation = invocation(issued, "entio_help", """{"topic":"NAVIGATION"}""")
        val changedScope = scope(features = setOf(AiCapabilityFeatures.LOCAL_SEMANTIC_READ))

        assertFailureCode("stale-capability-registry") {
            registry.decode(invocation, issued, changedScope)
        }
    }

    @Test
    fun forbiddenCapabilityNamesCannotBeRegistered(): Unit {
        val forbidden = defaultAiCapabilityDefinitions().first().copy(name = "apply_changes")

        assertFailsWith<IllegalArgumentException> { AiCapabilityRegistry(listOf(forbidden)) }
    }

    private fun assertDecodeFails(json: String, capabilityName: String, code: String) {
        val scope = scope()
        val snapshot = registry.snapshot(scope)
        assertFailureCode(code) {
            registry.decode(invocation(snapshot, capabilityName, json), snapshot, scope)
        }
    }

    private fun assertFailureCode(code: String, block: () -> Unit) {
        val failure = assertFailsWith<AiCapabilityFailure> { block() }
        assertEquals(code, failure.code)
    }

    private fun invocation(snapshot: AiCapabilityRegistrySnapshot, name: String, json: String): AiCapabilityInvocation =
        AiCapabilityInvocation(
            id = "invocation-1",
            capabilityName = name,
            registrySnapshotId = snapshot.id,
            userId = "alice",
            projectId = "simple",
            conversationId = "conversation-1",
            arguments = mapper.readTree(json),
        )

    private fun scope(
        features: Set<String> = setOf(
            AiCapabilityFeatures.LOCAL_SEMANTIC_READ,
            AiCapabilityFeatures.ENTIO_HELP,
            AiCapabilityFeatures.SEMANTIC_RESULTS,
            AiCapabilityFeatures.PROPOSAL_READ,
            AiCapabilityFeatures.ACTIVITY_READ,
            AiCapabilityFeatures.FIBO_READ,
        ),
        permissions: Set<String> = setOf(WebPermission.BROWSE.name, WebPermission.USE_AI.name),
    ): AiCapabilityScope = AiCapabilityScope(
        userId = "alice",
        projectId = "simple",
        conversationId = "conversation-1",
        allowedSourceIds = listOf("simple", "shapes"),
        baselineFingerprint = "baseline",
        role = "CONTRIBUTOR",
        permissions = permissions,
        availableFeatures = features,
        createdAt = now,
    )

    private fun isForbiddenName(name: String): Boolean = listOf(
        "file", "shell", "sparql", "turtle", "network", "secret", "credential", "config", "permission", "model",
        "approve", "reject", "apply", "rollback",
    ).any(name::contains)
}
