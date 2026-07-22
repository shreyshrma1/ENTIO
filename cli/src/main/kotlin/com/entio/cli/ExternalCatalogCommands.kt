package com.entio.cli

import com.entio.core.EntioProject
import com.entio.core.EntioResult
import com.entio.core.ExternalCatalogElement
import com.entio.core.ExternalConfidenceBand
import com.entio.core.ExternalDependency
import com.entio.core.ExternalDependencySelection
import com.entio.core.ExternalDependencySet
import com.entio.core.ExternalEntityKind
import com.entio.core.ExternalOntologyAvailability
import com.entio.core.ExternalOntologyMaturity
import com.entio.core.ExternalOntologyReference
import com.entio.core.ExternalProposalIntent
import com.entio.core.ExternalSchemaSearchQuery
import com.entio.core.ExternalSearchContext
import com.entio.core.Iri
import com.entio.core.Phase5PackageIdentity
import com.entio.core.ValidationIssue
import com.entio.core.ValidationSeverity
import com.entio.semantic.ExternalDependencyReviewer
import com.entio.semantic.ExternalFiboCatalogSession
import com.entio.semantic.ExternalProposalPreparer
import com.entio.semantic.FiboCatalogLoader
import com.entio.semantic.FiboSchemaSearchService
import com.entio.semantic.ProjectLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.Callable
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import picocli.CommandLine.Spec

private const val EXTERNAL_PACKAGE_DIRECTORY = "external-ontologies/fibo"

internal class ExternalCatalogCliSupport(
    private val projectLoader: ProjectLoader = ProjectLoader(),
    private val searchService: FiboSchemaSearchService = FiboSchemaSearchService(),
    private val dependencyReviewer: ExternalDependencyReviewer = ExternalDependencyReviewer(),
    private val proposalPreparer: ExternalProposalPreparer = ExternalProposalPreparer(),
) {
    fun loadSession(projectRoot: Path?): EntioResult<Pair<ExternalFiboCatalogSession, EntioProject?>> {
        val resolvedProjectRoot = projectRoot?.let(::resolveProjectRoot)
        val project = resolvedProjectRoot?.let {
            when (val result = projectLoader.loadProject(it)) {
                is EntioResult.Failure -> return result
                is EntioResult.Success -> result.value
            }
        }
        val packageRoot = findPackageRoot(projectRoot)
            ?: return failure("external-package-unavailable", "The approved FIBO package is not available in this repository.")
        return when (val result = FiboCatalogLoader(packageRoot).load(project)) {
            is EntioResult.Failure -> result
            is EntioResult.Success -> EntioResult.Success(result.value to project)
        }
    }

    fun search(session: ExternalFiboCatalogSession, query: ExternalSchemaSearchQuery) = searchService.search(session, query)

    fun review(
        session: ExternalFiboCatalogSession,
        element: ExternalCatalogElement,
        project: EntioProject?,
    ) = dependencyReviewer.review(session, element, project)

    fun prepare(
        project: EntioProject,
        targetSourceId: String,
        targetOntologyIri: Iri,
        intent: ExternalProposalIntent,
        proposalId: String,
        title: String,
    ) = proposalPreparer.prepare(
        project = project,
        targetSourceId = targetSourceId,
        targetOntologyIri = targetOntologyIri,
        intent = intent,
        id = proposalId,
        title = title,
    )

    private fun findPackageRoot(projectRoot: Path?): Path? {
        val candidates = buildList {
            projectRoot?.let { root ->
                generateSequence(root.toAbsolutePath().normalize()) { it.parent }
                    .forEach { add(it.resolve(EXTERNAL_PACKAGE_DIRECTORY)) }
            }
            generateSequence(Path.of("").toAbsolutePath().normalize()) { it.parent }
                .forEach { add(it.resolve(EXTERNAL_PACKAGE_DIRECTORY)) }
        }
        return candidates.firstOrNull { Files.isDirectory(it) }
    }

    private fun resolveProjectRoot(projectRoot: Path): Path {
        if (Files.exists(projectRoot.resolve("entio.yaml"))) return projectRoot
        val absolute = projectRoot.toAbsolutePath().normalize()
        if (Files.exists(absolute.resolve("entio.yaml"))) return absolute
        return generateSequence(Path.of("").toAbsolutePath().normalize()) { it.parent }
            .map { it.resolve(projectRoot).normalize() }
            .firstOrNull { Files.exists(it.resolve("entio.yaml")) }
            ?: projectRoot
    }

    companion object {
        fun failure(code: String, message: String): EntioResult.Failure = EntioResult.Failure(
            message = message,
            issues = listOf(ValidationIssue(ValidationSeverity.Error, code, message, "external-catalog")),
        )
    }
}

@Command(
    name = "external-sources",
    aliases = ["sources"],
    mixinStandardHelpOptions = true,
    description = ["List approved external ontology sources without changing the project."],
)
internal class ExternalSourcesCommand(
    private val support: ExternalCatalogCliSupport = ExternalCatalogCliSupport(),
) : Callable<Int> {
    @Spec
    private lateinit var spec: CommandSpec

    @Parameters(index = "0", arity = "0..1", paramLabel = "PROJECT_ROOT")
    private var projectRoot: String? = null

    override fun call(): Int = when (val result = support.loadSession(projectRoot?.let(Path::of))) {
        is EntioResult.Failure -> failure("external-sources", result)
        is EntioResult.Success -> {
            val session = result.value.first
            spec.commandLine().out.println(
                jsonObject(
                    "command" to "external-sources",
                    "ok" to true,
                    "sources" to jsonArray(listOf(externalSourceJson(session.source))),
                    "selection" to jsonObject("mode" to "read-only", "projectChanged" to false),
                ).encoded,
            )
            0
        }
    }

    private fun failure(command: String, result: EntioResult.Failure): Int = printExternalFailure(spec, command, result)
}

@Command(
    name = "external-manifest",
    aliases = ["manifest"],
    mixinStandardHelpOptions = true,
    description = ["Return the fixed FIBO package and catalog status."],
)
internal class ExternalManifestCommand(
    private val support: ExternalCatalogCliSupport = ExternalCatalogCliSupport(),
) : Callable<Int> {
    @Spec
    private lateinit var spec: CommandSpec

    @Parameters(index = "0", arity = "0..1", paramLabel = "PROJECT_ROOT")
    private var projectRoot: String? = null

    override fun call(): Int = when (val result = support.loadSession(projectRoot?.let(Path::of))) {
        is EntioResult.Failure -> printExternalFailure(spec, "external-manifest", result)
        is EntioResult.Success -> {
            val session = result.value.first
            spec.commandLine().out.println(
                jsonObject(
                    "command" to "external-manifest",
                    "ok" to true,
                    "source" to externalSourceJson(session.source),
                    "manifest" to externalManifestJson(session),
                    "catalog" to jsonObject(
                        "schema" to session.catalog.catalogSchema,
                        "elementCount" to session.catalog.elementCount,
                        "moduleCount" to session.catalog.modules.size,
                    ),
                    "package" to jsonObject("availability" to ExternalOntologyAvailability.Available.name.lowercase()),
                ).encoded,
            )
            0
        }
    }
}

@Command(
    name = "external-browse",
    aliases = ["browse"],
    mixinStandardHelpOptions = true,
    description = ["Browse the read-only FIBO catalog or curated Foundations modules."],
)
internal class ExternalBrowseCommand(
    private val support: ExternalCatalogCliSupport = ExternalCatalogCliSupport(),
) : Callable<Int> {
    @Spec
    private lateinit var spec: CommandSpec

    @Parameters(index = "0", paramLabel = "PROJECT_ROOT")
    private lateinit var projectRoot: String

    @Option(names = ["--mode"], defaultValue = "curated", description = ["Browse mode: curated, modules, or module."])
    private lateinit var mode: String

    @Option(names = ["--module-iri"], description = ["Module IRI for module mode."])
    private var moduleIri: String? = null

    @Option(names = ["--page"], defaultValue = "0")
    private var page: Int = 0

    @Option(names = ["--page-size"], defaultValue = "25")
    private var pageSize: Int = 25

    override fun call(): Int = when (val result = support.loadSession(Path.of(projectRoot))) {
        is EntioResult.Failure -> printExternalFailure(spec, "external-browse", result)
        is EntioResult.Success -> {
            val session = result.value.first
            val pageResult = try {
                when (mode.lowercase(Locale.ROOT)) {
                    "curated" -> session.browseCuratedModules(page, pageSize).let { externalPageJson(it, ::externalModuleJson) }
                    "modules" -> session.browseModules(page, pageSize).let { externalPageJson(it, ::externalModuleJson) }
                    "module" -> {
                        val iri = moduleIri?.takeIf(String::isNotBlank)
                            ?: return printExternalFailure(spec, "external-browse", ExternalCatalogCliSupport.failure("missing-module-iri", "Module mode requires --module-iri."))
                        session.browseModule(Iri(iri), page, pageSize).let { externalPageJson(it, ::externalElementJson) }
                    }
                    else -> return printExternalFailure(spec, "external-browse", ExternalCatalogCliSupport.failure("invalid-browse-mode", "Unknown browse mode '$mode'."))
                }
            } catch (exception: IllegalArgumentException) {
                return printExternalFailure(spec, "external-browse", ExternalCatalogCliSupport.failure("invalid-browse-page", exception.message ?: "Invalid browse page."))
            }
            spec.commandLine().out.println(jsonObject("command" to "external-browse", "ok" to true, "mode" to mode.lowercase(Locale.ROOT), "page" to pageResult).encoded)
            0
        }
    }
}

@Command(
    name = "external-describe",
    aliases = ["external-descriptor"],
    mixinStandardHelpOptions = true,
    description = ["Return an external FIBO semantic descriptor by IRI."],
)
internal class ExternalDescribeCommand(
    private val support: ExternalCatalogCliSupport = ExternalCatalogCliSupport(),
) : Callable<Int> {
    @Spec
    private lateinit var spec: CommandSpec

    @Parameters(index = "0", paramLabel = "PROJECT_ROOT")
    private lateinit var projectRoot: String

    @Parameters(index = "1", paramLabel = "ENTITY_IRI")
    private lateinit var entityIri: String

    @Option(names = ["--kind"])
    private var kind: String? = null

    override fun call(): Int {
        if (kind != null && parseExternalKind(kind) == null) {
            return printExternalFailure(spec, "external-describe", ExternalCatalogCliSupport.failure("invalid-external-kind", "Unknown external entity kind '$kind'."))
        }
        return when (val result = support.loadSession(Path.of(projectRoot))) {
            is EntioResult.Failure -> printExternalFailure(spec, "external-describe", result)
            is EntioResult.Success -> {
                val session = result.value.first
                val element = session.find(Iri(entityIri), parseExternalKind(kind))
                    ?: return printExternalFailure(spec, "external-describe", ExternalCatalogCliSupport.failure("external-element-not-found", "No external catalog element matched '$entityIri'."))
                spec.commandLine().out.println(jsonObject("command" to "external-describe", "ok" to true, "element" to externalElementJson(element)).encoded)
                0
            }
        }
    }
}

@Command(
    name = "external-search",
    aliases = ["external-schema-search"],
    mixinStandardHelpOptions = true,
    description = ["Search the pinned FIBO catalog using the deterministic Kotlin search service."],
)
internal class ExternalSearchCommand(
    private val support: ExternalCatalogCliSupport = ExternalCatalogCliSupport(),
) : Callable<Int> {
    @Spec
    private lateinit var spec: CommandSpec

    @Parameters(index = "0", paramLabel = "PROJECT_ROOT")
    private lateinit var projectRoot: String

    @Parameters(index = "1", paramLabel = "QUERY")
    private lateinit var queryText: String

    @Option(names = ["--kind"])
    private var kind: String? = null

    @Option(names = ["--module-iri"])
    private var moduleIri: String? = null

    @Option(names = ["--domain"])
    private var domain: String? = null

    @Option(names = ["--curated-only"])
    private var curatedOnly: Boolean = false

    @Option(names = ["--include-informative"])
    private var includeInformative: Boolean = false

    @Option(names = ["--parent-iri"])
    private var parentIri: String? = null

    @Option(names = ["--domain-iri"])
    private var domainIri: String? = null

    @Option(names = ["--range-iri"])
    private var rangeIri: String? = null

    @Option(names = ["--parent-required"])
    private var parentRequired: Boolean = false

    @Option(names = ["--domain-required"])
    private var domainRequired: Boolean = false

    @Option(names = ["--range-required"])
    private var rangeRequired: Boolean = false

    @Option(names = ["--minimum-score"], defaultValue = "20")
    private var minimumScore: Int = 20

    @Option(names = ["--page"], defaultValue = "0")
    private var page: Int = 0

    @Option(names = ["--page-size"], defaultValue = "25")
    private var pageSize: Int = 25

    override fun call(): Int {
        val externalKind = parseExternalKind(kind)
        if (kind != null && externalKind == null) return printExternalFailure(spec, "external-search", ExternalCatalogCliSupport.failure("invalid-external-kind", "Unknown external entity kind '$kind'."))
        val query = try {
            ExternalSchemaSearchQuery(
                text = queryText,
                kind = externalKind,
                moduleIri = moduleIri?.let(::Iri),
                domain = domain,
                curatedOnly = curatedOnly,
                includeInformative = includeInformative,
                context = ExternalSearchContext(
                    parentIri = parentIri?.let(::Iri),
                    domainIri = domainIri?.let(::Iri),
                    rangeIri = rangeIri?.let(::Iri),
                    parentRequired = parentRequired,
                    domainRequired = domainRequired,
                    rangeRequired = rangeRequired,
                ),
                minimumScore = minimumScore,
                pageSize = pageSize,
                page = page,
            )
        } catch (exception: IllegalArgumentException) {
            return printExternalFailure(spec, "external-search", ExternalCatalogCliSupport.failure("invalid-search-query", exception.message ?: "Invalid external search query."))
        }
        return when (val result = support.loadSession(Path.of(projectRoot))) {
            is EntioResult.Failure -> printExternalFailure(spec, "external-search", result)
            is EntioResult.Success -> {
                val response = support.search(result.value.first, query)
                spec.commandLine().out.println(
                    jsonObject(
                        "command" to "external-search",
                        "ok" to true,
                        "schema" to "fibo-schema-search-v1",
                        "query" to queryText,
                        "totalResultCount" to response.totalResultCount,
                        "page" to response.page,
                        "pageSize" to response.pageSize,
                        "hasNext" to response.hasNext,
                        "cursor" to response.page,
                        "noSilentTruncation" to true,
                        "candidates" to jsonArray(response.candidates.mapIndexed { index, candidate -> externalCandidateJson(candidate, index + 1) }),
                    ).encoded,
                )
                0
            }
        }
    }
}

@Command(
    name = "external-dependencies",
    aliases = ["external-dependency-review"],
    mixinStandardHelpOptions = true,
    description = ["Inspect explicit dependencies for one external FIBO element."],
)
internal class ExternalDependenciesCommand(
    private val support: ExternalCatalogCliSupport = ExternalCatalogCliSupport(),
) : Callable<Int> {
    @Spec
    private lateinit var spec: CommandSpec

    @Parameters(index = "0", paramLabel = "PROJECT_ROOT")
    private lateinit var projectRoot: String

    @Parameters(index = "1", paramLabel = "ENTITY_IRI")
    private lateinit var entityIri: String

    @Option(names = ["--kind"])
    private var kind: String? = null

    override fun call(): Int {
        if (kind != null && parseExternalKind(kind) == null) {
            return printExternalFailure(spec, "external-dependencies", ExternalCatalogCliSupport.failure("invalid-external-kind", "Unknown external entity kind '$kind'."))
        }
        return when (val result = support.loadSession(Path.of(projectRoot))) {
            is EntioResult.Failure -> printExternalFailure(spec, "external-dependencies", result)
            is EntioResult.Success -> {
                val session = result.value.first
                val element = session.find(Iri(entityIri), parseExternalKind(kind))
                    ?: return printExternalFailure(spec, "external-dependencies", ExternalCatalogCliSupport.failure("external-element-not-found", "No external catalog element matched '$entityIri'."))
                val dependencies = support.review(session, element, result.value.second)
                spec.commandLine().out.println(
                    jsonObject(
                        "command" to "external-dependencies",
                        "ok" to true,
                        "element" to externalElementJson(element),
                        "dependencySet" to externalDependencySetJson(dependencies),
                        "requiresExplicitApproval" to dependencies.requiredUserVisibleDependencies.any { it.selection == ExternalDependencySelection.Missing },
                    ).encoded,
                )
                if (dependencies.requiredUserVisibleDependencies.any { it.selection == ExternalDependencySelection.Missing }) 1 else 0
            }
        }
    }
}

@Command(
    name = "external-proposal",
    aliases = ["external-proposal-preview"],
    mixinStandardHelpOptions = true,
    description = ["Prepare an external reuse or local-extension proposal without writing project files."],
)
internal class ExternalProposalCommand(
    private val support: ExternalCatalogCliSupport = ExternalCatalogCliSupport(),
) : Callable<Int> {
    @Spec
    private lateinit var spec: CommandSpec

    @Parameters(index = "0", paramLabel = "PROJECT_ROOT")
    private lateinit var projectRoot: String

    @Parameters(index = "1", paramLabel = "TARGET_SOURCE_ID")
    private lateinit var targetSourceId: String

    @Parameters(index = "2", paramLabel = "TARGET_ONTOLOGY_IRI")
    private lateinit var targetOntologyIri: String

    @Option(names = ["--intent"], required = true, description = ["Intent: reuse-class, reuse-object-property, reuse-datatype-property, local-subclass, or add-reference."])
    private lateinit var intentKind: String

    @Option(names = ["--external-iri"], description = ["External element IRI."])
    private var externalIri: String? = null

    @Option(names = ["--kind"], description = ["External element kind for dependency review."])
    private var kind: String? = null

    @Option(names = ["--local-class-iri"], description = ["IRI for a new local subclass."])
    private var localClassIri: String? = null

    @Option(names = ["--select-dependency"], split = ",", description = ["Explicit dependency key(s) approved by the user."])
    private var selectedDependencyKeys: List<String> = emptyList()

    @Option(names = ["--proposal-id"], defaultValue = "external-cli-proposal")
    private lateinit var proposalId: String

    @Option(names = ["--title"], defaultValue = "External ontology proposal")
    private lateinit var title: String

    override fun call(): Int {
        val root = Path.of(projectRoot)
        return when (val result = support.loadSession(root)) {
            is EntioResult.Failure -> printExternalFailure(spec, "external-proposal", result)
            is EntioResult.Success -> prepareProposal(result.value.first, result.value.second ?: return printExternalFailure(spec, "external-proposal", ExternalCatalogCliSupport.failure("missing-project", "External proposals require a loaded Entio project.")))
        }
    }

    private fun prepareProposal(session: ExternalFiboCatalogSession, project: EntioProject): Int {
        val external = externalIri?.takeIf(String::isNotBlank)?.let(::Iri)
        val externalKind = parseExternalKind(kind)
        if (kind != null && externalKind == null) {
            return printExternalFailure(spec, "external-proposal", ExternalCatalogCliSupport.failure("invalid-external-kind", "Unknown external entity kind '$kind'."))
        }
        val selectedElement = external?.let { session.find(it, externalKind) }
        if (intentKind.lowercase(Locale.ROOT) != "add-reference" && selectedElement == null) {
            return printExternalFailure(spec, "external-proposal", ExternalCatalogCliSupport.failure("external-element-not-found", "A matching external element is required for this intent."))
        }
        val dependencySet = selectedElement?.let { element ->
            val reviewed = support.review(session, element, project)
            ExternalDependencySet(reviewed.dependencies.map { dependency ->
                val key = dependencyKey(dependency)
                if (key in selectedDependencyKeys && dependency.selection == ExternalDependencySelection.Missing) {
                    dependency.copy(selection = ExternalDependencySelection.NewlySelected)
                } else dependency
            }, reviewed.status)
        } ?: ExternalDependencySet()
        val intent = when (intentKind.lowercase(Locale.ROOT)) {
            "reuse-class" -> ExternalProposalIntent.ReuseExternalClass(requireNotNull(external), Phase5PackageIdentity.SOURCE_ID, dependencySet)
            "reuse-object-property" -> ExternalProposalIntent.ReuseExternalObjectProperty(requireNotNull(external), Phase5PackageIdentity.SOURCE_ID, dependencySet)
            "reuse-datatype-property" -> ExternalProposalIntent.ReuseExternalDatatypeProperty(requireNotNull(external), Phase5PackageIdentity.SOURCE_ID, dependencySet)
            "local-subclass" -> ExternalProposalIntent.CreateLocalSubclassOfExternalClass(
                localClassIri = localClassIri?.takeIf(String::isNotBlank)?.let(::Iri)
                    ?: return printExternalFailure(spec, "external-proposal", ExternalCatalogCliSupport.failure("missing-local-class-iri", "local-subclass requires --local-class-iri.")),
                externalSuperclassIri = requireNotNull(external),
                sourceId = Phase5PackageIdentity.SOURCE_ID,
                dependencies = dependencySet,
            )
            "add-reference" -> ExternalProposalIntent.AddExternalOntologyReference(
                reference = ExternalOntologyReference(
                    source = Phase5PackageIdentity.SOURCE_ID,
                    release = session.manifest.release,
                    commitSha = session.manifest.commitSha,
                    packageFingerprint = session.manifest.packageFingerprint,
                    modules = session.manifest.curatedSeedOntologyIris,
                ),
                sourceId = Phase5PackageIdentity.SOURCE_ID,
                dependencies = dependencySet,
            )
            else -> return printExternalFailure(spec, "external-proposal", ExternalCatalogCliSupport.failure("invalid-external-intent", "Unknown external proposal intent '$intentKind'."))
        }
        return when (val prepared = support.prepare(project, targetSourceId, Iri(targetOntologyIri), intent, proposalId, title)) {
            is EntioResult.Failure -> printExternalFailure(spec, "external-proposal", prepared)
            is EntioResult.Success -> {
                val proposal = prepared.value
                spec.commandLine().out.println(
                    jsonObject(
                        "command" to "external-proposal",
                        "ok" to true,
                        "readOnly" to true,
                        "proposal" to jsonObject(
                            "id" to proposal.id,
                            "title" to proposal.title,
                            "targetSourceId" to proposal.targetSourceId,
                            "status" to proposal.status.name.lowercase(),
                            "changeCount" to proposal.changeSet.changes.size,
                            "baseline" to jsonObject(
                                "projectFingerprint" to proposal.baseline.projectFingerprint,
                                "targetSourceFingerprint" to proposal.baseline.targetSourceFingerprint,
                                "graphFingerprint" to proposal.baseline.graphFingerprint,
                            ),
                            "preview" to jsonObject("tripleCount" to proposal.preview?.graph?.triples?.size),
                        ),
                        "dependencySet" to externalDependencySetJson(dependencySet),
                    ).encoded,
                )
                0
            }
        }
    }

    private fun dependencyKey(dependency: ExternalDependency): String = listOf(
        dependency.category.name,
        dependency.externalIri?.value.orEmpty(),
        dependency.sourceModule?.value.orEmpty(),
    ).joinToString("|")
}

private fun parseExternalKind(value: String?): ExternalEntityKind? = value?.let { requested ->
    ExternalEntityKind.entries.firstOrNull { it.name.equals(requested, ignoreCase = true) }
}

private fun printExternalFailure(spec: CommandSpec, command: String, result: EntioResult.Failure): Int {
    spec.commandLine().out.println(
        jsonObject(
            "command" to command,
            "ok" to false,
            "error" to jsonObject(
                "message" to result.message,
                "issues" to jsonArray(result.issues.map(::validationIssueJson)),
            ),
        ).encoded,
    )
    return 1
}

private fun externalSourceJson(source: com.entio.core.ExternalOntologySource): JsonFragment = jsonObject(
    "id" to source.id,
    "displayName" to source.displayName,
    "version" to source.version,
    "description" to source.description,
    "availability" to source.availability.name.lowercase(),
    "curatedPackageId" to source.curatedPackageId,
    "catalogId" to source.catalogId,
    "attribution" to source.attribution,
)

private fun externalManifestJson(session: ExternalFiboCatalogSession): JsonFragment = jsonObject(
    "sourceId" to session.manifest.sourceId,
    "release" to session.manifest.release,
    "commitSha" to session.manifest.commitSha,
    "packageSchema" to session.manifest.packageSchema,
    "catalogSchema" to session.manifest.catalogSchema,
    "checksumAlgorithm" to session.manifest.checksumAlgorithm,
    "commonsVersion" to session.manifest.commonsVersion,
    "packageFingerprint" to jsonObject(
        "algorithm" to session.manifest.packageFingerprint.algorithm,
        "value" to session.manifest.packageFingerprint.value,
    ),
    "curatedSeedOntologyIris" to jsonArray(session.manifest.curatedSeedOntologyIris.map(Iri::value)),
    "attributionComplete" to session.manifest.attributionComplete,
)

private fun externalModuleJson(module: com.entio.core.ExternalOntologyModule): JsonFragment = jsonObject(
    "ontologyIri" to module.ontologyIri.value,
    "label" to module.label,
    "domain" to module.domain,
    "sourcePath" to module.sourcePath,
    "maturity" to module.maturity.name,
    "curated" to module.curated,
    "importedOntologyIris" to jsonArray(module.importedOntologyIris.map(Iri::value)),
)

private fun externalElementJson(element: ExternalCatalogElement): JsonFragment = jsonObject(
    "kind" to element.kind.name,
    "descriptor" to jsonObject(
        "semantic" to semanticDescriptorJson(element.descriptor.descriptor),
        "sourceId" to element.descriptor.sourceId,
        "release" to element.descriptor.release,
        "moduleIri" to element.descriptor.moduleIri.value,
        "domain" to element.descriptor.domain,
        "maturity" to element.descriptor.maturity.name,
        "locality" to element.descriptor.locality.name,
        "catalogStatus" to element.descriptor.catalogStatus.name,
    ),
)

private fun externalCandidateJson(candidate: com.entio.core.ExternalSchemaCandidate, rank: Int): JsonFragment = jsonObject(
    "rank" to rank,
    "scoreModel" to candidate.scoreModel,
    "score" to candidate.score.total,
    "scoreBreakdown" to jsonObject(
        "nameOrIri" to candidate.score.nameOrIri,
        "definition" to candidate.score.definition,
        "semanticContext" to candidate.score.semanticContext,
        "catalogStatus" to candidate.score.catalogStatus,
        "localProjectRelevance" to candidate.score.localProjectRelevance,
    ),
    "confidence" to candidate.confidence.name,
    "adjustedConfidenceBand" to candidate.confidence.name,
    "tieGroupId" to candidate.tieGroupId,
    "tieGroup" to candidate.tieGroupId?.let { jsonObject("id" to it, "ambiguous" to true) },
    "totalResultCount" to candidate.totalResultCount,
    "page" to candidate.page,
    "pageSize" to candidate.pageSize,
    "reasons" to jsonArray(candidate.reasons.map { reason ->
        jsonObject(
            "type" to reason.type.name,
            "points" to reason.points,
            "matchedField" to reason.matchedField,
            "matchedText" to reason.matchedText,
            "relatedIri" to reason.relatedIri?.value,
        )
    }),
    "element" to externalElementJson(com.entio.core.ExternalCatalogElement(candidate.descriptor, candidate.kind)),
)

private fun externalDependencySetJson(set: ExternalDependencySet): JsonFragment = jsonObject(
    "status" to set.status.name,
    "dependencyCount" to set.dependencies.size,
    "requiredUserVisibleCount" to set.requiredUserVisibleDependencies.size,
    "dependencies" to jsonArray(set.dependencies.map { dependency ->
        jsonObject(
            "key" to listOf(dependency.category.name, dependency.externalIri?.value.orEmpty(), dependency.sourceModule?.value.orEmpty()).joinToString("|"),
            "category" to dependency.category.name,
            "requirement" to dependency.requirement.name,
            "closure" to dependency.closure.name,
            "visibility" to dependency.visibility.name,
            "selection" to dependency.selection.name,
            "reason" to dependency.reason,
            "externalIri" to dependency.externalIri?.value,
            "sourceModule" to dependency.sourceModule?.value,
            "maturity" to dependency.maturity.name,
            "packageAvailable" to dependency.packageAvailable,
        )
    }),
)

private fun <T> externalPageJson(page: com.entio.semantic.ExternalCatalogPage<T>, itemJson: (T) -> JsonFragment): JsonFragment = jsonObject(
    "items" to jsonArray(page.items.map(itemJson)),
    "totalCount" to page.totalCount,
    "page" to page.page,
    "pageSize" to page.pageSize,
    "hasNext" to page.hasNext,
    "cursor" to page.page,
    "noSilentTruncation" to true,
)
