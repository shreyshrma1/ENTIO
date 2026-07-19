package com.entio.web.contract

public data class WebAiModelSelectionRequest(
    val modelId: String,
)

public data class WebAiModelDescriptor(
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val description: String,
    val metadataKnown: Boolean,
    val recommended: Boolean,
    val capabilityTier: String?,
    val timeoutClass: String?,
    val relativeSpeed: String?,
    val relativeCost: String?,
    val verificationStatus: String,
    val compatibilityStatus: String,
    val policyVersion: String,
)

public data class WebAiProviderSettingsResponse(
    val apiVersion: String = WEB_API_VERSION,
    val providerId: String?,
    val credentialStatus: String,
    val discoveryStatus: String,
    val discoveredAt: String?,
    val policyVersion: String,
    val models: List<WebAiModelDescriptor>,
    val unsupportedProviderModelCount: Int,
    val selectedModel: WebAiModelDescriptor?,
    val selectionStatus: String,
    val selectedModelVerifiedAt: String?,
    val errorCode: String?,
    val availableActions: List<String>,
)
