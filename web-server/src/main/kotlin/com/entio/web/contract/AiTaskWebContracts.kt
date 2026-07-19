package com.entio.web.contract

public data class WebAiTaskCreateRequest(
    val conversationId: String,
    val objective: String,
    val allowedSourceIds: List<String>,
)

public data class WebAiTaskCommandRequest(
    val expectedRevision: Long,
    val message: String? = null,
    val answer: String? = null,
    val planRevision: Int? = null,
)

public data class WebAiTask(
    val id: String,
    val conversationId: String,
    val projectId: String,
    val objective: String,
    val type: String,
    val size: String,
    val status: String,
    val revision: Long,
    val modelId: String,
    val currentWorkPackageId: String?,
    val completedWorkPackageIds: List<String>,
    val failedWorkPackageIds: List<String>,
    val privateDraftId: String?,
    val createdAt: String,
    val updatedAt: String,
)

public data class WebAiTaskResponse(val apiVersion: String = WEB_API_VERSION, val task: WebAiTask)

public data class WebAiTaskWorkspace(
    val task: WebAiTask,
    val projectFingerprint: String,
    val assumptions: List<String>,
    val openQuestions: List<String>,
    val selectedEntityIris: List<String>,
    val planId: String?,
    val planRevision: Int?,
    val analysisReferenceIds: List<String>,
    val repairCycleCount: Int,
    val toolCallCount: Int,
    val pauseCode: String?,
    val limits: List<WebAiLimit>,
)

public data class WebAiTaskWorkspaceResponse(val apiVersion: String = WEB_API_VERSION, val workspace: WebAiTaskWorkspace)

public data class WebAiTaskResourceResponse(
    val apiVersion: String = WEB_API_VERSION,
    val taskId: String,
    val revision: Long,
    val resource: String,
    val referenceIds: List<String>,
    val available: Boolean,
)

public data class WebAiTaskEvent(
    val sequence: Int,
    val taskId: String,
    val type: String,
    val status: String,
    val message: String,
    val referenceIds: List<String>,
    val createdAt: String,
)

public data class WebAiTaskResynchronization(
    val apiVersion: String = WEB_API_VERSION,
    val taskId: String,
    val reason: String,
    val authoritativeTaskRoute: String,
    val authoritativeWorkspaceRoute: String,
)
