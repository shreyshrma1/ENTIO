package com.entio.web.ai

import com.entio.web.contract.DevelopmentAuthorization
import com.entio.web.contract.DevelopmentIdentityProvider
import com.entio.web.contract.ProjectRegistry
import com.entio.web.contract.WebPermission
import com.entio.web.contract.WebRole
import com.fasterxml.jackson.databind.JsonNode
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant

public data class AiCapabilityScopeRequest(
    val userId: String,
    val projectId: String,
    val conversationId: String,
    val allowedSourceIds: Set<String>,
    val availableSourceIds: Set<String>,
    val baselineFingerprint: String,
    val collaborationSessionId: String? = null,
    val availableFeatures: Set<String>,
    val createdAt: Instant,
)

public class AiCapabilityScopeFactory(
    private val projects: ProjectRegistry,
    private val identities: DevelopmentIdentityProvider,
    private val authorization: DevelopmentAuthorization,
) {
    public fun create(request: AiCapabilityScopeRequest): AiCapabilityScope {
        if (projects.find(request.projectId) == null) {
            throw AiCapabilityFailure("unknown-project", "The requested project is not registered.")
        }
        val user = identities.find(request.userId)
            ?: throw AiCapabilityFailure("unknown-user", "The requested user is not configured.")
        if (request.conversationId.isBlank()) throw AiCapabilityFailure("conversation-required", "A conversation is required.")
        if (request.baselineFingerprint.isBlank()) throw AiCapabilityFailure("baseline-required", "A baseline fingerprint is required.")
        if (!request.availableSourceIds.containsAll(request.allowedSourceIds)) {
            throw AiCapabilityFailure("source-scope-violation", "The requested source scope is not available in this project.")
        }
        if (request.allowedSourceIds.any { !isSourceId(it) }) {
            throw AiCapabilityFailure("invalid-source-id", "Source IDs must use the approved identifier format.")
        }
        return AiCapabilityScope(
            userId = user.id,
            projectId = request.projectId,
            conversationId = request.conversationId,
            allowedSourceIds = request.allowedSourceIds.sorted(),
            baselineFingerprint = request.baselineFingerprint,
            collaborationSessionId = request.collaborationSessionId,
            role = user.role.name,
            permissions = authorization.permissionsFor(user.role).map(WebPermission::name).toSet(),
            availableFeatures = request.availableFeatures.toSortedSet(),
            createdAt = request.createdAt,
        )
    }
}

public class AiCapabilityRegistry(
    definitions: List<AiCapabilityDefinition> = defaultAiCapabilityDefinitions(),
) {
    private val definitions: List<AiCapabilityDefinition> = definitions.sortedBy(AiCapabilityDefinition::name)

    init {
        require(this.definitions.map(AiCapabilityDefinition::name).distinct().size == this.definitions.size) {
            "duplicate-ai-capability-name"
        }
        val forbidden = this.definitions.map(AiCapabilityDefinition::name).filter(::isForbiddenCapabilityName)
        require(forbidden.isEmpty()) { "forbidden-ai-capability:${forbidden.joinToString()}" }
    }

    public fun snapshot(scope: AiCapabilityScope): AiCapabilityRegistrySnapshot {
        val allowed = definitions.filter { it.isAllowed(scope) }
        val material = buildList {
            add(scope.userId)
            add(scope.projectId)
            add(scope.conversationId)
            add(scope.baselineFingerprint)
            addAll(scope.allowedSourceIds.sorted())
            addAll(scope.permissions.sorted())
            addAll(scope.availableFeatures.sorted())
            addAll(allowed.map(AiCapabilityDefinition::name))
        }.joinToString("\u0000")
        return AiCapabilityRegistrySnapshot(sha256(material), allowed)
    }

    public fun decode(
        invocation: AiCapabilityInvocation,
        issuedSnapshot: AiCapabilityRegistrySnapshot,
        currentScope: AiCapabilityScope,
    ): AiDecodedCapabilityInvocation {
        validateInvocationScope(invocation, currentScope)
        val currentSnapshot = snapshot(currentScope)
        if (invocation.registrySnapshotId != issuedSnapshot.id || currentSnapshot.id != issuedSnapshot.id) {
            throw AiCapabilityFailure("stale-capability-registry", "The capability registry changed before invocation.")
        }
        val issued = issuedSnapshot.definitions.firstOrNull { it.name == invocation.capabilityName }
            ?: throw AiCapabilityFailure("unknown-capability", "The requested capability is not in the issued registry.")
        val current = currentSnapshot.definitions.firstOrNull { it.name == invocation.capabilityName }
            ?: throw AiCapabilityFailure("unauthorized-capability", "The requested capability is no longer allowed.")
        if (issued.operationType != current.operationType) {
            throw AiCapabilityFailure("stale-capability-registry", "The capability definition changed before invocation.")
        }
        val arguments = current.decoder.decode(invocation.arguments)
        validateSourceScope(current, arguments, currentScope)
        return AiDecodedCapabilityInvocation(invocation.id, current, arguments)
    }

    private fun validateInvocationScope(invocation: AiCapabilityInvocation, scope: AiCapabilityScope) {
        if (invocation.userId != scope.userId) throw AiCapabilityFailure("user-scope-violation", "The invocation belongs to another user.")
        if (invocation.projectId != scope.projectId) throw AiCapabilityFailure("project-scope-violation", "The invocation belongs to another project.")
        if (invocation.conversationId != scope.conversationId) {
            throw AiCapabilityFailure("conversation-scope-violation", "The invocation belongs to another conversation.")
        }
    }

    private fun validateSourceScope(
        definition: AiCapabilityDefinition,
        arguments: AiCapabilityArguments,
        scope: AiCapabilityScope,
    ) {
        val sourceId = (arguments as? AiSourceScopedArguments)?.sourceId
        if (definition.sourceScope == AiSourceScopeRule.REQUIRED_ALLOWED_SOURCE && sourceId == null) {
            throw AiCapabilityFailure("source-required", "This capability requires an allowed source.")
        }
        if (sourceId != null && sourceId !in scope.allowedSourceIds) {
            throw AiCapabilityFailure("source-scope-violation", "The requested source is outside the current run scope.")
        }
    }
}

private fun AiCapabilityDefinition.isAllowed(scope: AiCapabilityScope): Boolean {
    val roleAllowed = when (requiredRole) {
        AiRequiredRole.CONTRIBUTOR -> scope.role == WebRole.CONTRIBUTOR.name || scope.role == WebRole.REVIEWER.name
        AiRequiredRole.REVIEWER -> scope.role == WebRole.REVIEWER.name
    }
    return roleAllowed && requiredPermissions.all(scope.permissions::contains) && requiredFeature in scope.availableFeatures
}

public fun defaultAiCapabilityDefinitions(): List<AiCapabilityDefinition> = listOf(
    projectSummaryDefinition(),
    entityDetailDefinition(),
    localSearchDefinition(),
    helpDefinition(),
)

private fun projectSummaryDefinition(): AiCapabilityDefinition = AiCapabilityDefinition(
    name = "entio_project_summary",
    operationType = AiCapabilityOperationType.READ_PROJECT_SUMMARY,
    category = AiCapabilityCategory.LOCAL_READ,
    description = "Read the current registered Entio project's bounded summary.",
    inputSchema = objectSchema(emptyList(), emptySet()),
    access = AiCapabilityAccess.READ_ONLY,
    requiredRole = AiRequiredRole.CONTRIBUTOR,
    requiredPermissions = setOf(WebPermission.BROWSE.name, WebPermission.USE_AI.name),
    requiredFeature = AiCapabilityFeatures.LOCAL_SEMANTIC_READ,
    sourceScope = AiSourceScopeRule.NONE,
    resultLimit = 1,
    timeoutMillis = 5_000,
    auditClassification = AiCapabilityAuditClassification.PROJECT_READ,
    decoder = AiCapabilityArgumentDecoder { input ->
        StrictObject(input, emptySet(), emptySet())
        AiProjectSummaryArguments
    },
)

private fun entityDetailDefinition(): AiCapabilityDefinition {
    val fields = setOf("entityIri", "sourceId")
    return AiCapabilityDefinition(
        name = "entio_entity_detail",
        operationType = AiCapabilityOperationType.READ_ENTITY,
        category = AiCapabilityCategory.LOCAL_READ,
        description = "Read one local entity descriptor from the current project scope.",
        inputSchema = objectSchema(
            listOf(
                property("entityIri", AiStringSchema(maxLength = 2_048, format = AiStringFormat.HTTP_IRI), "Entity HTTP IRI."),
                property("sourceId", AiStringSchema(maxLength = 128, format = AiStringFormat.SOURCE_ID), "Optional allowed source ID.", nullable = true),
            ),
            setOf("entityIri"),
        ),
        access = AiCapabilityAccess.READ_ONLY,
        requiredRole = AiRequiredRole.CONTRIBUTOR,
        requiredPermissions = setOf(WebPermission.BROWSE.name, WebPermission.USE_AI.name),
        requiredFeature = AiCapabilityFeatures.LOCAL_SEMANTIC_READ,
        sourceScope = AiSourceScopeRule.OPTIONAL_ALLOWED_SOURCE,
        resultLimit = 1,
        timeoutMillis = 5_000,
        auditClassification = AiCapabilityAuditClassification.PROJECT_READ,
        decoder = AiCapabilityArgumentDecoder { input ->
            val objectInput = StrictObject(input, fields, setOf("entityIri"))
            AiEntityDetailArguments(objectInput.httpIri("entityIri"), objectInput.optionalSourceId("sourceId"))
        },
    )
}

private fun localSearchDefinition(): AiCapabilityDefinition {
    val fields = setOf("query", "kinds", "sourceId", "limit")
    return AiCapabilityDefinition(
        name = "entio_search_local_entities",
        operationType = AiCapabilityOperationType.SEARCH_LOCAL_ENTITIES,
        category = AiCapabilityCategory.LOCAL_READ,
        description = "Search bounded local entity labels and descriptions deterministically.",
        inputSchema = objectSchema(
            listOf(
                property("query", AiStringSchema(maxLength = 256), "Search text."),
                property(
                    "kinds",
                    AiArraySchema(
                        AiStringSchema(maxLength = 32, allowedValues = AiEntityKindFilter.entries.map(Enum<*>::name)),
                        maxItems = 6,
                    ),
                    "Optional unique entity-kind filters.",
                ),
                property("sourceId", AiStringSchema(maxLength = 128, format = AiStringFormat.SOURCE_ID), "Optional allowed source ID.", nullable = true),
                property("limit", AiIntegerSchema(1, 20), "Maximum result count."),
            ),
            setOf("query", "limit"),
        ),
        access = AiCapabilityAccess.READ_ONLY,
        requiredRole = AiRequiredRole.CONTRIBUTOR,
        requiredPermissions = setOf(WebPermission.BROWSE.name, WebPermission.USE_AI.name),
        requiredFeature = AiCapabilityFeatures.LOCAL_SEMANTIC_READ,
        sourceScope = AiSourceScopeRule.OPTIONAL_ALLOWED_SOURCE,
        resultLimit = 20,
        timeoutMillis = 5_000,
        auditClassification = AiCapabilityAuditClassification.PROJECT_READ,
        decoder = AiCapabilityArgumentDecoder { input ->
            val objectInput = StrictObject(input, fields, setOf("query", "limit"))
            AiLocalSearchArguments(
                query = objectInput.string("query", 1, 256),
                kinds = objectInput.enumArray("kinds", AiEntityKindFilter.entries.associateBy(Enum<*>::name), 6),
                sourceId = objectInput.optionalSourceId("sourceId"),
                limit = objectInput.integer("limit", 1, 20),
            )
        },
    )
}

private fun helpDefinition(): AiCapabilityDefinition {
    val fields = setOf("topic")
    return AiCapabilityDefinition(
        name = "entio_help",
        operationType = AiCapabilityOperationType.READ_ENTIO_HELP,
        category = AiCapabilityCategory.HELP,
        description = "Read versioned help for currently available Entio behavior.",
        inputSchema = objectSchema(
            listOf(property("topic", AiStringSchema(maxLength = 32, allowedValues = AiHelpTopic.entries.map(Enum<*>::name)), "Help topic.")),
            fields,
        ),
        access = AiCapabilityAccess.READ_ONLY,
        requiredRole = AiRequiredRole.CONTRIBUTOR,
        requiredPermissions = setOf(WebPermission.USE_AI.name),
        requiredFeature = AiCapabilityFeatures.ENTIO_HELP,
        sourceScope = AiSourceScopeRule.NONE,
        resultLimit = 1,
        timeoutMillis = 2_000,
        auditClassification = AiCapabilityAuditClassification.HELP_READ,
        decoder = AiCapabilityArgumentDecoder { input ->
            val objectInput = StrictObject(input, fields, fields)
            AiHelpArguments(objectInput.enum("topic", AiHelpTopic.entries.associateBy(Enum<*>::name)))
        },
    )
}

private class StrictObject(
    input: JsonNode,
    allowedFields: Set<String>,
    requiredFields: Set<String>,
) {
    private val value: JsonNode = input

    init {
        if (!input.isObject) throw AiCapabilityFailure("malformed-arguments", "Capability arguments must be a JSON object.")
        val present = input.fieldNames().asSequence().toSet()
        val unknown = present - allowedFields
        if (unknown.isNotEmpty()) throw AiCapabilityFailure("unknown-argument", "Unknown capability argument: ${unknown.sorted().joinToString()}.")
        val missing = requiredFields - present
        if (missing.isNotEmpty()) throw AiCapabilityFailure("missing-argument", "Missing capability argument: ${missing.sorted().joinToString()}.")
    }

    fun string(name: String, minLength: Int, maxLength: Int): String {
        val node = value.get(name)
        if (node == null || !node.isTextual) throw AiCapabilityFailure("malformed-argument", "$name must be a string.")
        val text = node.textValue()
        if (text.length !in minLength..maxLength) throw AiCapabilityFailure("argument-out-of-range", "$name has an invalid length.")
        return text
    }

    fun optionalSourceId(name: String): String? {
        val node = value.get(name) ?: return null
        if (node.isNull) return null
        val sourceId = string(name, 1, 128)
        if (!isSourceId(sourceId)) throw AiCapabilityFailure("invalid-source-id", "$name is not a valid source ID.")
        return sourceId
    }

    fun httpIri(name: String): String {
        val candidate = string(name, 1, 2_048)
        val parsed = runCatching { URI(candidate) }.getOrNull()
        if (parsed == null || !parsed.isAbsolute || parsed.scheme !in setOf("http", "https") || parsed.host.isNullOrBlank() || parsed.userInfo != null) {
            throw AiCapabilityFailure("invalid-iri", "$name must be an absolute HTTP or HTTPS IRI without user information.")
        }
        return candidate
    }

    fun integer(name: String, minimum: Int, maximum: Int): Int {
        val node = value.get(name)
        if (node == null || !node.isIntegralNumber || !node.canConvertToInt()) {
            throw AiCapabilityFailure("malformed-argument", "$name must be an integer.")
        }
        val number = node.intValue()
        if (number !in minimum..maximum) throw AiCapabilityFailure("argument-out-of-range", "$name is outside the allowed range.")
        return number
    }

    fun <T> enum(name: String, values: Map<String, T>): T {
        val raw = string(name, 1, 64)
        return values[raw] ?: throw AiCapabilityFailure("invalid-enum-value", "$name is not an allowed value.")
    }

    fun <T> enumArray(name: String, values: Map<String, T>, maximum: Int): List<T> {
        val node = value.get(name) ?: return emptyList()
        if (!node.isArray) throw AiCapabilityFailure("malformed-argument", "$name must be an array.")
        if (node.size() > maximum) throw AiCapabilityFailure("array-limit", "$name contains too many values.")
        val decoded = node.map { item ->
            if (!item.isTextual) throw AiCapabilityFailure("malformed-argument", "$name entries must be strings.")
            values[item.textValue()] ?: throw AiCapabilityFailure("invalid-enum-value", "$name contains an unsupported value.")
        }
        if (decoded.distinct().size != decoded.size) throw AiCapabilityFailure("duplicate-array-value", "$name values must be unique.")
        return decoded
    }
}

private fun objectSchema(properties: List<AiSchemaProperty>, required: Set<String>): AiObjectSchema =
    AiObjectSchema(properties, required, additionalProperties = false)

private fun property(name: String, schema: AiJsonSchema, description: String, nullable: Boolean = false): AiSchemaProperty =
    AiSchemaProperty(name, schema, nullable, description)

private fun isSourceId(value: String): Boolean = value.matches(Regex("[A-Za-z][A-Za-z0-9._-]{0,127}"))

private fun isForbiddenCapabilityName(name: String): Boolean {
    val forbiddenTerms = setOf(
        "filesystem", "file_read", "file_write", "shell", "code", "sparql", "turtle", "network", "url",
        "secret", "credential", "config", "permission", "approve", "reject", "apply", "rollback",
    )
    return forbiddenTerms.any { name.contains(it) }
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(StandardCharsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte) }
