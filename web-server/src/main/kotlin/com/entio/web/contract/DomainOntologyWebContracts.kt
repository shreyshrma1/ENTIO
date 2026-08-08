package com.entio.web.contract

import com.entio.core.DomainFoundationGroup
import com.entio.core.DomainFoundationPlan
import com.entio.core.DomainOntologyMigrationStatus
import com.entio.core.DomainOntologyProfile
import com.entio.core.DomainOntologyStatus
import com.entio.core.DomainOperationKind
import com.entio.core.DomainProfileActivationPreview
import com.entio.core.DomainProfileDeactivationPreview
import com.entio.core.DomainRecommendation
import com.entio.core.DomainRecommendationResult
import com.entio.core.DomainRetrievalAvailability
import com.entio.core.ExternalEntityKind

public data class WebDomainOntologyDescriptor(
    val sourceId: String,
    val displayName: String,
    val release: String,
    val packageFingerprint: String,
    val retrievalAvailability: DomainRetrievalAvailability,
    val selectable: Boolean,
)

public data class WebDomainOntologyListResponse(
    val apiVersion: String = WEB_API_VERSION,
    val domainOntologies: List<WebDomainOntologyDescriptor>,
)

public data class WebDomainOntologyStatusResponse(
    val apiVersion: String = WEB_API_VERSION,
    val projectId: String,
    val status: DomainOntologyStatus,
)

public data class WebDomainActivationPreviewResponse(
    val apiVersion: String = WEB_API_VERSION,
    val projectId: String,
    val activationToken: String,
    val preview: DomainProfileActivationPreview,
)

public data class WebDomainDeactivationPreviewResponse(
    val apiVersion: String = WEB_API_VERSION,
    val projectId: String,
    val deactivationToken: String?,
    val preview: DomainProfileDeactivationPreview,
)

public data class WebDomainProfileActionRequest(val confirmationToken: String)

public data class WebDomainProfileActionResponse(
    val apiVersion: String = WEB_API_VERSION,
    val projectId: String,
    val status: DomainOntologyStatus,
)

public data class WebDomainFoundationResponse(
    val apiVersion: String = WEB_API_VERSION,
    val projectId: String,
    val groups: List<DomainFoundationGroup>,
)

public data class WebDomainFoundationPlanRequest(
    val elementIds: Set<String> = emptySet(),
    val selectAll: Boolean = false,
)

public data class WebDomainFoundationPlanResponse(
    val apiVersion: String = WEB_API_VERSION,
    val plan: DomainFoundationPlan,
)

public data class WebDomainRecommendationRequest(
    val operationKind: DomainOperationKind,
    val requestedKind: ExternalEntityKind? = null,
    val draftLabel: String,
    val alternateWording: String? = null,
    val definition: String? = null,
    val currentEntityIri: String? = null,
    val requiredParentIri: String? = null,
    val requiredDomainIri: String? = null,
    val requiredRangeIri: String? = null,
    val requiredDatatypeIri: String? = null,
    val nearbyProjectIris: Set<String> = emptySet(),
    val targetSourceId: String? = null,
    val languagePreference: String? = null,
    val broadSearch: Boolean = false,
)

public data class WebDomainRecommendationResponse(
    val apiVersion: String = WEB_API_VERSION,
    val projectId: String,
    val result: DomainRecommendationResult,
)

public data class WebDomainRecommendationDetailResponse(
    val apiVersion: String = WEB_API_VERSION,
    val projectId: String,
    val recommendation: DomainRecommendation,
)

public data class WebDomainDependencyPreviewResponse(
    val apiVersion: String = WEB_API_VERSION,
    val projectId: String,
    val recommendationId: String,
    val dependencyIris: List<String>,
)

public data class WebDomainMigrationResponse(
    val apiVersion: String = WEB_API_VERSION,
    val projectId: String,
    val status: DomainOntologyMigrationStatus,
    val recognizedIriCount: Int,
    val proposedProfile: DomainOntologyProfile? = null,
    val mutatesProject: Boolean = false,
)
