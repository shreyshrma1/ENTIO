package com.entio.web.ai

/** Input supplied to a provider for one review-only ontology proposal. */
public data class AiProposalGenerationInput(
    val userRequest: String,
    val ontologyContext: String,
    val fiboContext: String,
    val conversation: List<AiConversationTurn> = emptyList(),
    val currentProposal: String = "",
    /** Deterministic findings from a completed proposal, supplied only on a repair pass. */
    val validationFindings: List<String> = emptyList(),
    val repairAttempt: Int = 0,
    /** The response mode that must be repaired, when a response failed a post-generation check. */
    val repairMode: String? = null,
    val responseKind: AiResponseKind? = null,
)

public enum class AiResponseKind {
    Answer,
    Proposal,
    Clarification,
}

/** Model-selected request for additional read-only external ontology context. */
public data class AiExternalContextRequest(
    val useFibo: Boolean,
    val query: String? = null,
)

/** One native user or assistant message supplied to the provider. */
public data class AiConversationTurn(
    val role: String,
    val content: String,
) {
    init {
        require(role == "user" || role == "assistant")
        require(content.isNotBlank())
    }
}

public sealed interface AiProposalGenerationResult {
    public data class Completed(val text: String) : AiProposalGenerationResult
    public data class Failed(val message: String, val retryable: Boolean = false) : AiProposalGenerationResult
}

/** Provider boundary for declarative proposals. It has no ontology tools and cannot write files. */
public interface AiProposalProvider {
    public val providerId: String
    public suspend fun route(apiKey: String, selectedModelId: String, input: AiProposalGenerationInput): AiResponseKind
    public suspend fun requestExternalContext(apiKey: String, selectedModelId: String, input: AiProposalGenerationInput): AiExternalContextRequest = AiExternalContextRequest(false)
    public suspend fun generate(apiKey: String, selectedModelId: String, input: AiProposalGenerationInput): AiProposalGenerationResult
}

/** Deterministic provider used by server tests. */
public class DevelopmentAiProposalProvider(
    override val providerId: String = "provider-neutral",
) : AiProposalProvider {
    override suspend fun route(apiKey: String, selectedModelId: String, input: AiProposalGenerationInput): AiResponseKind = AiResponseKind.Answer

    override suspend fun generate(apiKey: String, selectedModelId: String, input: AiProposalGenerationInput): AiProposalGenerationResult =
        AiProposalGenerationResult.Completed("{\"mode\":\"answer\",\"answer\":\"The development AI provider is configured, but it does not generate ontology proposals.\",\"edits\":[]}")
}
