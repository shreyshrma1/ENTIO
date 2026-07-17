package com.entio.web

import com.entio.core.AnnotationStatement
import com.entio.core.DatatypePropertyAssertion
import com.entio.core.EntioProject
import com.entio.core.EntioResult
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.LocalizedText
import com.entio.core.ObjectPropertyAssertion
import com.entio.core.OntologyEntityDescriptor
import com.entio.core.RdfLiteral
import com.entio.core.RdfResource
import com.entio.core.RdfTerm
import com.entio.core.SemanticSearchQuery
import com.entio.semantic.ProjectLoader
import com.entio.semantic.SemanticDescriptionService
import com.entio.web.contract.ProjectRegistry
import com.entio.web.contract.WebPage
import com.entio.web.contract.WebPageRequest
import com.entio.web.contract.toWebPage

private const val WEB_API_VERSION: String = "v1"

public data class WebProjectSummaryResponse(
    val apiVersion: String = WEB_API_VERSION,
    val project: WebProjectSummary,
    val sources: List<WebOntologySourceSummary>,
    val symbolCount: Int,
    val graphTripleCount: Int,
)

public data class WebProjectSummary(
    val id: String,
    val displayName: String,
    val name: String,
)

public data class WebOntologySourceSummary(
    val id: String,
    val path: String,
    val format: String,
    val roles: Set<String>,
    val tripleCount: Int,
)

public data class WebHierarchyItem(
    val iri: String,
    val label: String,
    val kind: String = "class",
    val sourceId: String,
    val childCount: Int,
)

public data class WebHierarchyResponse(
    val apiVersion: String = WEB_API_VERSION,
    val sourceId: String?,
    val parentIri: String?,
    val page: WebPage<WebHierarchyItem>,
)

public data class WebOutlineItem(
    val iri: String,
    val label: String,
    val kind: String,
    val sourceId: String,
)

public data class WebOutlineResponse(
    val apiVersion: String = WEB_API_VERSION,
    val sourceId: String?,
    val page: WebPage<WebOutlineItem>,
)

public data class WebEntityReference(
    val iri: String,
    val label: String,
    val kind: String? = null,
    val sourceId: String? = null,
)

public data class WebTextValue(
    val value: String,
    val language: String? = null,
    val datatype: String? = null,
)

public data class WebRdfValue(
    val kind: String,
    val value: String,
    val label: String? = null,
    val datatype: String? = null,
    val language: String? = null,
)

public data class WebAnnotation(
    val property: WebEntityReference,
    val value: WebRdfValue,
    val sourceId: String,
)

public data class WebRelationship(
    val direction: String,
    val predicate: WebEntityReference,
    val value: WebRdfValue,
    val sourceId: String,
)

public data class WebEntityDetailResponse(
    val apiVersion: String = WEB_API_VERSION,
    val iri: String,
    val label: String,
    val kind: String,
    val sourceId: String,
    val sourceOntologyId: String?,
    val locality: String,
    val preferredLabelSource: String,
    val alternateLabels: List<WebTextValue>,
    val definitions: List<WebTextValue>,
    val annotations: List<WebAnnotation>,
    val directSuperclasses: List<WebEntityReference> = emptyList(),
    val directSubclasses: List<WebEntityReference> = emptyList(),
    val directlyTypedIndividuals: List<WebEntityReference> = emptyList(),
    val assertedTypes: List<WebEntityReference> = emptyList(),
    val domains: List<WebEntityReference> = emptyList(),
    val ranges: List<WebEntityReference> = emptyList(),
    val outgoingRelationships: List<WebRelationship> = emptyList(),
    val incomingRelationships: List<WebRelationship> = emptyList(),
)

public data class WebSemanticSearchHit(
    val iri: String,
    val label: String,
    val kind: String,
    val sourceId: String,
    val reason: String,
    val rank: Int,
    val locality: String,
)

public data class WebSemanticSearchResponse(
    val apiVersion: String = WEB_API_VERSION,
    val query: String,
    val page: WebPage<WebSemanticSearchHit>,
)

public class ProjectReadFailure(
    public val code: String,
    message: String,
) : IllegalArgumentException(message)

/** Adapts existing semantic services to stable, read-only web responses. */
public class ReadOnlyProjectAdapter(
    private val projectRegistry: ProjectRegistry,
    private val projectLoader: ProjectLoader = ProjectLoader(),
    private val descriptionService: SemanticDescriptionService = SemanticDescriptionService(),
) {
    public fun summary(projectId: String): WebProjectSummaryResponse {
        val project = load(projectId)
        val descriptor = projectRegistry.find(projectId)
            ?: throw ProjectReadFailure("unknown-project", "The requested project is not registered.")
        val sources = project.ontologies.map { ontology ->
            val source = project.config.ontologySources.first { it.id == ontology.source.id }
            WebOntologySourceSummary(
                id = ontology.source.id,
                path = source.path,
                format = ontology.source.format.name.lowercase(),
                roles = ontology.source.roles.map { it.name.lowercase() }.toSortedSet(),
                tripleCount = ontology.graph.triples.size,
            )
        }
        return WebProjectSummaryResponse(
            project = WebProjectSummary(descriptor.id, descriptor.displayName, project.config.name),
            sources = sources.sortedBy(WebOntologySourceSummary::id),
            symbolCount = project.symbols.size,
            graphTripleCount = project.graph.triples.size,
        )
    }

    public fun sources(projectId: String, request: WebPageRequest): WebPage<WebOntologySourceSummary> =
        summary(projectId).sources.toWebPage(request)

    public fun hierarchy(
        projectId: String,
        sourceId: String?,
        parentIri: Iri?,
        request: WebPageRequest,
    ): WebHierarchyResponse {
        val project = load(projectId)
        val descriptors = descriptionService.describeAll(project)
            .filterIsInstance<OntologyEntityDescriptor.Class>()
            .filter { sourceId == null || it.common.sourceId == sourceId }
        if (parentIri != null && descriptors.none { it.common.entity == parentIri }) {
            throw ProjectReadFailure("missing-hierarchy-parent", "The requested hierarchy parent was not found.")
        }

        val classes = descriptors
            .filter { descriptor ->
                if (parentIri == null) descriptor.directSuperclasses.isEmpty()
                else parentIri in descriptor.directSuperclasses
            }
            .map { descriptor ->
                WebHierarchyItem(
                    iri = descriptor.common.entity.value,
                    label = descriptor.common.displayLabel(),
                    sourceId = descriptor.common.sourceId,
                    childCount = descriptor.directSubclasses.size,
                )
            }
            .sortedWith(compareBy<WebHierarchyItem> { it.label.lowercase() }.thenBy { it.iri })

        return WebHierarchyResponse(
            sourceId = sourceId,
            parentIri = parentIri?.value,
            page = classes.toWebPage(request),
        )
    }

    public fun outline(
        projectId: String,
        sourceId: String?,
        request: WebPageRequest,
    ): WebOutlineResponse {
        val items = descriptionService.describeAll(load(projectId))
            .map(OntologyEntityDescriptor::common)
            .filter { sourceId == null || it.sourceId == sourceId }
            .map { descriptor ->
                WebOutlineItem(
                    iri = descriptor.entity.value,
                    label = descriptor.displayLabel(),
                    kind = descriptor.kind.name,
                    sourceId = descriptor.sourceId,
                )
            }
            .sortedWith(
                compareBy<WebOutlineItem> { outlineKindOrder(it.kind) }
                    .thenBy { it.label.lowercase() }
                    .thenBy(WebOutlineItem::iri),
            )

        return WebOutlineResponse(
            sourceId = sourceId,
            page = items.toWebPage(request),
        )
    }

    public fun entity(
        projectId: String,
        entityIri: Iri,
        sourceId: String?,
    ): WebEntityDetailResponse {
        val project = load(projectId)
        val descriptors = descriptionService.describeAll(project)
        val descriptor = descriptors.firstOrNull {
            it.common.entity == entityIri && (sourceId == null || it.common.sourceId == sourceId)
        } ?: throw ProjectReadFailure("missing-entity", "The requested entity was not found.")
        val labels = descriptors.associate { it.common.entity.value to it.common.displayLabel() }
        val kinds = descriptors.associate { it.common.entity.value to it.common.kind.name }
        val sourceTriples = project.ontologies
            .filter { sourceId == null || it.source.id == descriptor.common.sourceId }
            .flatMap { ontology -> ontology.graph.triples.map { ontology.source.id to it } }

        return descriptor.toResponse(labels, kinds, sourceTriples)
    }

    public fun search(
        projectId: String,
        query: SemanticSearchQuery,
        request: WebPageRequest,
    ): WebSemanticSearchResponse {
        if (query.text.isBlank()) throw ProjectReadFailure("invalid-search-query", "Search text is required.")
        val results = descriptionService.search(load(projectId), query)
            .map { result ->
                WebSemanticSearchHit(
                    iri = result.descriptor.common.entity.value,
                    label = result.descriptor.common.displayLabel(),
                    kind = result.descriptor.common.kind.name,
                    sourceId = result.descriptor.common.sourceId,
                    reason = result.reason.name,
                    rank = result.rank,
                    locality = result.descriptor.common.locality.name,
                )
            }
        return WebSemanticSearchResponse(query = query.text, page = results.toWebPage(request))
    }

    private fun load(projectId: String): EntioProject {
        if (projectRegistry.find(projectId) == null) {
            throw ProjectReadFailure("unknown-project", "The requested project is not registered.")
        }
        return when (val result = projectLoader.loadProject(projectRegistry.rootFor(projectId))) {
            is EntioResult.Success -> result.value
            is EntioResult.Failure -> throw ProjectReadFailure(
                "project-load-failed",
                result.message,
            )
        }
    }

    private fun outlineKindOrder(kind: String): Int = when (kind) {
        "Class" -> 0
        "Individual" -> 1
        "ObjectProperty", "DatatypeProperty", "AnnotationProperty" -> 2
        else -> 3
    }

    private fun OntologyEntityDescriptor.toResponse(
        labels: Map<String, String>,
        kinds: Map<String, String>,
        triples: List<Pair<String, GraphTriple>>,
    ): WebEntityDetailResponse {
        val common = common
        val outgoing = triples
            .filter { (_, triple) -> triple.subjectResource == common.entity }
            .map { (sourceId, triple) -> triple.toRelationship("outgoing", sourceId, labels, kinds) }
            .sortedWith(relationshipComparator)
        val incoming = triples
            .filter { (_, triple) -> triple.objectTerm == common.entity }
            .map { (sourceId, triple) -> triple.toRelationship("incoming", sourceId, labels, kinds) }
            .sortedWith(relationshipComparator)

        val specific = when (this) {
            is OntologyEntityDescriptor.Class -> SpecificFields(
                directSuperclasses = directSuperclasses.map { it.reference(labels, kinds) },
                directSubclasses = directSubclasses.map { it.reference(labels, kinds) },
                directlyTypedIndividuals = directlyTypedIndividuals.map { it.reference(labels, kinds) },
            )
            is OntologyEntityDescriptor.ObjectProperty -> SpecificFields(
                domains = domains.map { it.reference(labels, kinds) },
                ranges = ranges.map { it.reference(labels, kinds) },
            )
            is OntologyEntityDescriptor.DatatypeProperty -> SpecificFields(
                domains = domains.map { it.reference(labels, kinds) },
                ranges = datatypeRanges.map { it.reference(labels, kinds) },
            )
            is OntologyEntityDescriptor.AnnotationProperty -> SpecificFields()
            is OntologyEntityDescriptor.Individual -> SpecificFields(
                assertedTypes = assertedTypes.map { it.reference(labels, kinds) },
                outgoingRelationships = objectPropertyAssertions.map { it.toRelationship(labels, kinds) } +
                    datatypePropertyAssertions.map { it.toRelationship(labels, kinds) },
            )
        }

        return WebEntityDetailResponse(
            iri = common.entity.value,
            label = common.displayLabel(),
            kind = common.kind.name,
            sourceId = common.sourceId,
            sourceOntologyId = common.sourceOntologyId,
            locality = common.locality.name,
            preferredLabelSource = common.preferredLabelSource.name,
            alternateLabels = common.alternateLabels.map(::textValue),
            definitions = common.definitions.map(::textValue),
            annotations = common.annotations.map { it.toAnnotation(labels, kinds) },
            directSuperclasses = specific.directSuperclasses,
            directSubclasses = specific.directSubclasses,
            directlyTypedIndividuals = specific.directlyTypedIndividuals,
            assertedTypes = specific.assertedTypes,
            domains = specific.domains,
            ranges = specific.ranges,
            outgoingRelationships = (outgoing + specific.outgoingRelationships).distinctBy { it.key() },
            incomingRelationships = incoming,
        )
    }

    private data class SpecificFields(
        val directSuperclasses: List<WebEntityReference> = emptyList(),
        val directSubclasses: List<WebEntityReference> = emptyList(),
        val directlyTypedIndividuals: List<WebEntityReference> = emptyList(),
        val assertedTypes: List<WebEntityReference> = emptyList(),
        val domains: List<WebEntityReference> = emptyList(),
        val ranges: List<WebEntityReference> = emptyList(),
        val outgoingRelationships: List<WebRelationship> = emptyList(),
    )

    private fun AnnotationStatement.toAnnotation(
        labels: Map<String, String>,
        kinds: Map<String, String>,
    ): WebAnnotation = WebAnnotation(
        property = property.reference(labels, kinds),
        value = value.term.toWebValue(labels),
        sourceId = sourceId,
    )

    private fun ObjectPropertyAssertion.toRelationship(
        labels: Map<String, String>,
        kinds: Map<String, String>,
    ): WebRelationship = WebRelationship(
        direction = "outgoing",
        predicate = property.reference(labels, kinds),
        value = value.toWebValue(labels),
        sourceId = sourceId,
    )

    private fun DatatypePropertyAssertion.toRelationship(
        labels: Map<String, String>,
        kinds: Map<String, String>,
    ): WebRelationship = WebRelationship(
        direction = "outgoing",
        predicate = property.reference(labels, kinds),
        value = value.toWebValue(labels),
        sourceId = sourceId,
    )

    private fun GraphTriple.toRelationship(
        direction: String,
        sourceId: String,
        labels: Map<String, String>,
        kinds: Map<String, String>,
    ): WebRelationship = WebRelationship(
        direction = direction,
        predicate = predicate.reference(labels, kinds),
        value = objectTerm.toWebValue(labels),
        sourceId = sourceId,
    )

    private fun RdfTerm.toWebValue(labels: Map<String, String>): WebRdfValue = when (this) {
        is RdfResource -> WebRdfValue(
            kind = if (this is com.entio.core.BlankNodeResource) "blank-node" else "iri",
            value = value,
            label = labels[value],
        )
        is RdfLiteral -> WebRdfValue(
            kind = "literal",
            value = lexicalForm,
            datatype = datatypeIri?.value,
            language = languageTag,
        )
    }

    private fun RdfResource.reference(
        labels: Map<String, String>,
        kinds: Map<String, String>,
    ): WebEntityReference = WebEntityReference(
        iri = value,
        label = labels[value] ?: value.localName(),
        kind = kinds[value],
    )

    private fun Iri.reference(
        labels: Map<String, String>,
        kinds: Map<String, String>,
    ): WebEntityReference = WebEntityReference(
        iri = value,
        label = labels[value] ?: value.localName(),
        kind = kinds[value],
    )

    private fun LocalizedText.toWebText(): WebTextValue = WebTextValue(lexicalForm, languageTag, datatypeIri?.value)

    private fun textValue(text: LocalizedText): WebTextValue = text.toWebText()

    private fun WebRelationship.key(): String = listOf(direction, predicate.iri, value.kind, value.value, sourceId).joinToString("\u0000")

    private val relationshipComparator = compareBy<WebRelationship> { it.direction }
        .thenBy { it.predicate.label }
        .thenBy { it.predicate.iri }
        .thenBy { it.value.label.orEmpty() }
        .thenBy { it.value.value }

}

private fun com.entio.core.SemanticDescriptorCommon.displayLabel(): String =
    preferredLabel?.lexicalForm ?: entity.value.substringAfterLast('#', entity.value.substringAfterLast('/')).ifBlank { entity.value }

private fun String.localName(): String = substringAfterLast('#', substringAfterLast('/')).ifBlank { this }
