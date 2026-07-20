package com.entio.web.ai

import com.entio.web.ai.models.AiModelDiscoveryService
import com.entio.web.ai.models.AiModelSelectionService
import com.entio.web.ai.models.AiModelSelectionStatus
import com.entio.web.ai.models.AiProviderSettingsService
import com.entio.web.ai.models.AiSelectableModelDescriptor
import com.entio.web.ai.models.AiSettingsCredentialStatus
import com.entio.web.ai.models.AiUserProviderSettings
import com.entio.web.contract.WebAiModelDescriptor
import com.entio.web.contract.WebAiProviderSettingsResponse

internal class AiModelWebBoundary(
    private val settings: AiProviderSettingsService,
    private val discovery: AiModelDiscoveryService,
    private val selection: AiModelSelectionService,
) {
    fun status(userId: String): WebAiProviderSettingsResponse {
        val current = settings.settings(userId)
        return current.toWeb(isFresh = discovery.isFresh(current))
    }

    suspend fun saveCredential(userId: String, request: AiCredentialRequest): WebAiProviderSettingsResponse =
        settings.saveCredential(userId, request.providerId, request.apiKey).toWeb()

    fun removeCredential(userId: String): WebAiProviderSettingsResponse = settings.removeCredential(userId).toWeb()

    suspend fun discover(userId: String): WebAiProviderSettingsResponse = discovery.discover(userId, force = true).toWeb()

    suspend fun select(
        userId: String,
        modelId: String,
        idempotencyKey: String,
    ): WebAiProviderSettingsResponse = selection.selectAndVerify(userId, modelId, idempotencyKey).toWeb()

    suspend fun retest(userId: String, idempotencyKey: String): WebAiProviderSettingsResponse =
        selection.retest(userId, idempotencyKey).toWeb()

    fun clearSelection(userId: String): WebAiProviderSettingsResponse = selection.clear(userId).toWeb()
}

private fun AiUserProviderSettings.toWeb(isFresh: Boolean = true): WebAiProviderSettingsResponse {
    val projected = if (
        !isFresh && credentialStatus == AiSettingsCredentialStatus.VALID &&
            discoveryStatus == com.entio.web.ai.models.AiModelDiscoveryStatus.COMPLETED
    ) {
        copy(
            discoveryStatus = com.entio.web.ai.models.AiModelDiscoveryStatus.STALE,
            selectionStatus = AiModelSelectionStatus.UNAVAILABLE,
            selectedModelVerifiedAt = null,
        )
    } else {
        this
    }
    val models = projected.candidates.map(AiSelectableModelDescriptor::toWeb)
    val selected = projected.selectedModelId?.let { id -> models.find { it.modelId == id } }
    return WebAiProviderSettingsResponse(
        providerId = projected.providerId,
        credentialStatus = projected.credentialStatus.name,
        discoveryStatus = projected.discoveryStatus.name,
        discoveredAt = projected.discoveredAt?.toString(),
        policyVersion = projected.policyVersion,
        models = models,
        unsupportedProviderModelCount = projected.unsupportedProviderModelCount,
        selectedModel = selected,
        selectionStatus = projected.selectionStatus.name,
        selectedModelVerifiedAt = projected.selectedModelVerifiedAt?.toString(),
        errorCode = projected.lastProviderErrorCategory?.webErrorCode(),
        availableActions = projected.availableActions(),
    )
}

private fun AiSelectableModelDescriptor.toWeb(): WebAiModelDescriptor = WebAiModelDescriptor(
    providerId = providerId,
    modelId = modelId,
    displayName = displayName,
    description = description,
    metadataKnown = metadataKnown,
    recommended = recommended,
    capabilityTier = capabilityTier?.name,
    timeoutClass = timeoutClass?.name,
    relativeSpeed = relativeSpeed,
    relativeCost = relativeCost,
    verificationStatus = verificationStatus.name,
    compatibilityStatus = compatibilityState.name,
    policyVersion = policyVersion,
)

private fun AiUserProviderSettings.availableActions(): List<String> = buildList {
    if (credentialStatus == AiSettingsCredentialStatus.NOT_CONFIGURED) {
        add("ADD_CREDENTIAL")
        return@buildList
    }
    add("REFRESH_MODELS")
    if (candidates.isNotEmpty()) add("SELECT_AND_TEST_MODEL")
    if (selectionStatus == AiModelSelectionStatus.READY) {
        add("RETEST_MODEL")
        add("CHANGE_MODEL")
        add("CLEAR_SELECTION")
    }
    add("REPLACE_CREDENTIAL")
    add("REMOVE_CREDENTIAL")
}

private fun com.entio.web.ai.provider.AiProviderModelErrorCategory.webErrorCode(): String = when (this) {
    com.entio.web.ai.provider.AiProviderModelErrorCategory.AUTHENTICATION -> "AI_CREDENTIAL_INVALID"
    com.entio.web.ai.provider.AiProviderModelErrorCategory.AUTHORIZATION -> "AI_MODEL_DISCOVERY_FAILED"
    com.entio.web.ai.provider.AiProviderModelErrorCategory.MODEL_NOT_AVAILABLE -> "AI_MODEL_NOT_AVAILABLE"
    com.entio.web.ai.provider.AiProviderModelErrorCategory.RATE_LIMITED -> "AI_PROVIDER_RATE_LIMITED"
    com.entio.web.ai.provider.AiProviderModelErrorCategory.TIMEOUT -> "AI_PROVIDER_TIMEOUT"
    com.entio.web.ai.provider.AiProviderModelErrorCategory.PROVIDER_UNAVAILABLE -> "AI_PROVIDER_UNAVAILABLE"
    com.entio.web.ai.provider.AiProviderModelErrorCategory.MALFORMED_RESPONSE -> "AI_MODEL_DISCOVERY_FAILED"
}
