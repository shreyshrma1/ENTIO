package com.entio.cli

import com.entio.core.ConsistencyStatus
import com.entio.core.EntioProject
import com.entio.core.EntioResult
import com.entio.core.FactOrigin
import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.ReasoningClassRelationship
import com.entio.core.ReasoningExplanation
import com.entio.core.ReasoningIndividualType
import com.entio.core.ReasoningPropertyRelationship
import com.entio.core.ReasoningResult
import com.entio.core.ReasoningRunStatus
import com.entio.core.RdfResource
import com.entio.core.ShaclConstraintKind
import com.entio.core.ShaclNodeShape
import com.entio.core.ShaclPath
import com.entio.core.ShaclPropertyShape
import com.entio.core.ShaclSeverity
import com.entio.core.ShaclTarget
import com.entio.core.ShaclValidationMode
import com.entio.core.ShaclValidationReport
import com.entio.core.ShaclValidationResult
import com.entio.core.ShaclValidationStatus
import com.entio.core.StagedChange
import com.entio.core.StagedChangeOperation
import com.entio.core.StagedChangeSet
import com.entio.core.ValidationIssue
import com.entio.core.ValidationSeverity
import com.entio.diff.CombinedProposalPreviewService
import com.entio.diff.ProposalImpactAnalyzer
import com.entio.semantic.ProjectLoader
import com.entio.semantic.ReasoningExplanationService
import com.entio.semantic.ReasoningExplanationTarget
import com.entio.semantic.ReasoningService
import com.entio.semantic.ShaclGraphLoader
import com.entio.semantic.ShaclGraphSet
import com.entio.semantic.ShaclShapeAuthoringService
import com.entio.semantic.ShaclValidationService
import com.entio.semantic.StagedChangeSetNormalizer
import java.nio.file.Path
import java.util.concurrent.Callable
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import picocli.CommandLine.Spec

@Command(
    name = "reasoning-refresh",
    aliases = ["reasoning"],
    mixinStandardHelpOptions = true,
    description = ["Run the Kotlin reasoning service and return structured results."],
)
internal class ReasoningRefreshCommand(
    private val projectLoader: ProjectLoader = ProjectLoader(),
    private val reasoningService: ReasoningService = ReasoningService(),
) : Callable<Int> {
    @Spec
    private lateinit var spec: CommandSpec

    @Parameters(index = "0")
    private lateinit var projectRoot: String

    override fun call(): Int {
        val project = when (val result = projectLoader.loadProject(Path.of(projectRoot))) {
            is EntioResult.Failure -> return printPhase4Failure("reasoning-refresh", result, spec)
            is EntioResult.Success -> result.value
        }
        return when (val result = reasoningService.reason(project.graph, sourceId = project.config.name)) {
            is EntioResult.Failure -> printPhase4Failure("reasoning-refresh", result, spec)
            is EntioResult.Success -> {
                spec.commandLine().out.println(jsonObject("command" to "reasoning-refresh", "ok" to true, "reasoning" to reasoningJson(result.value)).encoded)
                0
            }
        }
    }
}

@Command(
    name = "reasoning-explain",
    mixinStandardHelpOptions = true,
    description = ["Explain a selected reasoning result through the semantic engine."],
)
internal class ReasoningExplainCommand(
    private val projectLoader: ProjectLoader = ProjectLoader(),
    private val reasoningService: ReasoningService = ReasoningService(),
    private val explanationService: ReasoningExplanationService = ReasoningExplanationService(),
) : Callable<Int> {
    @Spec
    private lateinit var spec: CommandSpec

    @Parameters(index = "0")
    private lateinit var projectRoot: String

    @Option(names = ["--target-iri"], required = true)
    private lateinit var targetIri: String

    override fun call(): Int {
        val project = when (val result = projectLoader.loadProject(Path.of(projectRoot))) {
            is EntioResult.Failure -> return printPhase4Failure("reasoning-explain", result, spec)
            is EntioResult.Success -> result.value
        }
        val reasoning = when (val result = reasoningService.reason(project.graph, sourceId = project.config.name)) {
            is EntioResult.Failure -> return printPhase4Failure("reasoning-explain", result, spec)
            is EntioResult.Success -> result.value
        }
        val explanation = when (val result = explanationService.explain(ReasoningExplanationTarget.Entity(Iri(targetIri)), reasoning, project.graph)) {
            is EntioResult.Failure -> return printPhase4Failure("reasoning-explain", result, spec)
            is EntioResult.Success -> result.value
        }
        spec.commandLine().out.println(jsonObject("command" to "reasoning-explain", "ok" to true, "explanation" to explanationJson(explanation)).encoded)
        return 0
    }
}

@Command(
    name = "shacl-validate",
    mixinStandardHelpOptions = true,
    description = ["Validate configured data against configured SHACL shapes in an explicit mode."],
)
internal class ShaclValidateCommand(
    private val projectLoader: ProjectLoader = ProjectLoader(),
    private val graphLoader: ShaclGraphLoader = ShaclGraphLoader(),
    private val validationService: ShaclValidationService = ShaclValidationService(),
    private val reasoningService: ReasoningService = ReasoningService(),
) : Callable<Int> {
    @Spec
    private lateinit var spec: CommandSpec

    @Parameters(index = "0")
    private lateinit var projectRoot: String

    @Option(names = ["--mode"], defaultValue = "asserted-only")
    private lateinit var modeName: String

    override fun call(): Int {
        val mode = when (modeName.lowercase()) {
            "asserted-only", "asserted" -> ShaclValidationMode.AssertedOnly
            "asserted-and-inferred", "inferred" -> ShaclValidationMode.AssertedAndInferred
            else -> return printPhase4Failure("shacl-validate", failure("invalid-validation-mode", "Unsupported SHACL validation mode '$modeName'."), spec)
        }
        val project = when (val result = projectLoader.loadProject(Path.of(projectRoot))) {
            is EntioResult.Failure -> return printPhase4Failure("shacl-validate", result, spec)
            is EntioResult.Success -> result.value
        }
        val graphs = when (val result = graphLoader.load(project.ontologies)) {
            is EntioResult.Failure -> return printPhase4Failure("shacl-validate", result, spec)
            is EntioResult.Success -> result.value
        }
        val inferredGraph = if (mode == ShaclValidationMode.AssertedAndInferred) {
            when (val result = reasoningService.reason(project.graph, sourceId = project.config.name)) {
                is EntioResult.Failure -> null
                is EntioResult.Success -> result.value.takeIf { it.metadata.status == ReasoningRunStatus.Completed }?.let(::inferredGraph)
            }
        } else {
            null
        }
        val report = when (val result = validationService.validate(graphs, mode, inferredGraph)) {
            is EntioResult.Failure -> return printPhase4Failure("shacl-validate", result, spec)
            is EntioResult.Success -> result.value
        }
        val ok = report.status == ShaclValidationStatus.Completed && report.results.none { it.severity == ShaclSeverity.Violation }
        spec.commandLine().out.println(jsonObject("command" to "shacl-validate", "ok" to ok, "validation" to shaclReportJson(report)).encoded)
        return if (ok) 0 else 1
    }
}

@Command(
    name = "shacl-shapes",
    mixinStandardHelpOptions = true,
    description = ["List supported SHACL shapes through Entio-owned descriptors."],
)
internal class ShaclShapesCommand(
    private val projectLoader: ProjectLoader = ProjectLoader(),
    private val graphLoader: ShaclGraphLoader = ShaclGraphLoader(),
    private val authoringService: ShaclShapeAuthoringService = ShaclShapeAuthoringService(),
) : Callable<Int> {
    @Spec
    private lateinit var spec: CommandSpec

    @Parameters(index = "0")
    private lateinit var projectRoot: String

    override fun call(): Int {
        val project = when (val result = projectLoader.loadProject(Path.of(projectRoot))) {
            is EntioResult.Failure -> return printPhase4Failure("shacl-shapes", result, spec)
            is EntioResult.Success -> result.value
        }
        val graphs = when (val result = graphLoader.load(project.ontologies)) {
            is EntioResult.Failure -> return printPhase4Failure("shacl-shapes", result, spec)
            is EntioResult.Success -> result.value
        }
        val sourceId = graphs.identity.shapesSourceIds.singleOrNull() ?: "shapes"
        val document = when (val result = authoringService.load(sourceId, graphs.shapesGraph)) {
            is EntioResult.Failure -> return printPhase4Failure("shacl-shapes", result, spec)
            is EntioResult.Success -> result.value
        }
        spec.commandLine().out.println(jsonObject("command" to "shacl-shapes", "ok" to true, "shapes" to jsonArray(document.nodeShapes.map(::shapeJson))).encoded)
        return 0
    }
}

@Command(
    name = "proposal-impact",
    mixinStandardHelpOptions = true,
    description = ["Preview a typed request and return separate explicit, reasoning, and SHACL impact."],
)
internal class ProposalImpactCommand(
    private val projectLoader: ProjectLoader = ProjectLoader(),
    private val requestParser: StructuredRequestParser = StructuredRequestParser(),
    private val normalizer: StagedChangeSetNormalizer = StagedChangeSetNormalizer(),
    private val previewService: CombinedProposalPreviewService = CombinedProposalPreviewService(),
    private val impactAnalyzer: ProposalImpactAnalyzer = ProposalImpactAnalyzer(),
    private val reasoningService: ReasoningService = ReasoningService(),
    private val graphLoader: ShaclGraphLoader = ShaclGraphLoader(),
    private val shaclValidationService: ShaclValidationService = ShaclValidationService(),
) : Callable<Int> {
    @Spec
    private lateinit var spec: CommandSpec

    @Parameters(index = "0")
    private lateinit var projectRoot: String

    @Option(names = ["--request-file"], required = true)
    private lateinit var requestFile: String

    override fun call(): Int {
        val request = when (val result = requestParser.parse(Path.of(requestFile))) {
            is EntioResult.Failure -> return printPhase4Failure("proposal-impact", result, spec)
            is EntioResult.Success -> result.value
        }
        val project = when (val result = projectLoader.loadProject(Path.of(projectRoot))) {
            is EntioResult.Failure -> return printPhase4Failure("proposal-impact", result, spec)
            is EntioResult.Success -> result.value
        }
        val entries = request.edits.mapIndexed { index, edit ->
            when (val result = edit.toOperation(project, request.targetSourceId)) {
                is EntioResult.Failure -> return printPhase4Failure("proposal-impact", result, spec)
                is EntioResult.Success -> StagedChange(
                    id = "${request.proposalId}-$index",
                    order = index,
                    targetSourceId = request.targetSourceId,
                    summary = edit.kind,
                    operation = result.value,
                )
            }
        }
        val normalized = when (val result = normalizer.normalize(StagedChangeSet(entries))) {
            is EntioResult.Failure -> return printPhase4Failure("proposal-impact", result, spec)
            is EntioResult.Success -> result.value
        }
        if (normalized.conflicts.isNotEmpty()) {
            spec.commandLine().out.println(jsonObject("command" to "proposal-impact", "ok" to false, "status" to "conflicted", "conflicts" to jsonArray(normalized.conflicts.map { jsonObject("kind" to it.kind.name, "message" to it.message, "stagedChangeIds" to jsonArray(it.stagedChangeIds)) })).encoded)
            return 1
        }
        val changeSet = normalized.changeSet ?: return printPhase4Failure("proposal-impact", failure("empty-change-set", "The proposal request produced no graph changes."), spec)
        val currentReasoning = (reasoningService.reason(project.graph, sourceId = project.config.name) as? EntioResult.Success)?.value
        val previewGraph = com.entio.semantic.GraphChangePreviewer().preview(project.graph, changeSet).let { result ->
            (result as? EntioResult.Success)?.value?.graph ?: project.graph
        }
        val previewReasoning = (reasoningService.reason(previewGraph, sourceId = project.config.name) as? EntioResult.Success)?.value
        val currentGraphs = (graphLoader.load(project.ontologies) as? EntioResult.Success)?.value
        val previewGraphs = currentGraphs?.let { graphs ->
            val target = project.ontologies.firstOrNull { it.source.id == request.targetSourceId }
            val otherTriples = project.ontologies.filter { it.source.id != request.targetSourceId }.flatMap { it.graph.triples }.toSet()
            val previewTargetGraph = target?.copy(graph = com.entio.core.GraphState(previewGraph.triples - otherTriples))
            previewTargetGraph?.let { replacement ->
                graphLoader.load(project.ontologies.map { ontology ->
                    if (ontology.source.id == request.targetSourceId) replacement else ontology
                })
            }
        }?.let { result -> (result as? EntioResult.Success)?.value }
        val currentShacl = currentGraphs?.let { graphs ->
            (shaclValidationService.validate(graphs, ShaclValidationMode.AssertedOnly) as? EntioResult.Success)?.value
        }
        val previewShacl = previewGraphs?.let { graphs ->
            (shaclValidationService.validate(graphs, ShaclValidationMode.AssertedOnly) as? EntioResult.Success)?.value
        }
        val combined = when (val result = previewService.preview(request.proposalId, entries.map(StagedChange::id), listOf(request.targetSourceId), project.graph, changeSet, currentReasoning, previewReasoning, currentShacl, previewShacl)) {
            is EntioResult.Failure -> return printPhase4Failure("proposal-impact", result, spec)
            is EntioResult.Success -> result.value
        }
        val impact = when (val result = impactAnalyzer.analyze(project.graph, previewGraph, currentReasoning, previewReasoning, currentShacl, previewShacl)) {
            is EntioResult.Failure -> return printPhase4Failure("proposal-impact", result, spec)
            is EntioResult.Success -> result.value
        }
        val ok = combined.validationReport.ok
        spec.commandLine().out.println(jsonObject("command" to "proposal-impact", "ok" to ok, "status" to combined.metadata.status.name.lowercase(), "impact" to proposalImpactJson(impact), "validation" to validationReportJson(combined.validationReport)).encoded)
        return if (ok) 0 else 1
    }
}

private fun printPhase4Failure(command: String, result: EntioResult.Failure, spec: CommandSpec): Int {
    val output = jsonObject(
        "command" to command,
        "ok" to false,
        "error" to jsonObject("message" to result.message, "issues" to jsonArray(result.issues.map(::validationIssueJson))),
    ).encoded
    spec.commandLine().out.println(output)
    return 1
}

private fun failure(code: String, message: String): EntioResult.Failure = EntioResult.Failure(
    message,
    listOf(ValidationIssue(ValidationSeverity.Error, code, message, "cli")),
)

private fun reasoningJson(result: ReasoningResult): JsonFragment = jsonObject(
    "status" to result.metadata.status.name.lowercase(),
    "reasoner" to jsonObject("name" to result.metadata.reasonerName, "version" to result.metadata.reasonerVersion, "owlApiVersion" to result.metadata.owlApiVersion),
    "fingerprints" to jsonObject("graph" to result.metadata.fingerprints.graphFingerprint, "imports" to result.metadata.fingerprints.importClosureFingerprint, "configuration" to result.metadata.fingerprints.reasonerConfigurationFingerprint),
    "importClosureComplete" to result.metadata.importClosureComplete,
    "consistency" to result.consistency.name.lowercase(),
    "classRelationships" to jsonArray(result.classRelationships.map(::classRelationshipJson)),
    "individualTypes" to jsonArray(result.individualTypes.map(::individualTypeJson)),
    "propertyRelationships" to jsonArray(result.propertyRelationships.map(::propertyRelationshipJson)),
    "unsatisfiableClasses" to jsonArray(result.unsatisfiableClasses.map { it.value }),
    "unsupportedFeatures" to jsonArray(result.unsupportedFeatures.map { jsonObject("feature" to it.feature, "support" to it.support.name.lowercase(), "affectsCompleteness" to it.affectsCompleteness, "message" to it.message) }),
    "warnings" to jsonArray(result.warnings),
    "errors" to jsonArray(result.errors),
)

private fun classRelationshipJson(value: ReasoningClassRelationship): JsonFragment = jsonObject("subject" to value.subject.value, "objectClass" to value.objectClass.value, "origin" to value.origin.name.lowercase(), "sourceId" to value.sourceId)

private fun individualTypeJson(value: ReasoningIndividualType): JsonFragment = jsonObject("individual" to value.individual.value, "type" to value.type.value, "origin" to value.origin.name.lowercase(), "sourceId" to value.sourceId)

private fun propertyRelationshipJson(value: ReasoningPropertyRelationship): JsonFragment = jsonObject("subject" to value.subject.value, "predicate" to value.predicate.value, "objectResource" to value.objectResource.value, "origin" to value.origin.name.lowercase(), "sourceId" to value.sourceId)

private fun explanationJson(value: ReasoningExplanation): JsonFragment = jsonObject("target" to value.target.value, "kind" to value.kind.name.lowercase(), "rule" to value.rule, "complete" to value.complete, "caveat" to value.caveat, "assertedEvidence" to jsonArray(value.assertedEvidence.map { tripleJson(it) }))

private fun shaclReportJson(value: ShaclValidationReport): JsonFragment = jsonObject(
    "status" to value.status.name.lowercase(),
    "mode" to when (value.mode) {
        ShaclValidationMode.AssertedOnly -> "asserted-only"
        ShaclValidationMode.AssertedAndInferred -> "asserted-and-inferred"
    },
    "graphIdentity" to jsonObject("dataSourceIds" to jsonArray(value.graphIdentity.dataSourceIds), "shapesSourceIds" to jsonArray(value.graphIdentity.shapesSourceIds), "dataGraphFingerprint" to value.graphIdentity.dataGraphFingerprint, "shapesGraphFingerprint" to value.graphIdentity.shapesGraphFingerprint),
    "results" to jsonArray(value.results.map(::shaclResultJson)),
    "warnings" to jsonArray(value.warnings),
    "errors" to jsonArray(value.errors),
)

private fun shaclResultJson(value: ShaclValidationResult): JsonFragment = jsonObject("resultId" to value.resultId, "severity" to value.severity.name.lowercase(), "message" to value.message, "focusNode" to value.focusNode.value, "path" to pathJson(value.path), "shape" to value.shape.iri.value, "constraint" to value.constraint.name.lowercase(), "value" to value.value?.let(::rdfTermJson), "sourceId" to value.sourceId)

private fun shapeJson(value: ShaclNodeShape): JsonFragment = jsonObject("iri" to value.id.iri.value, "sourceId" to value.id.sourceId, "targets" to jsonArray(value.targets.map(::targetJson)), "propertyShapes" to jsonArray(value.propertyShapes.map(::propertyShapeJson)), "constraints" to jsonArray(value.constraints.map { jsonObject("kind" to it.kind.name.lowercase(), "value" to it.value.toString()) }), "closed" to value.closed, "ignoredProperties" to jsonArray(value.ignoredProperties.map { it.value }), "severity" to value.severity.name.lowercase(), "message" to value.message)

private fun targetJson(value: ShaclTarget): JsonFragment = when (value) {
    is ShaclTarget.TargetClass -> jsonObject("kind" to "target-class", "iri" to value.classIri.value)
    is ShaclTarget.TargetNode -> jsonObject("kind" to "target-node", "value" to value.node.value)
    is ShaclTarget.TargetSubjectsOf -> jsonObject("kind" to "target-subjects-of", "iri" to value.propertyIri.value)
    is ShaclTarget.TargetObjectsOf -> jsonObject("kind" to "target-objects-of", "iri" to value.propertyIri.value)
}

private fun propertyShapeJson(value: ShaclPropertyShape): JsonFragment = jsonObject("iri" to value.id.iri.value, "sourceId" to value.id.sourceId, "path" to pathJson(value.path), "constraints" to jsonArray(value.constraints.map { jsonObject("kind" to it.kind.name.lowercase(), "value" to it.value.toString()) }), "severity" to value.severity.name.lowercase(), "message" to value.message)

private fun pathJson(value: ShaclPath?): JsonFragment? = when (value) {
    null -> null
    is ShaclPath.DirectProperty -> jsonObject("kind" to "direct-property", "iri" to value.propertyIri.value)
}

private fun proposalImpactJson(value: com.entio.core.ProposalImpactReport): JsonFragment = jsonObject(
    "explicitDiff" to semanticDiffJson(value.explicitDiff),
    "reasoningImpact" to jsonObject(
        "addedInferences" to jsonArray(value.reasoningImpact.addedInferences.map(::propertyRelationshipJson)),
        "removedInferences" to jsonArray(value.reasoningImpact.removedInferences.map(::propertyRelationshipJson)),
        "consistencyChanged" to value.reasoningImpact.consistencyChanged,
        "unsatisfiableClassesAdded" to jsonArray(value.reasoningImpact.unsatisfiableClassesAdded.map { it.value }),
        "unsatisfiableClassesResolved" to jsonArray(value.reasoningImpact.unsatisfiableClassesResolved.map { it.value }),
    ),
    "shaclImpact" to jsonObject(
        "newResults" to jsonArray(value.shaclImpact.newResults.map(::shaclResultJson)),
        "worsenedResults" to jsonArray(value.shaclImpact.worsenedResults.map(::shaclResultJson)),
        "unchangedResults" to jsonArray(value.shaclImpact.unchangedResults.map(::shaclResultJson)),
        "resolvedResults" to jsonArray(value.shaclImpact.resolvedResults.map(::shaclResultJson)),
    ),
    "status" to value.status.name.lowercase(),
    "blockingMessages" to jsonArray(value.blockingMessages),
)

private fun tripleJson(value: GraphTriple): JsonFragment = jsonObject("subject" to value.subjectResource.value, "predicate" to value.predicate.value, "object" to rdfTermJson(value.objectTerm))

private fun inferredGraph(result: ReasoningResult): GraphState = GraphState(
    result.classRelationships.filter { it.origin == FactOrigin.Inferred }.map { GraphTriple(it.subject, Iri(RDFS_SUBCLASS_OF), it.objectClass) }
        .plus(result.individualTypes.filter { it.origin == FactOrigin.Inferred }.map { GraphTriple(it.individual, Iri(RDF_TYPE), it.type) })
        .plus(result.propertyRelationships.filter { it.origin == FactOrigin.Inferred }.map { GraphTriple(it.subject, it.predicate, it.objectResource) })
        .toSet(),
)

private const val RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
private const val RDFS_SUBCLASS_OF = "http://www.w3.org/2000/01/rdf-schema#subClassOf"
