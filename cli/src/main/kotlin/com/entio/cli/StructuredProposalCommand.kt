package com.entio.cli

import com.entio.core.AddDatatypePropertyAssertionEdit
import com.entio.core.AddObjectPropertyAssertionEdit
import com.entio.core.AddSuperclassEdit
import com.entio.core.AssignTypeEdit
import com.entio.core.CreateClassEdit
import com.entio.core.CreateDatatypePropertyEdit
import com.entio.core.CreateIndividualEdit
import com.entio.core.CreateObjectPropertyEdit
import com.entio.core.DeletionDependency
import com.entio.core.EntioProject
import com.entio.core.EntioResult
import com.entio.core.EntityCandidate
import com.entio.core.EntitySelector
import com.entio.core.Iri
import com.entio.core.RemoveSuperclassEdit
import com.entio.core.RdfLiteral
import com.entio.core.RdfResource
import com.entio.core.SetEntityLabelEdit
import com.entio.core.SetPropertyDomainEdit
import com.entio.core.SetPropertyRangeEdit
import com.entio.core.StagedChange
import com.entio.core.StagedChangeOperation
import com.entio.core.StagedChangeSet
import com.entio.core.SymbolKind
import com.entio.core.TypedOntologyEdit
import com.entio.core.ValidationIssue
import com.entio.core.ValidationSeverity
import com.entio.semantic.ProjectLoader
import com.entio.semantic.StagedChangeSetNormalizer
import com.entio.semantic.LabelResolver
import com.entio.semantic.DeletionDependencyAnalyzer
import java.nio.file.Path
import java.util.concurrent.Callable
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import picocli.CommandLine.Spec

@Command(
    name = "proposal-request",
    mixinStandardHelpOptions = true,
    description = ["Validate and normalize an ordered structured proposal request."],
)
internal class StructuredProposalCommand(
    private val projectLoader: ProjectLoader = ProjectLoader(),
    private val parser: StructuredRequestParser = StructuredRequestParser(),
    private val normalizer: StagedChangeSetNormalizer = StagedChangeSetNormalizer(),
) : Callable<Int> {
    @Spec
    private lateinit var spec: CommandSpec

    @Parameters(index = "0", paramLabel = "PROJECT_ROOT", description = ["Path to an Entio project root."])
    private lateinit var projectRoot: String

    @Option(names = ["--request-file"], required = true, description = ["JSON file containing the ordered proposal request."])
    private lateinit var requestFile: String

    override fun call(): Int {
        val request = when (val result = parser.parse(Path.of(requestFile))) {
            is EntioResult.Failure -> return printFailure("proposal-request", result)
            is EntioResult.Success -> result.value
        }
        val project = when (val result = projectLoader.loadProject(Path.of(projectRoot))) {
            is EntioResult.Failure -> return printFailure("proposal-request", result)
            is EntioResult.Success -> result.value
        }
        if (project.resolvedSources.none { it.id == request.targetSourceId }) {
            return printFailure(
                "proposal-request",
                failure("missing-target-source", "Target ontology source '${request.targetSourceId}' was not found."),
            )
        }
        val entries = request.edits.mapIndexed { index, edit ->
            when (val operation = edit.toOperation(project, request.targetSourceId)) {
                is EntioResult.Failure -> return printFailure("proposal-request", operation)
                is EntioResult.Success -> StagedChange(
                    id = "${request.proposalId}-$index",
                    order = index,
                    targetSourceId = request.targetSourceId,
                    summary = edit.kind,
                    operation = operation.value,
                )
            }
        }
        val normalized = when (val result = normalizer.normalize(StagedChangeSet(entries))) {
            is EntioResult.Failure -> return printFailure("proposal-request", result)
            is EntioResult.Success -> result.value
        }
        val ok = normalized.conflicts.isEmpty() && normalized.changeSet != null
        spec.commandLine().out.println(
            jsonObject(
                "command" to "proposal-request",
                "ok" to ok,
                "request" to jsonObject(
                    "schemaVersion" to request.schemaVersion,
                    "proposalId" to request.proposalId,
                    "title" to request.title,
                    "targetSourceId" to request.targetSourceId,
                    "editCount" to request.edits.size,
                    "orderedEditKinds" to jsonArray(request.edits.map { it.kind }),
                    "baseline" to request.baseline?.let { baseline ->
                        jsonObject(
                            "projectFingerprint" to baseline.projectFingerprint,
                            "targetSourceFingerprint" to baseline.targetSourceFingerprint,
                            "graphFingerprint" to baseline.graphFingerprint,
                        )
                    },
                ),
                "normalized" to jsonObject(
                    "entryIds" to jsonArray(normalized.entries.map { it.id }),
                    "changeCount" to (normalized.changeSet?.changes?.size ?: 0),
                    "conflicts" to jsonArray(normalized.conflicts.map { conflict ->
                        jsonObject(
                            "kind" to conflict.kind.name,
                            "stagedChangeIds" to jsonArray(conflict.stagedChangeIds),
                            "message" to conflict.message,
                        )
                    }),
                ),
            ).encoded,
        )
        return if (ok) EXIT_OK else EXIT_FAILED
    }

    private fun printFailure(command: String, failure: EntioResult.Failure): Int {
        spec.commandLine().out.println(
            jsonObject(
                "command" to command,
                "ok" to false,
                "error" to jsonObject(
                    "message" to failure.message,
                    "issues" to jsonArray(failure.issues.map(::validationIssueJson)),
                ),
            ).encoded,
        )
        return EXIT_FAILED
    }

    private companion object {
        private const val EXIT_OK = 0
        private const val EXIT_FAILED = 1
    }
}

@Command(
    name = "resolve-label",
    mixinStandardHelpOptions = true,
    description = ["Resolve an exact entity label or IRI in an Entio project."],
)
internal class ResolveLabelCommand(
    private val projectLoader: ProjectLoader = ProjectLoader(),
    private val resolver: LabelResolver = LabelResolver(),
) : Callable<Int> {
    @Spec
    private lateinit var spec: CommandSpec

    @Parameters(index = "0")
    private lateinit var projectRoot: String

    @Option(names = ["--label"])
    private var label: String? = null

    @Option(names = ["--iri"])
    private var iri: String? = null

    @Option(names = ["--kind"])
    private var kind: String? = null

    @Option(names = ["--source-id"])
    private var sourceId: String? = null

    override fun call(): Int {
        val project = when (val result = projectLoader.loadProject(Path.of(projectRoot))) {
            is EntioResult.Failure -> return printFailure(result)
            is EntioResult.Success -> result.value
        }
        val resolution = resolver.resolve(
            project.symbols,
            EntitySelector(label = label, iri = iri?.let(::Iri), kind = parseKind(kind), sourceId = sourceId),
        )
        val (ok, payload) = when (resolution) {
            is com.entio.core.EntityResolutionResult.Resolved -> true to jsonObject("status" to "resolved", "candidate" to candidateJson(resolution.candidate))
            is com.entio.core.EntityResolutionResult.Ambiguous -> false to jsonObject("status" to "ambiguous", "candidates" to jsonArray(resolution.candidates.map(::candidateJson)))
            com.entio.core.EntityResolutionResult.NotFound -> false to jsonObject("status" to "not-found")
            is com.entio.core.EntityResolutionResult.Invalid -> false to jsonObject("status" to "invalid", "message" to resolution.reason)
        }
        spec.commandLine().out.println(jsonObject("command" to "resolve-label", "ok" to ok, "resolution" to payload).encoded)
        return if (ok) 0 else 1
    }

    private fun printFailure(result: EntioResult.Failure): Int {
        spec.commandLine().out.println(jsonObject("command" to "resolve-label", "ok" to false, "error" to jsonObject("message" to result.message, "issues" to jsonArray(result.issues.map(::validationIssueJson)))).encoded)
        return 1
    }
}

@Command(
    name = "generate-iri",
    mixinStandardHelpOptions = true,
    description = ["Generate a deterministic IRI for a new entity."],
)
internal class GenerateIriCommand(
    private val projectLoader: ProjectLoader = ProjectLoader(),
    private val generator: com.entio.semantic.DeterministicIriGenerator = com.entio.semantic.DeterministicIriGenerator(),
) : Callable<Int> {
    @Spec
    private lateinit var spec: CommandSpec

    @Parameters(index = "0")
    private lateinit var projectRoot: String

    @Option(names = ["--label"], required = true)
    private lateinit var label: String

    @Option(names = ["--kind"], required = true)
    private lateinit var kind: String

    @Option(names = ["--distinct"])
    private var distinct: Boolean = false

    override fun call(): Int {
        val project = when (val result = projectLoader.loadProject(Path.of(projectRoot))) {
            is EntioResult.Failure -> return printFailure(result)
            is EntioResult.Success -> result.value
        }
        val symbolKind = parseKind(kind) ?: return printFailure(failure("unsupported-symbol-kind", "Unsupported symbol kind '$kind'."))
        val result = generator.generate(label, symbolKind, project.config.iriNamespace, project.symbols, distinct)
        when (result) {
            is EntioResult.Failure -> return printFailure(result)
            is EntioResult.Success -> spec.commandLine().out.println(jsonObject("command" to "generate-iri", "ok" to true, "generated" to jsonObject("iri" to result.value.iri.value, "localName" to result.value.localName, "collision" to result.value.collision.name, "normalizationVersion" to result.value.normalizationVersion)).encoded)
        }
        return 0
    }

    private fun printFailure(result: EntioResult.Failure): Int {
        spec.commandLine().out.println(jsonObject("command" to "generate-iri", "ok" to false, "error" to jsonObject("message" to result.message, "issues" to jsonArray(result.issues.map(::validationIssueJson)))).encoded)
        return 1
    }
}

@Command(
    name = "deletion-dependencies",
    mixinStandardHelpOptions = true,
    description = ["Inspect explicit graph dependencies for a supported deletion target."],
)
internal class DeletionDependenciesCommand(
    private val projectLoader: ProjectLoader = ProjectLoader(),
    private val resolver: LabelResolver = LabelResolver(),
    private val analyzer: DeletionDependencyAnalyzer = DeletionDependencyAnalyzer(),
) : Callable<Int> {
    @Spec
    private lateinit var spec: CommandSpec

    @Parameters(index = "0")
    private lateinit var projectRoot: String

    @Parameters(index = "1")
    private lateinit var sourceId: String

    @Option(names = ["--iri"])
    private var iri: String? = null

    @Option(names = ["--label"])
    private var label: String? = null

    @Option(names = ["--kind"])
    private var kind: String? = null

    override fun call(): Int {
        val project = when (val result = projectLoader.loadProject(Path.of(projectRoot))) {
            is EntioResult.Failure -> return printFailure(result)
            is EntioResult.Success -> result.value
        }
        val candidate = when (val result = resolver.resolve(project.symbols, EntitySelector(label, iri?.let(::Iri), parseKind(kind), sourceId))) {
            is com.entio.core.EntityResolutionResult.Resolved -> result.candidate
            is com.entio.core.EntityResolutionResult.Ambiguous -> return printFailure(failure("ambiguous-entity", "The deletion target label is ambiguous."))
            com.entio.core.EntityResolutionResult.NotFound -> return printFailure(failure("missing-deletion-target", "The deletion target was not found."))
            is com.entio.core.EntityResolutionResult.Invalid -> return printFailure(failure("invalid-entity-selector", result.reason))
        }
        val ontology = project.ontologies.firstOrNull { it.source.id == sourceId }
            ?: return printFailure(failure("missing-ontology-source", "Ontology source '$sourceId' was not found."))
        val plan = analyzer.analyze(ontology, candidate)
        spec.commandLine().out.println(
            jsonObject(
                "command" to "deletion-dependencies",
                "ok" to (plan.status == com.entio.core.DeletionPlanStatus.Safe),
                "target" to candidateJson(candidate),
                "status" to plan.status.name,
                "directStatements" to jsonArray(plan.directStatements.map { dependencyJson(it, project) }),
                "dependentStatements" to jsonArray(plan.dependentStatements.map { dependencyJson(it, project) }),
            ).encoded,
        )
        return if (plan.status == com.entio.core.DeletionPlanStatus.Safe) 0 else 1
    }

    private fun printFailure(result: EntioResult.Failure): Int {
        spec.commandLine().out.println(jsonObject("command" to "deletion-dependencies", "ok" to false, "error" to jsonObject("message" to result.message, "issues" to jsonArray(result.issues.map(::validationIssueJson)))).encoded)
        return 1
    }
}

internal fun StructuredEditRequest.toOperation(
    project: EntioProject,
    targetSourceId: String,
): EntioResult<StagedChangeOperation> {
    if (kind == "delete-entity") {
        val targetIri = fields["entityIri"]?.takeIf { it.isNotBlank() }
            ?: return EntioResult.Failure(
                "Deletion requests must define entityIri.",
                listOf(ValidationIssue(ValidationSeverity.Error, "missing-deletion-target", "Deletion requests must define entityIri.", kind)),
            )
        val candidate = project.symbols.firstOrNull { it.iri.value == targetIri && it.sourceId == targetSourceId }
            ?: return EntioResult.Failure(
                "Deletion target '$targetIri' was not found.",
                listOf(ValidationIssue(ValidationSeverity.Error, "missing-deletion-target", "Deletion target '$targetIri' was not found.", kind)),
            )
        val ontology = project.ontologies.firstOrNull { it.source.id == targetSourceId }
            ?: return EntioResult.Failure(
                "Target ontology source '$targetSourceId' was not found.",
                listOf(ValidationIssue(ValidationSeverity.Error, "missing-ontology-source", "Target ontology source '$targetSourceId' was not found.", kind)),
            )
        val plan = DeletionDependencyAnalyzer().analyze(ontology, EntityCandidate(candidate.iri, candidate.label, candidate.kind, candidate.sourceId))
        return EntioResult.Success(StagedChangeOperation.Delete(plan))
    }
    val typed = when (kind) {
        "create-class" -> CreateClassEdit(requiredIri("classIri"), literal("label"))
        "create-object-property" -> CreateObjectPropertyEdit(requiredIri("propertyIri"), literal("label"))
        "create-datatype-property" -> CreateDatatypePropertyEdit(requiredIri("propertyIri"), literal("label"))
        "set-property-domain" -> SetPropertyDomainEdit(requiredIri("propertyIri"), requiredIri("domainIri"))
        "set-property-range" -> SetPropertyRangeEdit(requiredIri("propertyIri"), Iri(fields["rangeIri"] ?: fields["datatype"] ?: ""))
        "create-individual" -> CreateIndividualEdit(requiredIri("individualIri"), fields["typeIri"]?.takeIf { it.isNotBlank() }?.let(::Iri))
        "assign-individual-type" -> AssignTypeEdit(requiredIri("resourceIri"), requiredIri("typeIri"))
        "add-object-property-assertion" -> AddObjectPropertyAssertionEdit(requiredIri("subjectIri"), requiredIri("propertyIri"), requiredIri("objectIri"))
        "add-datatype-property-assertion" -> AddDatatypePropertyAssertionEdit(requiredIri("subjectIri"), requiredIri("propertyIri"), literal("value") ?: RdfLiteral(""))
        "add-superclass" -> AddSuperclassEdit(requiredIri("classIri"), requiredIri("superclassIri"))
        "remove-superclass" -> RemoveSuperclassEdit(requiredIri("classIri"), requiredIri("superclassIri"))
        "set-entity-label" -> SetEntityLabelEdit(requiredIri("entityIri"), literal("label") ?: RdfLiteral(""))
        else -> return EntioResult.Failure(
            "Unsupported edit kind '$kind'.",
            listOf(ValidationIssue(ValidationSeverity.Error, "unsupported-edit-kind", "Unsupported edit kind '$kind'.", kind)),
        )
    }
    return EntioResult.Success(StagedChangeOperation.TypedEdit(typed))
}

private fun StructuredEditRequest.requiredIri(name: String): Iri = Iri(fields[name].orEmpty())

private fun StructuredEditRequest.literal(name: String): RdfLiteral? =
    fields[name]?.takeIf { it.isNotBlank() }?.let { RdfLiteral(it, languageTag = fields["language"]) }

private fun parseKind(value: String?): SymbolKind? =
    value?.let { candidate -> SymbolKind.entries.firstOrNull { it.name.equals(candidate, ignoreCase = true) } }

private fun candidateJson(candidate: EntityCandidate): JsonFragment =
    jsonObject("iri" to candidate.iri.value, "label" to candidate.label, "kind" to candidate.kind.name, "sourceId" to candidate.sourceId)

private fun dependencyJson(dependency: DeletionDependency, project: EntioProject): JsonFragment {
    val labels = project.symbols.associate { symbol -> symbol.iri.value to symbol.label }
    val subject = dependency.statement.subjectResource.value
    val predicate = dependency.statement.predicate.value
    val objectTerm = dependency.statement.objectTerm
    return jsonObject(
        "kind" to dependency.kind.name,
        "sourceId" to dependency.sourceId,
        "selectedForRemoval" to dependency.selectedForRemoval,
        "subject" to subject,
        "subjectLabel" to displayIri(subject, labels),
        "predicate" to predicate,
        "predicateLabel" to displayIri(predicate, labels),
        "object" to objectTerm.toString(),
        "objectLabel" to displayTerm(objectTerm, labels),
    )
}

private fun displayTerm(term: com.entio.core.RdfTerm, labels: Map<String, String?>): String =
    when (term) {
        is RdfResource -> displayIri(term.value, labels)
        is RdfLiteral -> buildString {
            append(term.lexicalForm)
            term.languageTag?.let { append("@").append(it) }
            term.datatypeIri?.let { append("^^").append(displayIri(it.value, labels)) }
        }
    }

private fun displayIri(value: String, labels: Map<String, String?>): String {
    val label = labels[value]
    if (!label.isNullOrBlank()) return label
    val separator = maxOf(value.lastIndexOf('#'), value.lastIndexOf('/'))
    return if (separator >= 0 && separator < value.lastIndex) value.substring(separator + 1) else value
}

private fun failure(code: String, message: String): EntioResult.Failure =
    EntioResult.Failure(message, listOf(ValidationIssue(ValidationSeverity.Error, code, message)))
