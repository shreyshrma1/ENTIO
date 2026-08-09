package com.entio.web

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocumentAndAiPhase13IsolationTest {
    @Test
    fun `document ingestion retains Phase 12 retrieval and has no Phase 13 model or index authority`(): Unit {
        val ingestion = productionSources("web-server/src/main/kotlin/com/entio/web/ingestion")

        assertTrue(ingestion.contains("DocumentOntologyRetrievalService") || ingestion.contains("DocumentRetrievalContextFactory"))
        assertTrue(ingestion.contains("FiboCatalogLoader"))
        assertFalse(ingestion.contains("DomainRecommendationService"))
        assertFalse(ingestion.contains("DomainSearchIndex"))
        assertFalse(ingestion.contains("LocalSentenceEmbeddingService"))
    }

    @Test
    fun `assistant retains bounded compatibility adapter and has no Phase 13 recommendation tools`(): Unit {
        val assistant = productionSources("web-server/src/main/kotlin/com/entio/web/ai")
        val proposalService = Files.readString(repositoryRoot().resolve("web-server/src/main/kotlin/com/entio/web/ai/AiProposalService.kt"))

        assertTrue(proposalService.contains("FiboWebService"))
        assertTrue(proposalService.contains("take(8)"))
        assertTrue(proposalService.contains("take(20)"))
        assertFalse(assistant.contains("DomainRecommendationService"))
        assertFalse(assistant.contains("DomainSearchIndex"))
        assertFalse(assistant.contains("LocalSentenceEmbeddingService"))
        assertFalse(assistant.contains("domain-profile.yaml"))
    }

    private fun productionSources(relative: String): String = Files.walk(repositoryRoot().resolve(relative)).use { paths ->
        paths.filter { Files.isRegularFile(it) && it.extension == "kt" }
            .sorted()
            .map(Files::readString)
            .toList()
            .joinToString("\n")
    }

    private fun repositoryRoot(): Path = generateSequence(Path.of("").toAbsolutePath().normalize()) { it.parent }
        .first { Files.isDirectory(it.resolve("web-server/src/main/kotlin")) }
}
