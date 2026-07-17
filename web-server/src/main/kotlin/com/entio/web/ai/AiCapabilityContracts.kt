package com.entio.web.ai

import com.fasterxml.jackson.databind.JsonNode

public enum class AiCapabilityCategory {
    LOCAL_READ,
    EXTERNAL_READ,
    HELP,
    PRIVATE_DRAFT,
    ANALYSIS,
}

public enum class AiCapabilityOperationType {
    READ_PROJECT_SUMMARY,
    READ_ENTITY,
    COMPARE_ENTITIES,
    SEARCH_LOCAL_ENTITIES,
    READ_HIERARCHY,
    READ_ENTITY_USAGE,
    READ_SCREEN_CONTEXT,
    READ_AVAILABLE_ACTIONS,
    READ_WORKFLOW_STATE,
    READ_ENTIO_HELP,
    EXPLAIN_ERROR_CODE,
    READ_SEMANTIC_JOB,
    READ_PROPOSAL,
    READ_ACTIVITY,
    SEARCH_FIBO,
    READ_FIBO_ENTITY,
}

public enum class AiCapabilityAccess {
    READ_ONLY,
    PRIVATE_DRAFT_MUTATION,
}

public enum class AiRequiredRole {
    CONTRIBUTOR,
    REVIEWER,
}

public enum class AiProjectScopeRule {
    CURRENT_PROJECT,
}

public enum class AiSourceScopeRule {
    NONE,
    OPTIONAL_ALLOWED_SOURCE,
    REQUIRED_ALLOWED_SOURCE,
}

public enum class AiCapabilityAuditClassification {
    PROJECT_READ,
    EXTERNAL_READ,
    HELP_READ,
    PRIVATE_DRAFT_CHANGE,
    ANALYSIS,
}

public enum class AiConfirmationRequirement {
    NONE,
    PLAN_CONFIRMATION,
    REVIEW_SUBMISSION,
}

public sealed interface AiJsonSchema

public data class AiObjectSchema(
    val properties: List<AiSchemaProperty>,
    val required: Set<String>,
    val additionalProperties: Boolean = false,
) : AiJsonSchema {
    init {
        require(!additionalProperties) { "ai-capability-schemas-must-reject-additional-properties" }
        require(properties.map(AiSchemaProperty::name).toSet().containsAll(required)) {
            "required-schema-fields-must-be-declared"
        }
    }
}

public data class AiSchemaProperty(
    val name: String,
    val schema: AiJsonSchema,
    val nullable: Boolean = false,
    val description: String,
)

public data class AiStringSchema(
    val minLength: Int = 1,
    val maxLength: Int,
    val format: AiStringFormat? = null,
    val allowedValues: List<String> = emptyList(),
) : AiJsonSchema {
    init {
        require(minLength >= 0)
        require(maxLength >= minLength)
        require(allowedValues.distinct().size == allowedValues.size)
    }
}

public enum class AiStringFormat {
    HTTP_IRI,
    SOURCE_ID,
}

public data class AiIntegerSchema(
    val minimum: Int,
    val maximum: Int,
) : AiJsonSchema {
    init {
        require(maximum >= minimum)
    }
}

public data class AiArraySchema(
    val items: AiJsonSchema,
    val minItems: Int = 0,
    val maxItems: Int,
    val uniqueItems: Boolean = true,
) : AiJsonSchema {
    init {
        require(minItems >= 0)
        require(maxItems >= minItems)
    }
}

public sealed interface AiCapabilityArguments

public sealed interface AiSourceScopedArguments : AiCapabilityArguments {
    public val sourceId: String?
}

public data object AiProjectSummaryArguments : AiCapabilityArguments

public data class AiEntityDetailArguments(
    val entityIri: String,
    override val sourceId: String?,
) : AiSourceScopedArguments

public data class AiEntityComparisonArguments(
    val entityIris: List<String>,
    override val sourceId: String?,
) : AiSourceScopedArguments

public enum class AiEntityKindFilter {
    CLASS,
    OBJECT_PROPERTY,
    DATATYPE_PROPERTY,
    ANNOTATION_PROPERTY,
    INDIVIDUAL,
    SHAPE,
}

public data class AiLocalSearchArguments(
    val query: String,
    val kinds: List<AiEntityKindFilter>,
    override val sourceId: String?,
    val limit: Int,
) : AiSourceScopedArguments

public data class AiHierarchyArguments(
    val parentIri: String?,
    override val sourceId: String?,
    val limit: Int,
) : AiSourceScopedArguments

public data class AiEntityUsageArguments(
    val entityIri: String,
    override val sourceId: String?,
    val limit: Int,
) : AiSourceScopedArguments

public data object AiScreenContextArguments : AiCapabilityArguments

public data object AiAvailableActionsArguments : AiCapabilityArguments

public data object AiWorkflowStateArguments : AiCapabilityArguments

public enum class AiHelpTopic {
    NAVIGATION,
    ENTITY_TYPES,
    EDITING,
    STAGING,
    PROPOSALS,
    REASONING,
    SHACL,
    FIBO,
    COLLABORATION,
    CONFLICTS,
    PERMISSIONS,
    CREDENTIALS,
    AI_LIMITS,
    ERRORS,
}

public data class AiHelpArguments(
    val topic: AiHelpTopic,
) : AiCapabilityArguments

public data class AiErrorCodeArguments(
    val code: String,
) : AiCapabilityArguments

public data class AiSemanticJobArguments(
    val jobId: String,
    val limit: Int,
) : AiCapabilityArguments

public data class AiProposalReadArguments(
    val proposalId: String?,
    val limit: Int,
) : AiCapabilityArguments

public data class AiActivityReadArguments(
    val limit: Int,
) : AiCapabilityArguments

public enum class AiFiboEntityKind {
    CLASS,
    OBJECT_PROPERTY,
    DATATYPE_PROPERTY,
}

public data class AiFiboSearchArguments(
    val query: String,
    val kind: AiFiboEntityKind?,
    val moduleIri: String?,
    val limit: Int,
) : AiCapabilityArguments

public data class AiFiboEntityArguments(
    val entityIri: String,
) : AiCapabilityArguments

public fun interface AiCapabilityArgumentDecoder {
    public fun decode(input: JsonNode): AiCapabilityArguments
}

public data class AiCapabilityDefinition(
    val name: String,
    val operationType: AiCapabilityOperationType,
    val category: AiCapabilityCategory,
    val description: String,
    val inputSchema: AiObjectSchema,
    val access: AiCapabilityAccess,
    val requiredRole: AiRequiredRole,
    val requiredPermissions: Set<String>,
    val requiredFeature: String,
    val projectScope: AiProjectScopeRule = AiProjectScopeRule.CURRENT_PROJECT,
    val sourceScope: AiSourceScopeRule,
    val resultLimit: Int,
    val timeoutMillis: Long,
    val auditClassification: AiCapabilityAuditClassification,
    val confirmation: AiConfirmationRequirement = AiConfirmationRequirement.NONE,
    val decoder: AiCapabilityArgumentDecoder,
) {
    init {
        require(name.matches(Regex("[a-z][a-z0-9_]{2,63}"))) { "invalid-capability-name" }
        require(resultLimit > 0)
        require(timeoutMillis > 0)
    }
}

public data class AiCapabilityRegistrySnapshot(
    val id: String,
    val definitions: List<AiCapabilityDefinition>,
)

public data class AiCapabilityInvocation(
    val id: String,
    val capabilityName: String,
    val registrySnapshotId: String,
    val userId: String,
    val projectId: String,
    val conversationId: String,
    val arguments: JsonNode,
)

public data class AiDecodedCapabilityInvocation(
    val invocationId: String,
    val definition: AiCapabilityDefinition,
    val arguments: AiCapabilityArguments,
)

public enum class AiCapabilityResultStatus {
    COMPLETED,
    FAILED,
    LIMIT_REACHED,
    CANCELLED,
    STALE,
}

public data class AiCapabilityResult(
    val invocationId: String,
    val capabilityName: String,
    val status: AiCapabilityResultStatus,
    val summary: String,
    val resultReferenceIds: List<String> = emptyList(),
    val warnings: List<AiWarning> = emptyList(),
)

public class AiCapabilityFailure(
    public val code: String,
    message: String,
) : IllegalArgumentException(message)

public object AiCapabilityFeatures {
    public const val LOCAL_SEMANTIC_READ: String = "local-semantic-read"
    public const val ENTIO_HELP: String = "entio-help"
    public const val SEMANTIC_RESULTS: String = "semantic-results"
    public const val PROPOSAL_READ: String = "proposal-read"
    public const val ACTIVITY_READ: String = "activity-read"
    public const val FIBO_READ: String = "fibo-read"
}
