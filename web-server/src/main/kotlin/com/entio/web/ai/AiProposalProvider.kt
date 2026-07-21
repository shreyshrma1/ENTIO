package com.entio.web.ai

/** Input supplied to a provider for one review-only ontology proposal. */
public data class AiProposalGenerationInput(
    val userRequest: String,
    val ontologyContext: String,
    val fiboContext: String,
    val conversationContext: String = "",
    val currentProposal: String = "",
    /** Deterministic findings from a completed proposal, supplied only on a repair pass. */
    val validationFindings: List<String> = emptyList(),
    val repairAttempt: Int = 0,
    /** The response mode that must be repaired, when a response failed a post-generation check. */
    val repairMode: String? = null,
)

public sealed interface AiProposalGenerationResult {
    public data class Completed(val text: String) : AiProposalGenerationResult
    public data class Failed(val message: String, val retryable: Boolean = false) : AiProposalGenerationResult
}

/** Provider boundary for declarative proposals. It has no ontology tools and cannot write files. */
public interface AiProposalProvider {
    public val providerId: String
    public suspend fun generate(apiKey: String, selectedModelId: String, input: AiProposalGenerationInput): AiProposalGenerationResult
}

/** Deterministic provider used by server tests. */
public class DevelopmentAiProposalProvider(
    override val providerId: String = "provider-neutral",
) : AiProposalProvider {
    override suspend fun generate(apiKey: String, selectedModelId: String, input: AiProposalGenerationInput): AiProposalGenerationResult =
        AiProposalGenerationResult.Completed("{\"mode\":\"answer\",\"answer\":\"The development AI provider is configured, but it does not generate ontology proposals.\",\"edits\":[]}")
}
