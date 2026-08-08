package com.entio.web

import com.entio.core.DomainCustomizationClassification
import com.entio.core.DomainOntologyProfileIdentity
import com.entio.core.DomainReuseAction
import com.entio.core.DomainReuseEventKind
import com.entio.core.DomainReuseProvenanceEvent
import com.entio.core.EntioResult
import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.RdfLiteral
import com.entio.core.RdfResource
import com.entio.core.StagedChange
import com.entio.core.StagedChangeOperation
import com.entio.semantic.DomainReuseProvenanceRepository
import com.entio.semantic.PreparedDomainReuseProvenance
import com.entio.web.contract.ProjectRegistry
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.Locale

internal interface DomainReuseApplyHooks {
    fun begin(
        projectId: String,
        proposalId: String,
        baselineFingerprint: String,
        expectedFingerprint: String,
        expectedGraph: GraphState,
        staged: List<StagedChange>,
        appliedByUserId: String,
    )

    fun commit(projectId: String)
    fun applied(projectId: String)
    fun rolledBack(projectId: String)
    fun recover(projectId: String, currentProjectFingerprint: String)
}

/** Coordinates project-local reuse provenance with the existing atomic proposal application. */
internal class DomainReuseProvenanceCoordinator(
    private val projectRegistry: ProjectRegistry,
    private val repository: DomainReuseProvenanceRepository = DomainReuseProvenanceRepository(),
    private val clock: Clock = Clock.systemUTC(),
) : DomainReuseApplyHooks {
    private val pending: MutableMap<String, PreparedDomainReuseProvenance> = linkedMapOf()

    @Synchronized
    override fun begin(
        projectId: String,
        proposalId: String,
        baselineFingerprint: String,
        expectedFingerprint: String,
        expectedGraph: GraphState,
        staged: List<StagedChange>,
        appliedByUserId: String,
    ) {
        check(projectId !in pending) { "domain-provenance-apply-already-pending" }
        val drafts = staged.mapNotNull(StagedChange::domainReuseProvenance)
            .distinctBy { listOf(it.canonicalIri.value, it.action.name, it.sourceSnapshot.recordFingerprint) }
        if (drafts.isEmpty()) return
        val existing = when (val result = repository.list(projectRegistry.rootFor(projectId))) {
            is EntioResult.Failure -> throw WebWorkflowFailure(result.issues.first().code, result.message)
            is EntioResult.Success -> result.value
        }
        val appliedAt = Instant.now(clock).toString()
        val changes = staged.filter { it.domainReuseProvenance != null }.flatMap { stagedChange ->
            (stagedChange.operation as? StagedChangeOperation.GraphChanges)?.changeSet?.changes.orEmpty()
        }
        val changeSetId = sha256(changes.map { stableTriple(it.triple) }.sorted().joinToString("\n"))
        val events = drafts.map { draft ->
            val projectStatements = expectedGraph.triples.filter { triple ->
                triple.subjectResource == draft.canonicalIri || triple.objectTerm == draft.canonicalIri
            }.sortedBy(::stableTriple)
            val prior = existing.lastOrNull { it.canonicalIri == draft.canonicalIri }?.recordId
            val eventKind = when (draft.action) {
                DomainReuseAction.Reuse -> DomainReuseEventKind.Reused
                DomainReuseAction.ReuseAndCustomize -> DomainReuseEventKind.Customized
                DomainReuseAction.ExtendLocally -> DomainReuseEventKind.Extended
                DomainReuseAction.MapClose, DomainReuseAction.MapRelated -> DomainReuseEventKind.Mapped
                DomainReuseAction.RemoveReuse -> DomainReuseEventKind.Removed
                DomainReuseAction.ContinueLocally -> error("Local continuation has no provenance event.")
            }
            val customization = when (draft.action) {
                DomainReuseAction.ReuseAndCustomize -> classify(draft.sourceSnapshot.statements, projectStatements)
                DomainReuseAction.RemoveReuse -> DomainCustomizationClassification.SourceSnapshotUnavailable
                else -> DomainCustomizationClassification.Unchanged
            }
            val recordId = "drp_" + sha256(
                listOf(projectId, proposalId, draft.canonicalIri.value, draft.action.name, expectedFingerprint)
                    .joinToString("\u0000"),
            ).take(40)
            DomainReuseProvenanceEvent(
                recordId = recordId,
                eventKind = eventKind,
                sourceId = DomainOntologyProfileIdentity.SOURCE_ID,
                release = DomainOntologyProfileIdentity.RELEASE,
                packageFingerprint = DomainOntologyProfileIdentity.PACKAGE_FINGERPRINT,
                recordFingerprint = draft.sourceSnapshot.recordFingerprint,
                canonicalIri = draft.canonicalIri,
                entityKind = draft.sourceSnapshot.kind,
                sourceOntologyIri = draft.sourceSnapshot.sourceOntologyIri,
                sourcePath = draft.sourceSnapshot.sourcePath,
                sourceStatementFingerprint = draft.sourceSnapshot.statementFingerprint,
                sourceSnapshot = draft.sourceSnapshot.statements,
                omittedSourceAxioms = draft.sourceSnapshot.omittedSourceAxioms,
                dependencySetFingerprint = draft.dependencySetFingerprint,
                targetManagedSourceId = draft.targetManagedSourceId,
                proposalId = proposalId,
                appliedChangeSetId = changeSetId,
                actorId = appliedByUserId,
                appliedAt = appliedAt,
                baselineProjectFingerprint = baselineFingerprint,
                resultingProjectFingerprint = expectedFingerprint,
                projectStatementFingerprint = sha256(projectStatements.joinToString("\n", transform = ::stableTriple)),
                customization = customization,
                priorRecordId = prior,
                checksum = "0".repeat(64),
            )
        }
        val prepared = when (
            val result = repository.prepare(
                projectRegistry.rootFor(projectId),
                events,
                baselineFingerprint,
                expectedFingerprint,
            )
        ) {
            is EntioResult.Failure -> throw WebWorkflowFailure(result.issues.first().code, result.message)
            is EntioResult.Success -> result.value
        }
        pending[projectId] = prepared
    }

    @Synchronized
    override fun commit(projectId: String): Unit {
        val prepared = pending[projectId] ?: return
        when (val result = repository.commit(prepared)) {
            is EntioResult.Failure -> throw WebWorkflowFailure(result.issues.first().code, result.message)
            is EntioResult.Success -> Unit
        }
    }

    @Synchronized
    override fun applied(projectId: String): Unit {
        val prepared = pending[projectId] ?: return
        when (val result = repository.finish(prepared)) {
            is EntioResult.Failure -> throw WebWorkflowFailure(result.issues.first().code, result.message)
            is EntioResult.Success -> pending.remove(projectId)
        }
    }

    @Synchronized
    override fun rolledBack(projectId: String): Unit {
        val prepared = pending[projectId] ?: return
        when (val result = repository.rollback(prepared)) {
            is EntioResult.Failure -> throw WebWorkflowFailure(result.issues.first().code, result.message)
            is EntioResult.Success -> pending.remove(projectId)
        }
    }

    @Synchronized
    override fun recover(projectId: String, currentProjectFingerprint: String): Unit {
        when (val result = repository.recover(projectRegistry.rootFor(projectId), currentProjectFingerprint)) {
            is EntioResult.Failure -> throw WebWorkflowFailure(result.issues.first().code, result.message)
            is EntioResult.Success -> Unit
        }
    }

    private fun classify(
        source: List<GraphTriple>,
        project: List<GraphTriple>,
    ): DomainCustomizationClassification {
        val differences = (source.toSet() - project.toSet()) + (project.toSet() - source.toSet())
        return when {
            differences.isEmpty() -> DomainCustomizationClassification.Unchanged
            differences.all { it.predicate.value in ANNOTATION_PREDICATES } -> DomainCustomizationClassification.AnnotationOnly
            else -> DomainCustomizationClassification.LogicalStructureChanged
        }
    }

    private companion object {
        val ANNOTATION_PREDICATES: Set<String> = setOf(
            "http://www.w3.org/2000/01/rdf-schema#label",
            "http://www.w3.org/2004/02/skos/core#altLabel",
            "http://www.w3.org/2004/02/skos/core#definition",
            "http://www.w3.org/2004/02/skos/core#closeMatch",
            "http://www.w3.org/2004/02/skos/core#relatedMatch",
        )

        fun stableTriple(triple: GraphTriple): String = listOf(
            triple.subjectResource.value,
            triple.predicate.value,
            when (val term = triple.objectTerm) {
                is RdfResource -> "I:${term.value}"
                is RdfLiteral -> "L:${term.lexicalForm}|${term.datatypeIri?.value.orEmpty()}|${term.languageTag.orEmpty()}"
            },
        ).joinToString("\u001F")

        fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(Locale.ROOT, it) }
    }
}
