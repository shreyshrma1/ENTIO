package com.entio.cli

import com.entio.core.AddDatatypePropertyAssertionEdit
import com.entio.core.AddObjectPropertyAssertionEdit
import com.entio.core.AddSuperclassEdit
import com.entio.core.AssignTypeEdit
import com.entio.core.ChangeProposal
import com.entio.core.ChangeProposalStatus
import com.entio.core.CreateClassEdit
import com.entio.core.CreateDatatypePropertyEdit
import com.entio.core.CreateIndividualEdit
import com.entio.core.CreateObjectPropertyEdit
import com.entio.core.EntioProject
import com.entio.core.EntioResult
import com.entio.core.Iri
import com.entio.core.RemoveSuperclassEdit
import com.entio.core.RdfLiteral
import com.entio.core.SemanticEquivalenceResult
import com.entio.core.SetEntityLabelEdit
import com.entio.core.SetPropertyDomainEdit
import com.entio.core.SetPropertyRangeEdit
import com.entio.core.TypedOntologyEdit
import com.entio.core.ValidationIssue
import com.entio.core.ValidationReport
import com.entio.core.ValidationSeverity
import com.entio.diff.ProposalDiffGenerator
import com.entio.semantic.PreviewTurtleRoundTripVerifier
import com.entio.semantic.ProposalApplier
import com.entio.semantic.ProposalCreator
import com.entio.semantic.ProjectLoader
import com.entio.semantic.TypedOntologyEditTranslator
import com.entio.validation.ProjectValidator
import com.entio.validation.ProposalValidator
import java.nio.file.Path

public class ProposalCommandSupport(
    private val projectLoader: ProjectLoader,
    private val proposalCreator: ProposalCreator,
    private val proposalDiffGenerator: ProposalDiffGenerator,
    private val proposalValidator: ProposalValidator,
    private val projectValidator: ProjectValidator,
    private val equivalenceVerifier: PreviewTurtleRoundTripVerifier,
    public val proposalApplier: ProposalApplier,
    private val editTranslator: TypedOntologyEditTranslator,
) {
    internal fun prepare(
        projectRoot: Path,
        targetSourceId: String,
        edit: CliEditRequest,
        proposalId: String,
        title: String,
    ): EntioResult<PreparedProposal> {
        val project = when (val result = projectLoader.loadProject(projectRoot)) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }
        val typedEdit = when (val result = edit.toTypedEdit()) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }
        val changeSet = when (
            val result = editTranslator.translate(
                typedEdit,
            )
        ) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }
        val proposal = when (
            val result = proposalCreator.createProposal(
                project = project,
                targetSourceId = targetSourceId,
                changeSet = changeSet,
                id = proposalId,
                title = title,
            )
        ) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }
        val proposalWithDiff = when (
            val result = proposalDiffGenerator.attachDiff(proposal, project.graph)
        ) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }
        val preview = proposalWithDiff.preview
            ?: return failure(
                message = "Proposal '${proposalWithDiff.id}' does not include a preview graph.",
                code = "missing-proposal-preview",
                source = proposalWithDiff.id,
            )
        val equivalence = when (val result = equivalenceVerifier.verify(preview)) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }
        val projectReport = projectValidator.validateProject(projectRoot)
        val validationReport = proposalValidator.validateProposal(
            proposal = proposalWithDiff,
            currentProject = project,
            projectValidationReport = projectReport,
            semanticEquivalenceResult = equivalence,
        )

        return EntioResult.Success(
            PreparedProposal(
                project = project,
                proposal = proposalWithDiff.copy(validationReport = validationReport),
                validationReport = validationReport,
                equivalence = equivalence,
            ),
        )
    }

    internal companion object {
        internal const val CREATE_CLASS_EDIT: String = "create-class"
        internal const val CREATE_OBJECT_PROPERTY_EDIT: String = "create-object-property"
        internal const val CREATE_DATATYPE_PROPERTY_EDIT: String = "create-datatype-property"
        internal const val SET_PROPERTY_DOMAIN_EDIT: String = "set-property-domain"
        internal const val SET_PROPERTY_RANGE_EDIT: String = "set-property-range"
        internal const val CREATE_INDIVIDUAL_EDIT: String = "create-individual"
        internal const val ASSIGN_INDIVIDUAL_TYPE_EDIT: String = "assign-individual-type"
        internal const val ADD_OBJECT_PROPERTY_ASSERTION_EDIT: String = "add-object-property-assertion"
        internal const val ADD_DATATYPE_PROPERTY_ASSERTION_EDIT: String = "add-datatype-property-assertion"
        internal const val ADD_SUPERCLASS_EDIT: String = "add-superclass"
        internal const val REMOVE_SUPERCLASS_EDIT: String = "remove-superclass"
        internal const val SET_ENTITY_LABEL_EDIT: String = "set-entity-label"

        internal fun failure(
            message: String,
            code: String,
            source: String,
        ): EntioResult.Failure =
            EntioResult.Failure(
                message = message,
                issues = listOf(
                    ValidationIssue(
                        severity = ValidationSeverity.Error,
                        code = code,
                        message = message,
                        source = source,
                    ),
                ),
            )
    }
}

internal data class CliEditRequest(
    val editKind: String,
    val classIri: String = "",
    val label: String? = null,
    val propertyIri: String = "",
    val domainIri: String = "",
    val rangeIri: String = "",
    val datatype: String? = null,
    val individualIri: String = "",
    val typeIri: String = "",
    val subjectIri: String = "",
    val objectIri: String = "",
    val value: String = "",
    val language: String? = null,
    val superclassIri: String = "",
    val entityIri: String = "",
)

private fun CliEditRequest.toTypedEdit(): EntioResult<TypedOntologyEdit> =
    when (editKind) {
        ProposalCommandSupport.CREATE_CLASS_EDIT -> EntioResult.Success(
            CreateClassEdit(
                classIri = Iri(classIri),
                label = label?.let { RdfLiteral(it, languageTag = language) },
            ),
        )
        ProposalCommandSupport.CREATE_OBJECT_PROPERTY_EDIT -> EntioResult.Success(
            CreateObjectPropertyEdit(
                propertyIri = Iri(propertyIri),
                label = label?.let { RdfLiteral(it, languageTag = language) },
            ),
        )
        ProposalCommandSupport.CREATE_DATATYPE_PROPERTY_EDIT -> EntioResult.Success(
            CreateDatatypePropertyEdit(
                propertyIri = Iri(propertyIri),
                label = label?.let { RdfLiteral(it, languageTag = language) },
            ),
        )
        ProposalCommandSupport.SET_PROPERTY_DOMAIN_EDIT -> EntioResult.Success(
            SetPropertyDomainEdit(
                propertyIri = Iri(propertyIri),
                domainClassIri = Iri(domainIri),
            ),
        )
        ProposalCommandSupport.SET_PROPERTY_RANGE_EDIT -> EntioResult.Success(
            SetPropertyRangeEdit(
                propertyIri = Iri(propertyIri),
                rangeIri = Iri(rangeIri.ifBlank { datatype.orEmpty() }),
            ),
        )
        ProposalCommandSupport.CREATE_INDIVIDUAL_EDIT -> EntioResult.Success(
            CreateIndividualEdit(
                individualIri = Iri(individualIri),
                classIri = if (typeIri.isBlank()) null else Iri(typeIri),
            ),
        )
        ProposalCommandSupport.ASSIGN_INDIVIDUAL_TYPE_EDIT -> EntioResult.Success(
            AssignTypeEdit(
                resource = Iri(individualIri),
                typeIri = Iri(typeIri),
            ),
        )
        ProposalCommandSupport.ADD_OBJECT_PROPERTY_ASSERTION_EDIT -> EntioResult.Success(
            AddObjectPropertyAssertionEdit(
                subject = Iri(subjectIri),
                propertyIri = Iri(propertyIri),
                objectResource = Iri(objectIri),
            ),
        )
        ProposalCommandSupport.ADD_DATATYPE_PROPERTY_ASSERTION_EDIT -> EntioResult.Success(
            AddDatatypePropertyAssertionEdit(
                subject = Iri(subjectIri),
                propertyIri = Iri(propertyIri),
                value = RdfLiteral(
                    lexicalForm = value,
                    datatypeIri = datatype?.let(::Iri),
                    languageTag = language,
                ),
            ),
        )
        ProposalCommandSupport.ADD_SUPERCLASS_EDIT -> EntioResult.Success(
            AddSuperclassEdit(
                classIri = Iri(classIri),
                superclassIri = Iri(superclassIri),
            ),
        )
        ProposalCommandSupport.REMOVE_SUPERCLASS_EDIT -> EntioResult.Success(
            RemoveSuperclassEdit(
                classIri = Iri(classIri),
                superclassIri = Iri(superclassIri),
            ),
        )
        ProposalCommandSupport.SET_ENTITY_LABEL_EDIT -> EntioResult.Success(
            SetEntityLabelEdit(
                entity = Iri(entityIri),
                label = RdfLiteral(label.orEmpty(), languageTag = language),
            ),
        )
        else -> ProposalCommandSupport.failure(
            message = "Unsupported CLI edit '$editKind'.",
            code = "unsupported-cli-edit",
            source = editKind,
        )
    }

private fun String.ifBlank(defaultValue: () -> String): String =
    if (isBlank()) defaultValue() else this

internal data class PreparedProposal(
    val project: EntioProject,
    val proposal: ChangeProposal,
    val validationReport: ValidationReport,
    val equivalence: SemanticEquivalenceResult,
)

internal fun proposalPayload(
    command: String,
    prepared: PreparedProposal,
    ok: Boolean = prepared.validationReport.ok,
    statusOverride: ChangeProposalStatus? = null,
): JsonFragment {
    val proposal = prepared.proposal
    val status = statusOverride ?: proposal.status

    return jsonObject(
        "command" to command,
        "ok" to ok,
        "proposal" to jsonObject(
            "id" to proposal.id,
            "title" to proposal.title,
            "targetSourceId" to proposal.targetSourceId,
            "status" to status.name.lowercase(),
            "changeCount" to proposal.changeSet.changes.size,
            "sourceFileImpact" to jsonObject(
                "affectedPaths" to jsonArray(proposal.sourceFileImpact?.affectedPaths.orEmpty()),
            ),
            "preview" to jsonObject(
                "tripleCount" to proposal.preview?.graph?.triples?.size,
            ),
            "diff" to (proposal.diff?.let(::semanticDiffJson) ?: jsonObject("entryCount" to 0, "entries" to emptyList<Any>())),
            "validation" to validationReportJson(prepared.validationReport),
            "semanticEquivalence" to semanticEquivalenceJson(prepared.equivalence),
        ),
    )
}

private fun semanticEquivalenceJson(result: SemanticEquivalenceResult): JsonFragment =
    when (result) {
        SemanticEquivalenceResult.Equivalent -> jsonObject("status" to "equivalent")
        is SemanticEquivalenceResult.NotEquivalent -> jsonObject(
            "status" to "not-equivalent",
            "reason" to result.reason,
        )
        is SemanticEquivalenceResult.Failed -> jsonObject(
            "status" to "failed",
            "reason" to result.reason,
        )
    }

internal fun applyProposalPayload(
    proposalId: String,
    result: com.entio.core.ApplyProposalResult,
): JsonFragment =
    when (result) {
        is com.entio.core.ApplyProposalResult.Applied -> jsonObject(
            "command" to "proposal-apply",
            "ok" to true,
            "proposalId" to proposalId,
            "status" to "applied",
            "changedFiles" to jsonArray(result.changedFiles),
            "rollback" to jsonObject("status" to "not-required"),
        )
        is com.entio.core.ApplyProposalResult.Failed -> jsonObject(
            "command" to "proposal-apply",
            "ok" to false,
            "proposalId" to proposalId,
            "status" to "apply-failed",
            "reason" to result.reason,
            "rollback" to rollbackPayload(result.rollback),
        )
    }

private fun rollbackPayload(result: com.entio.core.RollbackResult): JsonFragment =
    when (result) {
        com.entio.core.RollbackResult.NotRequired -> jsonObject("status" to "not-required")
        is com.entio.core.RollbackResult.Restored -> jsonObject(
            "status" to "restored",
            "restoredFiles" to jsonArray(result.restoredFiles),
        )
        is com.entio.core.RollbackResult.Failed -> jsonObject(
            "status" to "failed",
            "reason" to result.reason,
        )
    }
