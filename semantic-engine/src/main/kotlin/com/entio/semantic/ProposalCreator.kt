package com.entio.semantic

import com.entio.core.ChangeProposal
import com.entio.core.ChangeProposalStatus
import com.entio.core.ChangeSet
import com.entio.core.EntioProject
import com.entio.core.EntioResult
import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.ProposalBaseline
import com.entio.core.RdfLiteral
import com.entio.core.RdfResource
import com.entio.core.RdfTerm
import com.entio.core.ResolvedOntologySource
import com.entio.core.SourceFileImpact
import com.entio.core.ValidationIssue
import com.entio.core.ValidationSeverity
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest

public class ProposalCreator(
    private val previewer: GraphChangePreviewer = GraphChangePreviewer(),
) {
    public fun createProposal(
        project: EntioProject,
        targetSourceId: String,
        changeSet: ChangeSet,
        id: String,
        title: String,
    ): EntioResult<ChangeProposal> {
        val targetSource = when (val result = findTargetSource(project, targetSourceId)) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }
        val baseline = when (val result = createBaseline(project, targetSource)) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }
        val preview = when (val result = previewer.preview(project.graph, changeSet)) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }

        return EntioResult.Success(
            ChangeProposal(
                id = id,
                title = title,
                targetSourceId = targetSourceId,
                changeSet = changeSet,
                baseline = baseline,
                status = ChangeProposalStatus.Previewed,
                preview = preview,
                sourceFileImpact = SourceFileImpact(
                    affectedPaths = listOf(targetSource.path.toString()),
                ),
            ),
        )
    }

    public fun isCurrent(
        proposal: ChangeProposal,
        currentProject: EntioProject,
    ): EntioResult<Boolean> {
        val targetSource = when (val result = findTargetSource(currentProject, proposal.targetSourceId)) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }
        val currentBaseline = when (val result = createBaseline(currentProject, targetSource)) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }

        return EntioResult.Success(proposal.baseline == currentBaseline)
    }

    private fun findTargetSource(
        project: EntioProject,
        targetSourceId: String,
    ): EntioResult<ResolvedOntologySource> {
        val source = project.resolvedSources.firstOrNull { it.id == targetSourceId }
            ?: return EntioResult.Failure(
                message = "Target ontology source '$targetSourceId' was not found.",
                issues = listOf(
                    ValidationIssue(
                        severity = ValidationSeverity.Error,
                        code = "missing-target-source",
                        message = "Target ontology source '$targetSourceId' was not found.",
                        source = targetSourceId,
                    ),
                ),
            )

        return EntioResult.Success(source)
    }

    private fun createBaseline(
        project: EntioProject,
        targetSource: ResolvedOntologySource,
    ): EntioResult<ProposalBaseline> {
        val sourceFingerprints = project.resolvedSources
            .sortedBy { source -> source.id }
            .map { source ->
                val fingerprint = when (val result = sourceFingerprint(source)) {
                    is EntioResult.Failure -> return result
                    is EntioResult.Success -> result.value
                }
                "${source.id}:${source.path.normalize()}:$fingerprint"
            }
        val targetSourceFingerprint = when (val result = sourceFingerprint(targetSource)) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }
        val graphFingerprint = graphFingerprint(project.graph)

        return EntioResult.Success(
            ProposalBaseline(
                projectFingerprint = sha256(sourceFingerprints.joinToString(separator = "\n") + "\n$graphFingerprint"),
                targetSourceId = targetSource.id,
                targetSourcePath = targetSource.path.toString(),
                targetSourceFingerprint = targetSourceFingerprint,
                graphFingerprint = graphFingerprint,
            ),
        )
    }

    private fun sourceFingerprint(source: ResolvedOntologySource): EntioResult<String> =
        try {
            EntioResult.Success(sha256(Files.readAllBytes(source.path)))
        } catch (exception: IOException) {
            EntioResult.Failure(
                message = "Ontology source '${source.id}' could not be fingerprinted.",
                issues = listOf(
                    ValidationIssue(
                        severity = ValidationSeverity.Error,
                        code = "source-fingerprint-failed",
                        message = "Ontology source '${source.id}' could not be fingerprinted.",
                        source = source.id,
                    ),
                ),
                cause = exception,
            )
        }

    private fun graphFingerprint(graph: GraphState): String =
        sha256(
            graph.triples
                .map(::tripleKey)
                .sorted()
                .joinToString(separator = "\n"),
        )

    private fun tripleKey(triple: GraphTriple): String =
        listOf(
            resourceKey(triple.subjectResource),
            iriKey(triple.predicate),
            termKey(triple.objectTerm),
        ).joinToString(separator = "\u001F")

    private fun termKey(term: RdfTerm): String =
        when (term) {
            is RdfLiteral -> listOf(
                "literal",
                term.lexicalForm,
                term.datatypeIri?.value.orEmpty(),
                term.languageTag.orEmpty(),
            ).joinToString(separator = "\u001E")
            is RdfResource -> resourceKey(term)
        }

    private fun resourceKey(resource: RdfResource): String =
        "${resource::class.qualifiedName}:${resource.value}"

    private fun iriKey(iri: Iri): String = "iri:${iri.value}"

    private fun sha256(value: String): String =
        sha256(value.toByteArray(Charsets.UTF_8))

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
