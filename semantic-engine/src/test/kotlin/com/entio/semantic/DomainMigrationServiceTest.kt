package com.entio.semantic

import com.entio.core.DomainOntologyMigrationStatus
import com.entio.core.DomainOntologyProfileIdentity
import com.entio.core.EntioResult
import com.entio.core.Iri
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DomainMigrationServiceTest {
    private val searchRoot = Path.of("..", DomainCorpusIdentity.OUTPUT_RELATIVE_PATH).toAbsolutePath().normalize()
    private val agreement = "https://spec.edmcouncil.org/fibo/ontology/FND/Agreements/Agreements/Agreement"
    private val borrower = "https://spec.edmcouncil.org/fibo/ontology/FBC/DebtAndEquities/Debt/hasBorrower"
    private val unknown = "https://spec.edmcouncil.org/fibo/ontology/LEGACY/UnknownConcept"

    @Test
    fun `detects no recognized ambiguous and unsupported legacy reuse without inferring history`(): Unit {
        assertEquals(DomainOntologyMigrationStatus.NoExistingReuse, detect("ex:Local a owl:Class .").status)

        val recognized = detect(
            """
            <$agreement> a owl:Class .
            ex:LocalAgreement a owl:Class ; rdfs:subClassOf <$agreement> .
            ex:borrower <$borrower> ex:bank .
            """.trimIndent(),
        )
        assertEquals(DomainOntologyMigrationStatus.ExistingReuseRecognized, recognized.status)
        assertEquals(DomainOntologyProfileIdentity.RELEASE, recognized.verifiedCurrentRelease)
        assertNull(recognized.historicalRelease)
        assertTrue(recognized.localExtensionCount >= 2)
        assertEquals(listOf(Iri(agreement)), recognized.provenanceSeedCandidates)
        assertFalse(recognized.provenanceSeedingEligible)

        val ambiguous = detect("<$agreement> a owl:Class . <$unknown> a owl:Class .")
        assertEquals(DomainOntologyMigrationStatus.ExistingReuseAmbiguous, ambiguous.status)
        assertEquals(listOf(Iri(unknown)), ambiguous.unsupportedIris)
        assertNull(ambiguous.verifiedCurrentRelease)

        val unsupported = detect("<$unknown> a owl:Class .")
        assertEquals(DomainOntologyMigrationStatus.ExistingReuseUnsupported, unsupported.status)
        assertTrue(unsupported.recognizedIris.isEmpty())
        assertNull(unsupported.historicalRelease)
    }

    @Test
    fun `retains staged proposed rejected and rolled back historical baselines`(): Unit {
        val root = project("<$agreement> a owl:Class .")
        val project = load(root)
        val work = DomainMigrationWorkState.entries.map { state ->
            DomainMigrationOpenWork(state, setOf(Iri(agreement)))
        }

        val report = service().detect(project, work)

        assertEquals(DomainMigrationWorkState.entries, report.openWork.map(DomainMigrationOpenWork::state))
        assertTrue(report.openWorkBaselineRetained)
        assertTrue(report.openWork.all(DomainMigrationOpenWork::baselineRetained))
        assertTrue(report.issues.any { it.contains("baselines are retained") })
    }

    @Test
    fun `migration preview and activation preserve RDF and failed configuration verification rolls back`(): Unit {
        val root = project("<$agreement> a owl:Class .")
        val before = load(root).graph
        val historical = root.resolve(DomainOntologyProfileIdentity.PROVENANCE_PATH)
        historical.parent.createDirectories()
        historical.writeText("legacy historical record retained verbatim\n")
        val historicalBefore = Files.readAllBytes(historical)
        val repository = DomainProfileRepository()
        val transactions = DomainFileTransactionManager(repository)
        val profiles = DomainProfileService(repository, transactions)
        val service = DomainMigrationService.open(searchRoot, profiles)

        val preview = assertIs<EntioResult.Success<DomainMigrationPreview>>(
            service.preview(root, load(root)),
        ).value
        assertFalse(preview.movesExistingStatements)
        assertFalse(preview.seedsProvenance)
        assertFalse(root.resolve(DomainOntologyProfileIdentity.PROFILE_PATH).exists())
        assertEquals(before, load(root).graph)

        val prepared = assertIs<EntioResult.Success<PreparedDomainTransaction>>(profiles.prepareActivation(root)).value
        assertIs<EntioResult.Success<Unit>>(transactions.commit(prepared))
        assertEquals(before, load(root).graph)
        assertTrue(historicalBefore.contentEquals(Files.readAllBytes(historical)))

        val failedRoot = project("<$agreement> a owl:Class .")
        val failed = assertIs<EntioResult.Success<PreparedDomainTransaction>>(profiles.prepareActivation(failedRoot)).value
        val failure = transactions.commit(failed) {
            EntioResult.Failure("forced", emptyList())
        }
        assertIs<EntioResult.Failure>(failure)
        assertFalse(failedRoot.resolve(DomainOntologyProfileIdentity.PROFILE_PATH).exists())
        assertFalse(failedRoot.resolve(DomainOntologyProfileIdentity.MANAGED_SOURCE_PATH).exists())
        assertEquals(load(failedRoot).graph, before)
    }

    private fun detect(body: String): DomainMigrationReport {
        val project = load(project(body))
        return service().detect(project)
    }

    private fun service(): DomainMigrationService = DomainMigrationService.open(searchRoot)

    private fun load(root: Path) = assertIs<EntioResult.Success<com.entio.core.EntioProject>>(
        ProjectLoader().loadProject(root),
    ).value

    private fun project(body: String): Path {
        val root = Files.createTempDirectory("entio-domain-migration")
        root.resolve("ontology").createDirectories()
        root.resolve("entio.yaml").writeText(
            """
            name: migration-fixture
            iriNamespace: https://example.com/migration#
            ontologySources:
              - id: main
                path: ontology/main.ttl
                format: turtle
                roles:
                  - ontology
                  - data
            """.trimIndent() + "\n",
        )
        root.resolve("ontology/main.ttl").writeText(
            """
            @prefix ex: <https://example.com/migration#> .
            @prefix owl: <http://www.w3.org/2002/07/owl#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            $body
            """.trimIndent() + "\n",
        )
        return root
    }
}
