package com.entio.cli

import com.entio.core.ApplyProposalResult
import com.entio.core.ChangeProposalStatus
import com.entio.core.EntioResult
import com.entio.core.ExternalDependencySelection
import com.entio.core.ExternalDependencySet
import com.entio.core.ExternalEntityKind
import com.entio.core.ExternalProposalIntent
import com.entio.core.Iri
import com.entio.core.RollbackResult
import com.entio.core.SemanticEquivalenceResult
import com.entio.diff.ProposalDiffGenerator
import com.entio.semantic.ExternalDependencyReviewer
import com.entio.semantic.ExternalProposalPreparer
import com.entio.semantic.ExternalFiboCatalogSession
import com.entio.semantic.FiboCatalogLoader
import com.entio.semantic.ProjectLoader
import com.entio.semantic.ProposalApplier
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Phase5EndToEndRegressionTest {
    private val projectLoader = ProjectLoader()

    @Test
    fun copiedFixtureRunsOfflineExternalWorkflowWithoutMutatingCommittedAssets(): Unit {
        val repositoryRoot = repositoryRoot()
        val packageRoot = repositoryRoot.resolve("external-ontologies/fibo")
        val packageBefore = snapshotFiles(packageRoot)
        val fixture = copyFixture(repositoryRoot)
        val originalSource = fixture.ontologyPath.readText()

        val sources = runCli("external-sources", fixture.projectRoot.toString())
        assertEquals(0, sources.exitCode, sources.out)
        assertTrue(sources.out.contains("\"projectChanged\":false"), sources.out)

        val manifest = runCli("external-manifest", fixture.projectRoot.toString())
        assertEquals(0, manifest.exitCode, manifest.out)
        assertTrue(manifest.out.contains("\"release\":\"master_2026Q2\""), manifest.out)
        assertTrue(manifest.out.contains("\"availability\":\"available\""), manifest.out)

        val session = loadSession(fixture.packageRoot, fixture.projectRoot)
        val curated = session.browseCuratedModules(pageSize = 1)
        assertEquals(15, curated.totalCount)
        assertTrue(curated.hasNext)
        val selectedModule = curated.items.first()
        val modulePage = session.browseModule(selectedModule.ontologyIri, pageSize = 5)
        assertTrue(modulePage.items.isNotEmpty())

        val browse = runCli(
            "external-browse",
            fixture.projectRoot.toString(),
            "--mode",
            "module",
            "--module-iri",
            selectedModule.ontologyIri.value,
            "--page-size",
            "5",
        )
        assertEquals(0, browse.exitCode, browse.out)
        assertTrue(browse.out.contains("\"noSilentTruncation\":true"), browse.out)

        val selected = session.allElements().first { it.kind == ExternalEntityKind.Class }
        val selectedIri = selected.descriptor.descriptor.common.entity as Iri
        val selectedLabel = selected.descriptor.descriptor.common.preferredLabel?.lexicalForm ?: selectedIri.value
        val search = runCli("external-search", fixture.projectRoot.toString(), selectedLabel, "--kind", "Class", "--page-size", "1")
        assertEquals(0, search.exitCode, search.out)
        assertTrue(search.out.contains("\"schema\":\"fibo-schema-search-v1\""), search.out)
        assertTrue(search.out.contains("\"scoreBreakdown\":{"), search.out)
        assertTrue(search.out.contains("\"reasons\":["), search.out)

        val descriptor = runCli("external-describe", fixture.projectRoot.toString(), selectedIri.value, "--kind", "Class")
        assertEquals(0, descriptor.exitCode, descriptor.out)
        assertTrue(descriptor.out.contains("\"locality\":\"External\""), descriptor.out)
        assertTrue(descriptor.out.contains(selectedIri.value), descriptor.out)

        val dependencyResponse = runCli("external-dependencies", fixture.projectRoot.toString(), selectedIri.value, "--kind", "Class")
        assertEquals(1, dependencyResponse.exitCode, dependencyResponse.out)
        assertTrue(dependencyResponse.out.contains("\"requiresExplicitApproval\":true"), dependencyResponse.out)
        assertTrue(dependencyResponse.out.contains("\"selection\":\"Missing\""), dependencyResponse.out)

        val blockedProposal = runCli(
            "external-proposal",
            fixture.projectRoot.toString(),
            "simple",
            TARGET_ONTOLOGY,
            "--intent",
            "reuse-class",
            "--external-iri",
            selectedIri.value,
            "--kind",
            "Class",
        )
        assertEquals(1, blockedProposal.exitCode, blockedProposal.out)
        assertTrue(blockedProposal.out.contains("unapproved-required-dependency"), blockedProposal.out)
        assertEquals(originalSource, fixture.ontologyPath.readText())

        val currentProject = loadProject(fixture.projectRoot)
        val reviewed = ExternalDependencyReviewer().review(session, selected, currentProject)
        val approvedDependencies = ExternalDependencySet(
            dependencies = reviewed.dependencies.map { dependency ->
                if (dependency.visibility.name == "UserVisible" &&
                    dependency.requirement.name == "Required" &&
                    dependency.selection == ExternalDependencySelection.Missing
                ) {
                    dependency.copy(selection = ExternalDependencySelection.NewlySelected)
                } else {
                    dependency
                }
            },
        )
        val reuseIntent = ExternalProposalIntent.ReuseExternalClass(
            classIri = selectedIri,
            sourceId = "fibo",
            dependencies = approvedDependencies,
        )
        val prepared = assertIs<EntioResult.Success<com.entio.core.ChangeProposal>>(
            ExternalProposalPreparer().prepare(
                project = currentProject,
                targetSourceId = "simple",
                targetOntologyIri = Iri(TARGET_ONTOLOGY),
                intent = reuseIntent,
                id = "phase5-reuse",
                title = "Phase 5 external reuse",
            ),
        ).value
        assertNotNull(prepared.preview)
        assertTrue(prepared.changeSet.changes.isNotEmpty())
        val withDiff = assertIs<EntioResult.Success<com.entio.core.ChangeProposal>>(
            ProposalDiffGenerator().attachDiff(prepared, currentProject.graph),
        ).value
        assertTrue(withDiff.diff?.entries?.isNotEmpty() == true)
        assertEquals(originalSource, fixture.ontologyPath.readText())

        val subclass = ExternalProposalPreparer().prepare(
            project = currentProject,
            targetSourceId = "simple",
            targetOntologyIri = Iri(TARGET_ONTOLOGY),
            intent = ExternalProposalIntent.CreateLocalSubclassOfExternalClass(
                localClassIri = Iri("https://example.com/entio/simple#LocalExternalClass"),
                externalSuperclassIri = selectedIri,
                sourceId = "fibo",
                dependencies = approvedDependencies,
            ),
            id = "phase5-subclass",
            title = "Phase 5 local subclass",
        )
        val subclassProposal = assertIs<EntioResult.Success<com.entio.core.ChangeProposal>>(subclass).value
        assertTrue(subclassProposal.changeSet.changes.any { change -> change.triple.subjectResource == Iri("https://example.com/entio/simple#LocalExternalClass") })
        assertEquals(originalSource, fixture.ontologyPath.readText())

        val rejected = prepared.copy(status = ChangeProposalStatus.Rejected)
        assertEquals(ChangeProposalStatus.Rejected, rejected.status)
        assertEquals(originalSource, fixture.ontologyPath.readText())

        val appliedFixture = copyFixture(repositoryRoot)
        val appliedProject = loadProject(appliedFixture.projectRoot)
        val appliedSession = loadSession(appliedFixture.packageRoot, appliedFixture.projectRoot)
        val appliedElement = appliedSession.find(selectedIri, ExternalEntityKind.Class) ?: error("Selected external class was not copied.")
        val appliedDependencies = ExternalDependencyReviewer().review(appliedSession, appliedElement, appliedProject)
        val appliedIntent = reuseIntent.copy(
            dependencies = ExternalDependencySet(
                appliedDependencies.dependencies.map { dependency ->
                    if (dependency.visibility.name == "UserVisible" && dependency.requirement.name == "Required" && dependency.selection == ExternalDependencySelection.Missing) {
                        dependency.copy(selection = ExternalDependencySelection.NewlySelected)
                    } else dependency
                },
            ),
        )
        val appliedProposal = assertIs<EntioResult.Success<com.entio.core.ChangeProposal>>(
            ExternalProposalPreparer().prepare(
                project = appliedProject,
                targetSourceId = "simple",
                targetOntologyIri = Iri(TARGET_ONTOLOGY),
                intent = appliedIntent,
                id = "phase5-applied",
                title = "Phase 5 applied reuse",
            ),
        ).value.copy(status = ChangeProposalStatus.Approved)
        val applied = ProposalApplier().applyProposal(appliedFixture.projectRoot, appliedProposal)
        assertIs<ApplyProposalResult.Applied>(applied)
        val reloaded = loadProject(appliedFixture.projectRoot)
        assertTrue(reloaded.graph.triples.any { triple -> triple.predicate == OWL_IMPORTS && triple.objectTerm == appliedElement.descriptor.moduleIri })

        val rollbackFixture = copyFixture(repositoryRoot)
        val rollbackProject = loadProject(rollbackFixture.projectRoot)
        val rollbackSession = loadSession(rollbackFixture.packageRoot, rollbackFixture.projectRoot)
        val rollbackElement = rollbackSession.find(selectedIri, ExternalEntityKind.Class) ?: error("Selected external class was not copied.")
        val rollbackDependencies = ExternalDependencyReviewer().review(rollbackSession, rollbackElement, rollbackProject)
        val rollbackIntent = reuseIntent.copy(
            dependencies = ExternalDependencySet(
                rollbackDependencies.dependencies.map { dependency ->
                    if (dependency.visibility.name == "UserVisible" && dependency.requirement.name == "Required" && dependency.selection == ExternalDependencySelection.Missing) {
                        dependency.copy(selection = ExternalDependencySelection.NewlySelected)
                    } else dependency
                },
            ),
        )
        val rollbackProposal = assertIs<EntioResult.Success<com.entio.core.ChangeProposal>>(
            ExternalProposalPreparer().prepare(
                project = rollbackProject,
                targetSourceId = "simple",
                targetOntologyIri = Iri(TARGET_ONTOLOGY),
                intent = rollbackIntent,
                id = "phase5-rollback",
                title = "Phase 5 rollback",
            ),
        ).value.copy(status = ChangeProposalStatus.Approved)
        val rollbackBytes = rollbackFixture.ontologyPath.readBytes()
        val rolledBack = ProposalApplier(
            compareGraphs = { _, _ -> SemanticEquivalenceResult.NotEquivalent("forced regression rollback") },
        ).applyProposal(rollbackFixture.projectRoot, rollbackProposal)
        val failed = assertIs<ApplyProposalResult.Failed>(rolledBack)
        assertIs<RollbackResult.Restored>(failed.rollback)
        assertEquals(rollbackBytes.toList(), rollbackFixture.ontologyPath.readBytes().toList())

        assertEquals(packageBefore, snapshotFiles(packageRoot))
    }

    private fun loadSession(packageRoot: Path, projectRoot: Path): ExternalFiboCatalogSession =
        assertIs<EntioResult.Success<ExternalFiboCatalogSession>>(FiboCatalogLoader(packageRoot).load(loadProject(projectRoot))).value

    private fun loadProject(root: Path): com.entio.core.EntioProject =
        assertIs<EntioResult.Success<com.entio.core.EntioProject>>(projectLoader.loadProject(root)).value

    private fun copyFixture(repositoryRoot: Path): Fixture {
        val root = Files.createTempDirectory("entio-phase-5-e2e")
        val projectRoot = root.resolve("project")
        copyTree(repositoryRoot.resolve("examples/simple-ontology"), projectRoot)
        val packageRoot = root.resolve("external-ontologies/fibo")
        listOf(
            "manifest.yaml",
            "ATTRIBUTION.md",
            "indexes/catalog-v1.jsonl",
            "indexes/catalog-metadata-v1.json",
            "indexes/ontology-iri-map-v1.json",
        ).forEach { relative ->
            val target = packageRoot.resolve(relative)
            Files.createDirectories(target.parent)
            Files.copy(repositoryRoot.resolve("external-ontologies/fibo").resolve(relative), target, StandardCopyOption.REPLACE_EXISTING)
        }
        return Fixture(root, projectRoot, packageRoot, projectRoot.resolve("ontology/simple.ttl"))
    }

    private fun copyTree(source: Path, target: Path): Unit {
        Files.walk(source).use { paths ->
            paths.forEach { path ->
                val destination = target.resolve(source.relativize(path).toString())
                if (Files.isDirectory(path)) Files.createDirectories(destination)
                else Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private fun snapshotFiles(root: Path): Map<String, String> = buildMap {
        Files.walk(root).use { paths ->
            paths.filter(Files::isRegularFile).forEach { path ->
                put(root.relativize(path).toString(), sha256(path.readBytes()))
            }
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun runCli(vararg args: String): CliRun {
        val out = StringWriter()
        val err = StringWriter()
        val exitCode = EntioCli().execute(args.toList().toTypedArray(), PrintWriter(out, true), PrintWriter(err, true))
        return CliRun(exitCode, out.toString(), err.toString())
    }

    private fun repositoryRoot(): Path {
        var current = Path.of("").toAbsolutePath().normalize()
        while (true) {
            if (Files.isRegularFile(current.resolve("examples/simple-ontology/entio.yaml")) &&
                Files.isDirectory(current.resolve("external-ontologies/fibo"))
            ) return current
            current = current.parent ?: error("Could not locate the Entio repository root.")
        }
    }

    private data class Fixture(
        val root: Path,
        val projectRoot: Path,
        val packageRoot: Path,
        val ontologyPath: Path,
    )

    private data class CliRun(val exitCode: Int, val out: String, val err: String)

    private companion object {
        private const val TARGET_ONTOLOGY = "https://example.com/entio/simple"
        private val OWL_IMPORTS = Iri("http://www.w3.org/2002/07/owl#imports")
    }
}
