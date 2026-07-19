package com.entio.web.ai.provider

import com.entio.web.ai.models.AiProviderModelDescriptor

public enum class AiProviderModelErrorCategory {
    AUTHENTICATION,
    AUTHORIZATION,
    MODEL_NOT_AVAILABLE,
    RATE_LIMITED,
    TIMEOUT,
    PROVIDER_UNAVAILABLE,
    MALFORMED_RESPONSE,
}

public data class AiProviderModelError(
    val category: AiProviderModelErrorCategory,
    val retryable: Boolean,
    val retryAfterSeconds: Long? = null,
)

public sealed interface AiModelDiscoveryResult {
    public data class Discovered(val models: List<AiProviderModelDescriptor>) : AiModelDiscoveryResult

    public data class Failed(val error: AiProviderModelError) : AiModelDiscoveryResult
}

public sealed interface AiModelVerificationResult {
    public data object Verified : AiModelVerificationResult

    public data class Failed(val error: AiProviderModelError) : AiModelVerificationResult
}

/** Provider-neutral model inventory and harmless compatibility-verification boundary. */
public interface AiModelProviderClient {
    public val providerId: String

    public suspend fun discoverModels(apiKey: String): AiModelDiscoveryResult

    public suspend fun verifyModel(apiKey: String, modelId: String): AiModelVerificationResult
}

/** Deterministic CI fixture. It never performs network or credential persistence. */
public class DevelopmentAiModelProviderClient(
    override val providerId: String = "openai",
    private val models: List<AiProviderModelDescriptor> = emptyList(),
    private val verificationByModelId: Map<String, AiModelVerificationResult> = emptyMap(),
    private val discoveryFailure: AiProviderModelError? = null,
) : AiModelProviderClient {
    override suspend fun discoverModels(apiKey: String): AiModelDiscoveryResult = discoveryFailure
        ?.let(AiModelDiscoveryResult::Failed)
        ?: AiModelDiscoveryResult.Discovered(models.sortedBy(AiProviderModelDescriptor::modelId))

    override suspend fun verifyModel(apiKey: String, modelId: String): AiModelVerificationResult =
        verificationByModelId[modelId]
            ?: AiModelVerificationResult.Failed(
                AiProviderModelError(AiProviderModelErrorCategory.MODEL_NOT_AVAILABLE, retryable = false),
            )
}
