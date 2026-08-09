package com.entio.cli

import com.entio.core.DomainModelingIntent
import com.entio.core.DomainOntologyAvailability
import com.entio.core.DomainOntologyProfileIdentity
import com.entio.core.DomainOperationKind
import com.entio.core.DomainRecommendation
import com.entio.core.DomainReuseAction
import com.entio.core.DomainReuseCustomization
import com.entio.core.EntioProject
import com.entio.core.EntioResult
import com.entio.core.ExternalEntityKind
import com.entio.core.Iri
import com.entio.semantic.DomainProfileService
import com.entio.semantic.DomainMigrationPreview
import com.entio.semantic.DomainMigrationReport
import com.entio.semantic.DomainMigrationService
import com.entio.semantic.DomainRecommendationService
import com.entio.semantic.DomainRecommendationFingerprints
import com.entio.semantic.DomainReusePreparationRequest
import com.entio.semantic.DomainReuseService
import com.entio.semantic.ProjectLoader
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.Callable
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import picocli.CommandLine.Spec

/** Shared path and project adapter for the thin Phase 13 CLI commands. */
internal class DomainOntologyCliSupport(
    private val projectLoader: ProjectLoader = ProjectLoader(),
    private val profiles: DomainProfileService = DomainProfileService(),
) {
    fun status(projectRoot: Path) = profiles.status(resolveProjectRoot(projectRoot))

    fun loadProject(projectRoot: Path): EntioResult<Pair<Path, EntioProject>> {
        val root = resolveProjectRoot(projectRoot)
        return when (val loaded = projectLoader.loadProject(root)) {
            is EntioResult.Failure -> loaded
            is EntioResult.Success -> EntioResult.Success(root to loaded.value)
        }
    }

    fun activationPreview(projectRoot: Path) = profiles.previewActivation(resolveProjectRoot(projectRoot))

    fun loadActiveProject(projectRoot: Path): EntioResult<Pair<Path, EntioProject>> {
        val root = resolveProjectRoot(projectRoot)
        val status = profiles.status(root)
        if (status.availability != DomainOntologyAvailability.Active) {
            return failure("domain-profile-inactive", "Activate the approved domain ontology before using this command.")
        }
        return when (val loaded = projectLoader.loadProject(root)) {
            is EntioResult.Failure -> loaded
            is EntioResult.Success -> EntioResult.Success(root to loaded.value)
        }
    }

    fun recommendationService(): DomainRecommendationService {
        val repository = repositoryRoot()
        return DomainRecommendationService.open(
            repository.resolve("external-ontologies/domain-search/fibo/master_2026Q2"),
            repository.resolve("external-ontologies/domain-search/models/all-MiniLM-L6-v2"),
        )
    }

    fun reuseService(): DomainReuseService = DomainReuseService.open(
        repositoryRoot().resolve("external-ontologies/domain-search/fibo/master_2026Q2"),
    )

    fun migrationService(): DomainMigrationService = DomainMigrationService.open(
        repositoryRoot().resolve("external-ontologies/domain-search/fibo/master_2026Q2"),
        profiles,
    )

    fun intent(
        root: Path,
        project: EntioProject,
        service: DomainRecommendationService,
        query: String,
        kind: ExternalEntityKind?,
        broadSearch: Boolean,
    ): DomainModelingIntent = DomainModelingIntent(
        projectId = root.fileName.toString(),
        operationKind = DomainOperationKind.GlobalSemanticSearch,
        requestedKind = kind,
        draftLabel = query,
        alreadyReusedIris = project.graph.triples.flatMap { triple ->
            listOfNotNull(triple.subjectResource as? Iri, triple.objectTerm as? Iri)
        }.filter { it.value.startsWith(FIBO_PREFIX) || it.value.startsWith(COMMONS_PREFIX) }.toSet(),
        projectFingerprint = sha256(Files.readAllBytes(root.resolve("entio.yaml"))),
        profileFingerprint = sha256(Files.readAllBytes(root.resolve(DomainOntologyProfileIdentity.PROFILE_PATH))),
        ontologyFingerprint = sha256(
            project.graph.triples.map { it.toString() }.sorted().joinToString("\n").toByteArray(Charsets.UTF_8),
        ),
        currentWorkFingerprint = "cli-no-open-work",
        packageFingerprint = service.packageFingerprint(),
        indexFingerprint = service.indexFingerprint(),
        broadSearch = broadSearch,
    )

    private fun resolveProjectRoot(projectRoot: Path): Path {
        if (Files.exists(projectRoot.resolve("entio.yaml"))) return projectRoot.toAbsolutePath().normalize()
        return generateSequence(Path.of("").toAbsolutePath().normalize()) { it.parent }
            .map { it.resolve(projectRoot).normalize() }
            .firstOrNull { Files.exists(it.resolve("entio.yaml")) }
            ?: projectRoot.toAbsolutePath().normalize()
    }

    private fun repositoryRoot(): Path = generateSequence(Path.of("").toAbsolutePath().normalize()) { it.parent }
        .firstOrNull { Files.isDirectory(it.resolve("external-ontologies/domain-search")) }
        ?: error("domain-assets-unavailable")

    companion object {
        fun failure(code: String, message: String): EntioResult.Failure = EntioResult.Failure(
            message,
            listOf(com.entio.core.ValidationIssue(com.entio.core.ValidationSeverity.Error, code, message, "domain-ontology")),
        )

        private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }

        private const val FIBO_PREFIX = "https://spec.edmcouncil.org/fibo/ontology/"
        private const val COMMONS_PREFIX = "https://www.omg.org/spec/Commons/"
    }
}

@Command(name = "domain-migration", mixinStandardHelpOptions = true)
internal class DomainMigrationCommand(
    private val support: DomainOntologyCliSupport = DomainOntologyCliSupport(),
) : Callable<Int> {
    @Spec private lateinit var spec: CommandSpec
    @Parameters(index = "0") private lateinit var projectRoot: String

    override fun call(): Int = when (val loaded = support.loadProject(Path.of(projectRoot))) {
        is EntioResult.Failure -> printDomainFailure(spec, "domain-migration", loaded)
        is EntioResult.Success -> {
            val report = support.migrationService().detect(loaded.value.second)
            spec.commandLine().out.println(domainMigrationJson("domain-migration", report, null).encoded)
            0
        }
    }
}

@Command(name = "domain-migration-preview", mixinStandardHelpOptions = true)
internal class DomainMigrationPreviewCommand(
    private val support: DomainOntologyCliSupport = DomainOntologyCliSupport(),
) : Callable<Int> {
    @Spec private lateinit var spec: CommandSpec
    @Parameters(index = "0") private lateinit var projectRoot: String

    override fun call(): Int = when (val loaded = support.loadProject(Path.of(projectRoot))) {
        is EntioResult.Failure -> printDomainFailure(spec, "domain-migration-preview", loaded)
        is EntioResult.Success -> when (
            val preview = support.migrationService().preview(loaded.value.first, loaded.value.second)
        ) {
            is EntioResult.Failure -> printDomainFailure(spec, "domain-migration-preview", preview)
            is EntioResult.Success -> {
                spec.commandLine().out.println(
                    domainMigrationJson("domain-migration-preview", preview.value.report, preview.value).encoded,
                )
                0
            }
        }
    }
}

@Command(name = "domain-profile-status", mixinStandardHelpOptions = true)
internal class DomainProfileStatusCommand(
    private val support: DomainOntologyCliSupport = DomainOntologyCliSupport(),
) : Callable<Int> {
    @Spec private lateinit var spec: CommandSpec
    @Parameters(index = "0") private lateinit var projectRoot: String

    override fun call(): Int {
        val status = support.status(Path.of(projectRoot))
        spec.commandLine().out.println(
            jsonObject(
                "command" to "domain-profile-status",
                "ok" to (status.availability != DomainOntologyAvailability.Invalid &&
                    status.availability != DomainOntologyAvailability.Stale &&
                    status.availability != DomainOntologyAvailability.RecoveryRequired),
                "availability" to status.availability.name.lowercase(),
                "selected" to (status.availability == DomainOntologyAvailability.Active),
                "profile" to status.profile?.let(::domainProfileJson),
                "issues" to jsonArray(status.issues.map { jsonObject("code" to it.code, "message" to it.message, "path" to it.path) }),
            ).encoded,
        )
        return if (status.availability in setOf(
                DomainOntologyAvailability.Invalid,
                DomainOntologyAvailability.Stale,
                DomainOntologyAvailability.RecoveryRequired,
            )
        ) 1 else 0
    }
}

@Command(name = "domain-sources", mixinStandardHelpOptions = true)
internal class DomainSourcesCommand(
    private val support: DomainOntologyCliSupport = DomainOntologyCliSupport(),
) : Callable<Int> {
    @Spec private lateinit var spec: CommandSpec

    override fun call(): Int = support.recommendationService().use { service ->
        spec.commandLine().out.println(jsonObject(
            "command" to "domain-sources",
            "ok" to true,
            "sources" to jsonArray(listOf(jsonObject(
                "sourceId" to DomainOntologyProfileIdentity.SOURCE_ID,
                "displayName" to "Financial Industry Business Ontology (FIBO)",
                "release" to DomainOntologyProfileIdentity.RELEASE,
                "packageFingerprint" to service.packageFingerprint(),
                "retrievalAvailability" to service.retrievalAvailability().name,
                "selectable" to (service.retrievalAvailability() != com.entio.core.DomainRetrievalAvailability.Unavailable),
            ))),
        ).encoded)
        0
    }
}

@Command(name = "domain-activation-preview", mixinStandardHelpOptions = true)
internal class DomainActivationPreviewCommand(
    private val support: DomainOntologyCliSupport = DomainOntologyCliSupport(),
) : Callable<Int> {
    @Spec private lateinit var spec: CommandSpec
    @Parameters(index = "0") private lateinit var projectRoot: String

    override fun call(): Int = when (val preview = support.activationPreview(Path.of(projectRoot))) {
        is EntioResult.Failure -> printDomainFailure(spec, "domain-activation-preview", preview)
        is EntioResult.Success -> {
            spec.commandLine().out.println(jsonObject(
                "command" to "domain-activation-preview",
                "ok" to true,
                "readOnly" to true,
                "profile" to domainProfileJson(preview.value.profile),
                "profilePath" to preview.value.profilePath,
                "managedSourcePath" to preview.value.managedSourcePath,
                "changesProjectOntology" to preview.value.changesProjectOntology,
            ).encoded)
            0
        }
    }
}

@Command(name = "domain-foundation", mixinStandardHelpOptions = true)
internal class DomainFoundationCommand(
    private val support: DomainOntologyCliSupport = DomainOntologyCliSupport(),
) : Callable<Int> {
    @Spec private lateinit var spec: CommandSpec
    @Parameters(index = "0") private lateinit var projectRoot: String

    override fun call(): Int = when (val loaded = support.loadActiveProject(Path.of(projectRoot))) {
        is EntioResult.Failure -> printDomainFailure(spec, "domain-foundation", loaded)
        is EntioResult.Success -> support.recommendationService().use { service ->
            spec.commandLine().out.println(jsonObject(
                "command" to "domain-foundation",
                "ok" to true,
                "availability" to service.retrievalAvailability().name.lowercase(),
                "groups" to jsonArray(service.foundations().map { group -> jsonObject(
                    "groupId" to group.groupId,
                    "label" to group.label,
                    "members" to jsonArray(group.members.map { member -> jsonObject(
                        "elementId" to member.elementId,
                        "iri" to member.iri.value,
                        "label" to member.label,
                        "kind" to member.kind.name,
                        "sourceFamily" to member.sourceFamily,
                    ) }),
                ) }),
            ).encoded)
            0
        }
    }
}

@Command(name = "domain-foundation-plan", mixinStandardHelpOptions = true)
internal class DomainFoundationPlanCommand(
    private val support: DomainOntologyCliSupport = DomainOntologyCliSupport(),
) : Callable<Int> {
    @Spec private lateinit var spec: CommandSpec
    @Parameters(index = "0") private lateinit var projectRoot: String
    @Option(names = ["--element-id"], split = ",") private var elementIds: Set<String> = emptySet()
    @Option(names = ["--all"]) private var selectAll: Boolean = false

    override fun call(): Int = when (val loaded = support.loadActiveProject(Path.of(projectRoot))) {
        is EntioResult.Failure -> printDomainFailure(spec, "domain-foundation-plan", loaded)
        is EntioResult.Success -> support.recommendationService().use { service ->
            try {
                val (root, project) = loaded.value
                val intent = support.intent(root, project, service, "foundation", null, false)
                val plan = service.planFoundation(
                    userId = "cli",
                    projectId = root.fileName.toString(),
                    selectedElementIds = elementIds,
                    selectAll = selectAll,
                    alreadyPresentIris = intent.alreadyReusedIris,
                    fingerprints = DomainRecommendationFingerprints(
                        intent.projectFingerprint,
                        intent.profileFingerprint,
                        intent.ontologyFingerprint,
                        intent.currentWorkFingerprint,
                        intent.packageFingerprint,
                        intent.indexFingerprint,
                    ),
                )
                spec.commandLine().out.println(jsonObject(
                    "command" to "domain-foundation-plan",
                    "ok" to true,
                    "readOnly" to true,
                    "planId" to plan.planId,
                    "explicitSelectionCount" to plan.explicitSelectionCount,
                    "dependencyCount" to plan.dependencyCount,
                    "batches" to jsonArray(plan.batches.map { batch -> jsonObject(
                        "batchNumber" to batch.batchNumber,
                        "explicitSelectionCount" to batch.explicitSelectionCount,
                        "items" to jsonArray(batch.items.map { item -> jsonObject(
                            "iri" to item.iri.value,
                            "label" to item.label,
                            "kind" to item.kind.name,
                            "role" to item.role.name,
                        ) }),
                    ) }),
                ).encoded)
                0
            } catch (failure: IllegalArgumentException) {
                printDomainFailure(spec, "domain-foundation-plan", DomainOntologyCliSupport.failure(
                    failure.message?.substringBefore(':') ?: "invalid-domain-foundation-selection",
                    failure.message ?: "The foundation selection is invalid.",
                ))
            }
        }
    }
}

@Command(name = "domain-recommendations", aliases = ["domain-search"], mixinStandardHelpOptions = true)
internal class DomainRecommendationsCommand(
    private val support: DomainOntologyCliSupport = DomainOntologyCliSupport(),
) : Callable<Int> {
    @Spec private lateinit var spec: CommandSpec
    @Parameters(index = "0") private lateinit var projectRoot: String
    @Parameters(index = "1") private lateinit var query: String
    @Option(names = ["--kind"]) private var kind: String? = null
    @Option(names = ["--broad-search"]) private var broadSearch: Boolean = false

    override fun call(): Int {
        val requestedKind = kind?.let { value -> ExternalEntityKind.entries.firstOrNull { it.name.equals(value, true) } }
        if (kind != null && requestedKind == null) {
            return printDomainFailure(spec, "domain-recommendations", DomainOntologyCliSupport.failure("invalid-domain-kind", "Unknown domain entity kind '$kind'."))
        }
        return when (val loaded = support.loadActiveProject(Path.of(projectRoot))) {
            is EntioResult.Failure -> printDomainFailure(spec, "domain-recommendations", loaded)
            is EntioResult.Success -> support.recommendationService().use { service ->
                val (root, project) = loaded.value
                val result = service.recommend("cli", support.intent(root, project, service, query, requestedKind, broadSearch))
                spec.commandLine().out.println(jsonObject(
                    "command" to "domain-recommendations",
                    "ok" to true,
                    "query" to query,
                    "availability" to result.availability.name.lowercase(),
                    "noConfidentMatch" to result.noConfidentMatch,
                    "intentFingerprint" to result.normalizedIntentFingerprint,
                    "recommendations" to jsonArray(result.recommendations.map(::domainRecommendationJson)),
                ).encoded)
                0
            }
        }
    }
}

@Command(name = "domain-describe", mixinStandardHelpOptions = true)
internal class DomainDescribeCommand(
    private val support: DomainOntologyCliSupport = DomainOntologyCliSupport(),
) : Callable<Int> {
    @Spec private lateinit var spec: CommandSpec
    @Parameters(index = "0") private lateinit var projectRoot: String
    @Parameters(index = "1") private lateinit var iri: String

    override fun call(): Int = when (val loaded = support.loadActiveProject(Path.of(projectRoot))) {
        is EntioResult.Failure -> printDomainFailure(spec, "domain-describe", loaded)
        is EntioResult.Success -> when (val described = support.reuseService().describe(Iri(iri), loaded.value.second.graph)) {
            is EntioResult.Failure -> printDomainFailure(spec, "domain-describe", described)
            is EntioResult.Success -> {
                val difference = described.value
                spec.commandLine().out.println(jsonObject(
                    "command" to "domain-describe",
                    "ok" to true,
                    "entityId" to difference.entityId,
                    "iri" to difference.canonicalIri.value,
                    "kind" to difference.sourceSnapshot.kind.name,
                    "sourceOntologyIri" to difference.sourceSnapshot.sourceOntologyIri.value,
                    "sourcePath" to difference.sourceSnapshot.sourcePath,
                    "sourceStatementCount" to difference.sourceSnapshot.statements.size,
                    "projectStatementCount" to difference.projectStatements.size,
                    "classification" to difference.classification.name,
                ).encoded)
                0
            }
        }
    }
}

@Command(name = "domain-dependencies", mixinStandardHelpOptions = true)
internal class DomainDependenciesCommand(
    private val support: DomainOntologyCliSupport = DomainOntologyCliSupport(),
) : Callable<Int> {
    @Spec private lateinit var spec: CommandSpec
    @Parameters(index = "0") private lateinit var projectRoot: String
    @Parameters(index = "1") private lateinit var iri: String

    override fun call(): Int = previewDomainReuse(spec, support, projectRoot, iri, "domain-dependencies", dependenciesOnly = true)
}

@Command(name = "domain-proposal", aliases = ["domain-proposal-preview"], mixinStandardHelpOptions = true)
internal class DomainProposalCommand(
    private val support: DomainOntologyCliSupport = DomainOntologyCliSupport(),
) : Callable<Int> {
    @Spec private lateinit var spec: CommandSpec
    @Parameters(index = "0") private lateinit var projectRoot: String
    @Parameters(index = "1") private lateinit var iri: String

    override fun call(): Int = previewDomainReuse(spec, support, projectRoot, iri, "domain-proposal", dependenciesOnly = false)
}

private fun previewDomainReuse(
    spec: CommandSpec,
    support: DomainOntologyCliSupport,
    projectRoot: String,
    iri: String,
    command: String,
    dependenciesOnly: Boolean,
): Int = when (val loaded = support.loadActiveProject(Path.of(projectRoot))) {
    is EntioResult.Failure -> printDomainFailure(spec, command, loaded)
    is EntioResult.Success -> {
        val project = loaded.value.second
        val managed = project.ontologies.singleOrNull { it.source.id == DomainOntologyProfileIdentity.MANAGED_SOURCE_ID }?.graph
            ?: return printDomainFailure(spec, command, DomainOntologyCliSupport.failure("domain-managed-source-missing", "The managed domain reuse source is unavailable."))
        when (val prepared = support.reuseService().prepare(
            DomainReusePreparationRequest(
                action = DomainReuseAction.Reuse,
                canonicalIri = Iri(iri),
                managedSourceId = DomainOntologyProfileIdentity.MANAGED_SOURCE_ID,
                customization = DomainReuseCustomization(),
                partialMaterializationAcknowledged = true,
            ),
            project.graph,
            managed,
        )) {
            is EntioResult.Failure -> printDomainFailure(spec, command, prepared)
            is EntioResult.Success -> {
                val batch = prepared.value
                spec.commandLine().out.println(jsonObject(
                    "command" to command,
                    "ok" to true,
                    "readOnly" to true,
                    "iri" to batch.canonicalIri.value,
                    "dependencies" to jsonArray(batch.dependencies.map { dependency -> jsonObject(
                        "iri" to dependency.iri.value,
                        "label" to dependency.label,
                        "kind" to dependency.kind.name,
                        "disposition" to dependency.disposition.name,
                    ) }),
                    "proposal" to if (dependenciesOnly) null else jsonObject(
                        "action" to batch.action.name,
                        "targetSourceId" to DomainOntologyProfileIdentity.MANAGED_SOURCE_ID,
                        "entryCount" to batch.entries.size,
                        "generatedStatementCount" to batch.generatedStatementCount,
                        "preparedPayloadBytes" to batch.preparedPayloadBytes,
                        "sourceIriStatic" to true,
                    ),
                ).encoded)
                0
            }
        }
    }
}

private fun domainProfileJson(profile: com.entio.core.DomainOntologyProfile): JsonFragment = jsonObject(
    "schema" to profile.schema,
    "sourceId" to profile.sourceId,
    "release" to profile.release,
    "packageFingerprint" to profile.packageFingerprint,
    "managedSourceId" to profile.managedSourceId,
)

private fun domainRecommendationJson(recommendation: DomainRecommendation): JsonFragment = jsonObject(
    "recommendationId" to recommendation.recommendationId,
    "iri" to recommendation.iri.value,
    "preferredLabel" to recommendation.preferredLabel,
    "kind" to recommendation.kind.name,
    "sourceFamily" to recommendation.sourceFamily,
    "sourceModuleIri" to recommendation.sourceModuleIri.value,
    "maturity" to recommendation.maturity.name,
    "confidence" to recommendation.confidence.name,
    "permittedActions" to jsonArray(recommendation.permittedActions.sortedBy { it.name }.map { it.name }),
    "reasons" to jsonArray(recommendation.reasons.map { jsonObject("type" to it.type.name, "relatedIri" to it.relatedIri?.value) }),
    "warnings" to jsonArray(recommendation.warnings.map { it.name }),
    "rankingContract" to recommendation.rankingContract,
)

private fun domainMigrationJson(
    command: String,
    report: DomainMigrationReport,
    preview: DomainMigrationPreview?,
): JsonFragment = jsonObject(
    "command" to command,
    "ok" to true,
    "readOnly" to true,
    "status" to report.status.name,
    "detectedIris" to jsonArray(report.detectedIris.map { it.value }),
    "recognizedIris" to jsonArray(report.recognizedIris.map { it.value }),
    "unsupportedIris" to jsonArray(report.unsupportedIris.map { it.value }),
    "localExtensionCount" to report.localExtensionCount,
    "verifiedCurrentRelease" to report.verifiedCurrentRelease,
    "historicalRelease" to report.historicalRelease,
    "provenanceSeedCandidates" to jsonArray(report.provenanceSeedCandidates.map { it.value }),
    "provenanceSeedingEligible" to report.provenanceSeedingEligible,
    "openWorkBaselineRetained" to report.openWorkBaselineRetained,
    "issues" to jsonArray(report.issues),
    "activationPreview" to preview?.activation?.let { activation -> jsonObject(
        "profile" to domainProfileJson(activation.profile),
        "profilePath" to activation.profilePath,
        "managedSourcePath" to activation.managedSourcePath,
        "changesProjectOntology" to activation.changesProjectOntology,
    ) },
    "movesExistingStatements" to (preview?.movesExistingStatements ?: false),
    "seedsProvenance" to (preview?.seedsProvenance ?: false),
    "requiresNormalProposalForStatementMovement" to
        (preview?.requiresNormalProposalForStatementMovement ?: true),
)

private fun printDomainFailure(spec: CommandSpec, command: String, result: EntioResult.Failure): Int {
    spec.commandLine().out.println(jsonObject(
        "command" to command,
        "ok" to false,
        "error" to jsonObject(
            "message" to result.message,
            "issues" to jsonArray(result.issues.map(::validationIssueJson)),
        ),
    ).encoded)
    return 1
}
