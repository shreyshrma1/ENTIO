package com.entio.web.contract

public data class WebStageChangeRequest(
    val sourceId: String,
    val editType: String,
    val classIri: String? = null,
    val superclassIri: String? = null,
    val propertyIri: String? = null,
    val domainClassIri: String? = null,
    val rangeIri: String? = null,
    val individualIri: String? = null,
    val resourceIri: String? = null,
    val typeIri: String? = null,
    val subjectIri: String? = null,
    val objectIri: String? = null,
    val targetIri: String? = null,
    val targetLabel: String? = null,
    val dependencyKeys: Set<String> = emptySet(),
    val label: String? = null,
    val value: String? = null,
    val datatypeIri: String? = null,
    val languageTag: String? = null,
    val comment: String? = null,
    val aiGenerated: Boolean = false,
    val idempotencyKey: String? = null,
)

public data class WebStagedEntry(
    val id: String,
    val order: Int,
    val sourceId: String,
    val summary: String,
    val editType: String,
    val status: String,
    val authorId: String,
    val latestEditorId: String,
    val comment: String?,
    val aiGenerated: Boolean,
    val normalizedValues: Map<String, String>,
    val generatedIris: List<String>,
    val validationMessages: List<String>,
)

public data class WebDiffEntry(
    val kind: String,
    val subject: String,
    val predicate: String?,
    val objectValue: String?,
    val description: String,
)

public data class WebProposalState(
    val id: String,
    val status: String,
    val stagedChangeIds: List<String>,
    val baselineProjectFingerprint: String?,
    val validationMessages: List<String>,
    val diff: List<WebDiffEntry>,
    val message: String? = null,
)

public data class WebStagingResponse(
    val apiVersion: String = WEB_API_VERSION,
    val projectId: String,
    val status: String,
    val entries: List<WebStagedEntry>,
    val proposal: WebProposalState? = null,
)
