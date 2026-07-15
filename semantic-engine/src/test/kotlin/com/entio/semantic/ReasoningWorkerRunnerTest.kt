package com.entio.semantic

import com.entio.core.FactOrigin
import com.entio.core.ReasoningRunStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReasoningWorkerRunnerTest {
    @Test
    fun completesAndReusesMatchingFingerprintResult(): Unit {
        val launcher = RecordingLauncher { request -> responseFor(request.graphFingerprint, request.importClosureFingerprint) }
        val runner = ReasoningWorkerRunner(launcher)
        val request = request("g1", "i1", "c1")

        val first = runner.start(request).await(1000)
        val second = runner.start(request).await(1000)

        assertEquals(ReasoningRunStatus.Completed, first.status)
        assertEquals(ReasoningRunStatus.Completed, second.status)
        assertTrue(second.reused)
        assertEquals(1, launcher.launchCount)
    }

    @Test
    fun changedFingerprintInvalidatesCachedResult(): Unit {
        val launcher = RecordingLauncher { request -> responseFor(request.graphFingerprint, request.importClosureFingerprint) }
        val runner = ReasoningWorkerRunner(launcher)

        runner.start(request("g1", "i1", "c1")).await(1000)
        val result = runner.start(request("g2", "i1", "c1")).await(1000)

        assertEquals(ReasoningRunStatus.Completed, result.status)
        assertEquals(2, launcher.launchCount)
    }

    @Test
    fun handlesTimeoutCancellationMalformedOutputAndCrash(): Unit {
        val timeoutProcess = FakeProcess(output = null)
        val timeout = ReasoningWorkerRunner(ReasoningWorkerLauncher { timeoutProcess })
            .start(request("timeout", "i", "c"))
            .await(10)
        assertEquals(ReasoningRunStatus.TimedOut, timeout.status)
        assertTrue(timeoutProcess.terminated)

        val cancelProcess = FakeProcess(output = null)
        val cancelHandle = ReasoningWorkerRunner(ReasoningWorkerLauncher { cancelProcess })
            .start(request("cancel", "i", "c"))
        assertEquals(ReasoningRunStatus.Cancelled, cancelHandle.cancel().status)
        assertTrue(cancelProcess.terminated)

        val malformed = ReasoningWorkerRunner(ReasoningWorkerLauncher { FakeProcess("not-a-response") })
            .start(request("malformed", "i", "c"))
            .await(1000)
        assertEquals(ReasoningRunStatus.Failed, malformed.status)
        assertTrue(malformed.message.orEmpty().contains("malformed"))

        val crashed = ReasoningWorkerRunner(ReasoningWorkerLauncher { error("worker crashed") })
            .start(request("crashed", "i", "c"))
            .await(1000)
        assertEquals(ReasoningRunStatus.Failed, crashed.status)
        assertTrue(crashed.message.orEmpty().contains("could not start"))
    }

    @Test
    fun discardsLateResultWhenNewerRunStarts(): Unit {
        val firstProcess = FakeProcess(output = responseFor("g1", "i1"), awaitOutput = { null })
        val secondProcess = FakeProcess(responseFor("g2", "i1"))
        var launches = 0
        val runner = ReasoningWorkerRunner(ReasoningWorkerLauncher {
            launches += 1
            if (launches == 1) firstProcess else secondProcess
        })

        val first = runner.start(request("g1", "i1", "c"))
        val second = runner.start(request("g2", "i1", "c"))

        assertEquals(ReasoningRunStatus.Cancelled, first.await(1000).status)
        assertTrue(firstProcess.terminated)
        assertEquals(ReasoningRunStatus.Completed, second.await(1000).status)
    }

    private fun request(graph: String, imports: String, configuration: String): ReasoningWorkerRequest =
        ReasoningWorkerRequest(
            ontologySourcePaths = listOf("ontology.ttl"),
            importSourcePaths = listOf("import.ttl"),
            graphFingerprint = graph,
            importClosureFingerprint = imports,
            reasonerConfigurationFingerprint = configuration,
        )

    private fun responseFor(graph: String, imports: String): String = ReasoningWorkerProtocol.encodeResponse(
        ReasoningWorkerResponse(
            status = ReasoningWorkerResponseStatus.Completed,
            output = ReasoningWorkerNormalizedOutput(
                graphFingerprint = graph,
                importClosureFingerprint = imports,
                facts = listOf(ReasoningWorkerFact("s", "p", "o", FactOrigin.Inferred)),
            ),
        ),
    )

    private class RecordingLauncher(
        private val output: (ReasoningWorkerRequest) -> String,
    ) : ReasoningWorkerLauncher {
        var launchCount: Int = 0

        override fun launch(request: ReasoningWorkerRequest): ReasoningWorkerProcess {
            launchCount += 1
            return FakeProcess(output(request))
        }
    }

    private class FakeProcess(
        private val output: String?,
        private val awaitOutput: () -> String? = { output },
    ) : ReasoningWorkerProcess {
        var terminated: Boolean = false

        override fun awaitOutput(timeoutMillis: Long): String? = awaitOutput()

        override fun terminate() {
            terminated = true
        }
    }
}
