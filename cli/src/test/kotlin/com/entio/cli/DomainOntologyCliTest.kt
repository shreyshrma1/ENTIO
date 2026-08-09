package com.entio.cli

import com.entio.core.DomainOntologyProfile
import com.entio.semantic.DomainProfileRepository
import com.entio.semantic.DomainProfileService
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DomainOntologyCliTest {
    @Test
    fun inactiveProfileStatusAndActivationPreviewAreMachineReadableAndReadOnly(): Unit {
        val root = copiedProject()
        val before = Files.readAllBytes(root.resolve("ontology/simple.ttl")).toList()

        val status = runCli("domain-profile-status", root.toString())
        assertEquals(0, status.exitCode, status.out)
        assertTrue(status.out.contains("\"availability\":\"inactive\""), status.out)
        assertTrue(status.out.contains("\"selected\":false"), status.out)

        val sources = runCli("domain-sources")
        assertEquals(0, sources.exitCode, sources.out)
        assertTrue(sources.out.contains("\"retrievalAvailability\":"), sources.out)
        assertTrue(sources.out.contains("\"sourceId\":\"fibo\""), sources.out)

        val preview = runCli("domain-activation-preview", root.toString())
        assertEquals(0, preview.exitCode, preview.out)
        assertTrue(preview.out.contains("\"readOnly\":true"), preview.out)
        assertTrue(preview.out.contains("\"sourceId\":\"fibo\""), preview.out)
        assertEquals(before, Files.readAllBytes(root.resolve("ontology/simple.ttl")).toList())
    }

    @Test
    fun activeProfileExposesFoundationFullCorpusRecommendationsDetailsDependenciesAndProposalPreview(): Unit {
        val root = copiedProject()
        activateProfile(root)
        val agreement = "https://spec.edmcouncil.org/fibo/ontology/FND/Agreements/Agreements/Agreement"
        val financialRecord = "https://spec.edmcouncil.org/fibo/ontology/FND/Arrangements/Documents/FinancialRecord"

        val status = runCli("domain-profile-status", root.toString())
        assertEquals(0, status.exitCode, status.out)
        assertTrue(status.out.contains("\"availability\":\"active\""), status.out)
        assertTrue(status.out.contains("\"selected\":true"), status.out)

        val foundation = runCli("domain-foundation", root.toString())
        assertEquals(0, foundation.exitCode, foundation.out)
        assertTrue(foundation.out.contains("\"groups\":["), foundation.out)
        val foundationElementId = Regex("\"elementId\":\"([^\"]+)\"").find(foundation.out)!!.groupValues[1]
        val foundationPlan = runCli("domain-foundation-plan", root.toString(), "--element-id", foundationElementId)
        assertEquals(0, foundationPlan.exitCode, foundationPlan.out)
        assertTrue(foundationPlan.out.contains("\"readOnly\":true"), foundationPlan.out)
        assertTrue(foundationPlan.out.contains("\"batches\":["), foundationPlan.out)

        val recommendations = runCli(
            "domain-recommendations",
            root.toString(),
            "agreement",
            "--kind",
            "Class",
            "--broad-search",
        )
        assertEquals(0, recommendations.exitCode, recommendations.out)
        assertTrue(recommendations.out.contains("\"rankingContract\":\"domain-ranking-v1\""), recommendations.out)
        assertTrue(recommendations.out.contains(agreement), recommendations.out)

        val details = runCli("domain-describe", root.toString(), agreement)
        assertEquals(0, details.exitCode, details.out)
        assertTrue(details.out.contains("\"sourceStatementCount\":"), details.out)
        assertTrue(details.out.contains("\"projectStatementCount\":"), details.out)
        assertTrue(details.out.contains("\"classification\":"), details.out)

        val dependencies = runCli("domain-dependencies", root.toString(), financialRecord)
        assertEquals(0, dependencies.exitCode, dependencies.out)
        assertTrue(dependencies.out.contains("\"dependencies\":["), dependencies.out)
        assertTrue(dependencies.out.contains("RequiredStructuralDependency"), dependencies.out)

        val proposal = runCli("domain-proposal", root.toString(), financialRecord)
        assertEquals(0, proposal.exitCode, proposal.out)
        assertTrue(proposal.out.contains("\"readOnly\":true"), proposal.out)
        assertTrue(proposal.out.contains("\"targetSourceId\":\"fibo-reuse\""), proposal.out)
        assertTrue(proposal.out.contains("\"sourceIriStatic\":true"), proposal.out)
        assertTrue(Files.readString(root.resolve("ontology/fibo-reuse.ttl")).contains("Statements are added only through approved proposals"))
    }

    @Test
    fun profileGatedCommandsReturnStructuredFailureWhenInactive(): Unit {
        val root = copiedProject()
        val recommendation = runCli("domain-search", root.toString(), "agreement")
        assertEquals(1, recommendation.exitCode, recommendation.out)
        assertTrue(recommendation.out.contains("\"code\":\"domain-profile-inactive\""), recommendation.out)
    }

    private fun activateProfile(root: Path): Unit {
        val repository = DomainProfileRepository()
        val profile = (repository.serialize(DomainOntologyProfile()) as com.entio.core.EntioResult.Success).value
        root.resolve(".entio").createDirectories()
        Files.writeString(root.resolve(".entio/domain-profile.yaml"), profile)
        Files.writeString(root.resolve("ontology/fibo-reuse.ttl"), DomainProfileService.EMPTY_MANAGED_SOURCE)
    }

    private fun copiedProject(): Path {
        val source = repoRoot().resolve("examples/simple-ontology")
        val root = Files.createTempDirectory("entio-domain-cli")
        root.resolve("ontology").createDirectories()
        Files.copy(source.resolve("entio.yaml"), root.resolve("entio.yaml"))
        Files.copy(source.resolve("ontology/simple.ttl"), root.resolve("ontology/simple.ttl"))
        Files.copy(source.resolve("ontology/shapes.ttl"), root.resolve("ontology/shapes.ttl"))
        return root
    }

    private fun runCli(vararg args: String): CliRun {
        val out = StringWriter()
        val err = StringWriter()
        val exitCode = EntioCli().execute(args.toList().toTypedArray(), PrintWriter(out, true), PrintWriter(err, true))
        return CliRun(exitCode, out.toString(), err.toString())
    }

    private fun repoRoot(): Path = generateSequence(Path.of("").toAbsolutePath().normalize()) { it.parent }
        .first { Files.isDirectory(it.resolve("external-ontologies/domain-search")) }

    private data class CliRun(val exitCode: Int, val out: String, val err: String)
}
