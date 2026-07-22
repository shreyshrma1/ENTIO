package com.entio.web

import com.entio.core.ExternalCatalogElement
import com.entio.core.ExternalDependency
import com.entio.core.ExternalDependencySelection
import com.entio.core.ExternalDependencySet
import com.entio.core.ExternalEntityKind
import com.entio.core.ExternalOntologyModule
import com.entio.core.ExternalProposalIntent
import com.entio.core.Iri
import com.entio.core.OntologyEntityDescriptor
import com.entio.core.Phase5PackageIdentity
import com.entio.semantic.ExternalDependencyReviewer
import com.entio.semantic.ExternalProposalIntentTranslator
import com.entio.semantic.ExternalFiboCatalogSession
import com.entio.semantic.FiboCatalogLoader
import com.entio.semantic.FiboSchemaSearchService
import com.entio.semantic.ProjectLoader
import com.entio.web.contract.ProjectRegistry
import com.entio.web.contract.WebPage
import com.entio.web.contract.WebPageRequest
import com.entio.web.contract.WebStagingResponse
import java.nio.file.Files
import java.nio.file.Path

public data class WebFiboModule(
    val ontologyIri: String,
    val label: String,
    val domain: String,
    val sourcePath: String,
    val maturity: String,
    val curated: Boolean,
    val elementCount: Int,
)

public data class WebFiboElement(
    val iri: String,
    val label: String,
    val kind: String,
    val moduleIri: String,
    val domain: String,
    val maturity: String,
    val catalogStatus: String,
    val sourcePath: String,
    val alternateLabels: List<String>,
    val definitions: List<String>,
    val parents: List<String>,
    val domains: List<String>,
    val ranges: List<String>,
)

public data class WebFiboDependency(
    val category: String,
    val requirement: String,
    val visibility: String,
    val selection: String,
    val reason: String,
    val externalIri: String?,
    val label: String?,
)

public data class WebFiboModulesResponse(
    val apiVersion: String = "v1",
    val sourceId: String,
    val release: String,
    val page: WebPage<WebFiboModule>,
)

public data class WebFiboElementsResponse(
    val apiVersion: String = "v1",
    val moduleIri: String,
    val page: WebPage<WebFiboElement>,
)

public data class WebFiboSearchResponse(
    val apiVersion: String = "v1",
    val query: String,
    val page: WebPage<WebFiboElement>,
)

public data class WebFiboDetailsResponse(
    val apiVersion: String = "v1",
    val element: WebFiboElement,
    val dependencies: List<WebFiboDependency>,
)

public data class WebFiboProposalRequest(
    val intentType: String,
    val sourceId: String = "simple",
    val targetOntologyIri: String,
    val externalIri: String,
    val localClassIri: String? = null,
    val selectedDependencyIris: List<String> = emptyList(),
    val idempotencyKey: String? = null,
)

/** Web adapter over the pinned, immutable Phase 5 FIBO catalog. */
public class FiboWebService(
    private val projectRegistry: ProjectRegistry,
    private val staging: StagingWorkflowService,
    packageRoot: Path = defaultPackageRoot(),
    private val projectLoader: ProjectLoader = ProjectLoader(),
    private val dependencyReviewer: ExternalDependencyReviewer = ExternalDependencyReviewer(),
    private val searchService: FiboSchemaSearchService = FiboSchemaSearchService(),
) {
    private val catalogLoader = FiboCatalogLoader(packageRoot)
    private val translator = ExternalProposalIntentTranslator()

    public fun modules(projectId: String, curatedOnly: Boolean, request: WebPageRequest): WebFiboModulesResponse {
        val session = session(projectId)
        val page = if (curatedOnly) session.browseCuratedModules(page = request.page(), pageSize = request.limit) else session.browseModules(page = request.page(), pageSize = request.limit)
        val counts = session.allElements().groupingBy { it.descriptor.moduleIri }.eachCount()
        return WebFiboModulesResponse(
            sourceId = session.source.id,
            release = session.manifest.release,
            page = WebPage(
                items = page.items.map { module ->
                    WebFiboModule(module.ontologyIri.value, module.label, module.domain, module.sourcePath, module.maturity.name, module.curated, counts[module.ontologyIri] ?: 0)
                },
                offset = request.offset,
                limit = request.limit,
                total = page.totalCount,
                nextOffset = (request.offset + page.items.size).takeIf { it < page.totalCount },
            ),
        )
    }

    public fun moduleElements(projectId: String, moduleIri: Iri, request: WebPageRequest): WebFiboElementsResponse {
        val session = session(projectId)
        val page = session.browseModule(moduleIri, page = request.page(), pageSize = request.limit)
        return WebFiboElementsResponse(
            moduleIri = moduleIri.value,
            page = WebPage(
                items = page.items.map { toElement(it, session) },
                offset = request.offset,
                limit = request.limit,
                total = page.totalCount,
                nextOffset = (request.offset + page.items.size).takeIf { it < page.totalCount },
            ),
        )
    }

    public fun search(
        projectId: String,
        text: String,
        kind: ExternalEntityKind?,
        moduleIri: Iri?,
        curatedOnly: Boolean,
        request: WebPageRequest,
    ): WebFiboSearchResponse {
        val session = session(projectId)
        val result = searchService.search(
            session,
            com.entio.core.ExternalSchemaSearchQuery(
                text = text,
                kind = kind,
                moduleIri = moduleIri,
                curatedOnly = curatedOnly,
                pageSize = request.limit,
                page = request.page(),
            ),
        )
        val items = result.candidates.map { candidate -> toElement(ExternalCatalogElement(candidate.descriptor, candidate.kind), session) }
        return WebFiboSearchResponse(
            query = text,
            page = WebPage(items, request.offset, request.limit, result.totalResultCount, (request.offset + items.size).takeIf { it < result.totalResultCount }),
        )
    }

    public fun details(projectId: String, iri: Iri): WebFiboDetailsResponse {
        val session = session(projectId)
        val element = session.find(iri) ?: throw WebWorkflowFailure("external-element-not-found", "The external ontology element was not found.")
        val project = project(projectId)
        val dependencies = dependencyReviewer.review(session, element, project)
        return WebFiboDetailsResponse(
            element = toElement(element, session),
            dependencies = dependencies.dependencies.map { it.toWebDependency(session) },
        )
    }

    public fun stageProposal(projectId: String, request: WebFiboProposalRequest, userId: String): WebStagingResponse {
        val session = session(projectId)
        val element = session.find(Iri(request.externalIri))
            ?: throw WebWorkflowFailure("external-element-not-found", "The external ontology element was not found.")
        val dependencies = dependencyReviewer.review(session, element, project(projectId))
        val selected = request.selectedDependencyIris.map(::Iri).toSet()
        val selectedDependencies = dependencies.copy(
            dependencies = dependencies.dependencies.map { dependency ->
                if (dependency.selection == ExternalDependencySelection.Missing && dependency.externalIri in selected) {
                    dependency.copy(selection = ExternalDependencySelection.NewlySelected)
                } else {
                    dependency
                }
            },
        )
        val intent = intent(request, element, selectedDependencies.dependencies)
        val targetOntologyIri = Iri(request.targetOntologyIri)
        val materializedElements = materializedElements(session, element, request.selectedDependencyIris.toSet())
        val currentProject = project(projectId)
        when (val translated = translator.translate(intent, targetOntologyIri, materializedElements, currentProject.graph)) {
            is com.entio.core.EntioResult.Failure -> throw WebWorkflowFailure("external-proposal-invalid", translated.message)
            is com.entio.core.EntioResult.Success -> Unit
        }
        return staging.stageExternal(
            projectId = projectId,
            sourceId = request.sourceId,
            targetOntologyIri = targetOntologyIri,
            intent = intent,
            summary = "${request.intentType}: ${elementLabel(element)}",
            userId = userId,
            idempotencyKey = request.idempotencyKey,
            materializedElements = materializedElements,
            existingGraph = currentProject.graph,
        )
    }

    private fun materializedElements(
        session: ExternalFiboCatalogSession,
        selected: ExternalCatalogElement,
        selectedDependencyIris: Set<String>,
    ): List<ExternalCatalogElement> {
        val descriptor = selected.descriptor.descriptor
        val dependencyIris = when (descriptor) {
            is com.entio.core.OntologyEntityDescriptor.Class -> descriptor.directSuperclasses
            is com.entio.core.OntologyEntityDescriptor.ObjectProperty -> descriptor.domains + descriptor.ranges
            is com.entio.core.OntologyEntityDescriptor.DatatypeProperty -> descriptor.domains + descriptor.datatypeRanges
            else -> emptyList()
        }
        return (listOf(selected) + dependencyIris
            .filter { it.value in selectedDependencyIris }
            .mapNotNull { session.find(it) })
            .distinctBy { it.descriptor.descriptor.common.entity.value }
    }

    private fun intent(
        request: WebFiboProposalRequest,
        element: ExternalCatalogElement,
        dependencies: List<ExternalDependency>,
    ): ExternalProposalIntent {
        val dependencySet = ExternalDependencySet(dependencies)
        return when (request.intentType.lowercase()) {
        "reuse-class" -> {
            if (element.kind != ExternalEntityKind.Class) throw WebWorkflowFailure("external-kind-mismatch", "The selected element is not a class.")
            ExternalProposalIntent.ReuseExternalClass(Iri(request.externalIri), Phase5PackageIdentity.SOURCE_ID, dependencySet)
        }
        "reuse-object-property" -> {
            if (element.kind != ExternalEntityKind.ObjectProperty) throw WebWorkflowFailure("external-kind-mismatch", "The selected element is not an object property.")
            ExternalProposalIntent.ReuseExternalObjectProperty(Iri(request.externalIri), Phase5PackageIdentity.SOURCE_ID, dependencySet)
        }
        "reuse-datatype-property" -> {
            if (element.kind != ExternalEntityKind.DatatypeProperty) throw WebWorkflowFailure("external-kind-mismatch", "The selected element is not a datatype property.")
            ExternalProposalIntent.ReuseExternalDatatypeProperty(Iri(request.externalIri), Phase5PackageIdentity.SOURCE_ID, dependencySet)
        }
        "create-local-subclass" -> ExternalProposalIntent.CreateLocalSubclassOfExternalClass(
            localClassIri = Iri(request.localClassIri ?: throw WebWorkflowFailure("missing-local-class-iri", "A local class IRI is required.")),
            externalSuperclassIri = Iri(request.externalIri),
            sourceId = Phase5PackageIdentity.SOURCE_ID,
            dependencies = dependencySet,
        )
        else -> throw WebWorkflowFailure("invalid-external-intent", "Unknown external proposal intent '${request.intentType}'.")
    }
    }

    private fun session(projectId: String): ExternalFiboCatalogSession {
        val loaded = catalogLoader.load(project(projectId))
        return when (loaded) {
            is com.entio.core.EntioResult.Failure -> throw WebWorkflowFailure("external-catalog-unavailable", loaded.message)
            is com.entio.core.EntioResult.Success -> loaded.value
        }
    }

    private fun project(projectId: String) = when (val loaded = projectLoader.loadProject(projectRegistry.rootFor(projectId))) {
        is com.entio.core.EntioResult.Failure -> throw WebWorkflowFailure("project-load-failed", loaded.message)
        is com.entio.core.EntioResult.Success -> loaded.value
    }

    private fun toElement(element: ExternalCatalogElement, session: ExternalFiboCatalogSession): WebFiboElement {
        val descriptor = element.descriptor.descriptor
        val common = descriptor.common
        val module = session.catalog.modules.firstOrNull { it.ontologyIri == element.descriptor.moduleIri }
        val parents = when (descriptor) {
            is OntologyEntityDescriptor.Class -> descriptor.directSuperclasses.map(Iri::value)
            else -> emptyList()
        }
        val domains = when (descriptor) {
            is OntologyEntityDescriptor.ObjectProperty -> descriptor.domains.map(Iri::value)
            is OntologyEntityDescriptor.DatatypeProperty -> descriptor.domains.map(Iri::value)
            else -> emptyList()
        }
        val ranges = when (descriptor) {
            is OntologyEntityDescriptor.ObjectProperty -> descriptor.ranges.map(Iri::value)
            is OntologyEntityDescriptor.DatatypeProperty -> descriptor.datatypeRanges.map(Iri::value)
            else -> emptyList()
        }
        return WebFiboElement(
            iri = common.entity.value,
            label = common.preferredLabel?.lexicalForm ?: readableExternalLabel(common.entity.value),
            kind = element.kind.name,
            moduleIri = element.descriptor.moduleIri.value,
            domain = element.descriptor.domain,
            maturity = element.descriptor.maturity.name,
            catalogStatus = element.descriptor.catalogStatus.name,
            sourcePath = module?.sourcePath.orEmpty(),
            alternateLabels = common.alternateLabels.map { it.lexicalForm },
            definitions = common.definitions.map { it.lexicalForm },
            parents = parents,
            domains = domains,
            ranges = ranges,
        )
    }

    private fun ExternalDependency.toWebDependency(session: ExternalFiboCatalogSession): WebFiboDependency {
        val label = externalIri?.let { iri ->
            session.find(iri)?.let(::elementLabel)
                ?: session.catalog.modules.firstOrNull { it.ontologyIri == iri }?.label
        }
        return WebFiboDependency(category.name, requirement.name, visibility.name, selection.name, reason, externalIri?.value, label)
    }

    private fun elementLabel(element: ExternalCatalogElement): String = element.descriptor.descriptor.common.preferredLabel?.lexicalForm
        ?: readableExternalLabel(element.descriptor.descriptor.common.entity.value)

    /** Keep catalog entries readable when the source omits an explicit label. */
    private fun readableExternalLabel(iri: String): String {
        val localName = iri.substringAfterLast('#', iri.substringAfterLast('/')).ifBlank { iri }
        return localName
            .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
            .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1 $2")
            .replace('_', ' ')
            .replace('-', ' ')
    }

    private fun WebPageRequest.page(): Int = if (offset == 0) 0 else offset / limit

    private companion object {
        fun defaultPackageRoot(): Path {
            val repositoryPath = Path.of("external-ontologies/fibo")
            return if (Files.isDirectory(repositoryPath)) repositoryPath else Path.of("..", "external-ontologies", "fibo")
        }
    }

}
