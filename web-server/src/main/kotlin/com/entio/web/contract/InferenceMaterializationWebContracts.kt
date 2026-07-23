package com.entio.web.contract

public data class WebInferenceMaterializationSelection(
    val factId: String,
    val targetSourceId: String? = null,
)

public data class WebInferenceMaterializationRequest(
    val selections: List<WebInferenceMaterializationSelection>,
    val idempotencyKey: String,
)

public data class WebInferenceMaterializationMapping(
    val factId: String,
    val stagedChangeId: String,
)

public data class WebInferenceMaterializationResponse(
    val apiVersion: String = WEB_API_VERSION,
    val projectId: String,
    val reasoningJobId: String,
    val graphFingerprint: String,
    val mappings: List<WebInferenceMaterializationMapping>,
    val staging: WebStagingResponse,
)
