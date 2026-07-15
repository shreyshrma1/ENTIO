package com.entio.semantic

import com.entio.core.ReasoningRunStatus
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

public fun interface ReasoningWorkerLauncher {
    public fun launch(request: ReasoningWorkerRequest): ReasoningWorkerProcess
}

public interface ReasoningWorkerProcess {
    public fun awaitOutput(timeoutMillis: Long): String?

    public fun terminate()
}

public data class ReasoningWorkerRunResult(
    public val status: ReasoningRunStatus,
    public val response: ReasoningWorkerResponse? = null,
    public val message: String? = null,
    public val reused: Boolean = false,
)

public class ReasoningWorkerRunner(
    private val launcher: ReasoningWorkerLauncher,
) {
    private val cache = mutableMapOf<CacheKey, ReasoningWorkerResponse>()
    private var generation: Long = 0
    private var activeRun: ReasoningWorkerRunHandle? = null

    @Synchronized
    public fun start(request: ReasoningWorkerRequest): ReasoningWorkerRunHandle {
        generation += 1
        activeRun?.invalidateAsStale()
        val key = CacheKey.from(request)
        val cached = cache[key]
        if (cached != null) {
            return ReasoningWorkerRunHandle(
                runner = this,
                generation = generation,
                request = request,
                process = null,
                cachedResponse = cached,
            ).also { activeRun = it }
        }

        return try {
            ReasoningWorkerRunHandle(
                runner = this,
                generation = generation,
                request = request,
                process = launcher.launch(request),
            ).also { activeRun = it }
        } catch (exception: RuntimeException) {
            ReasoningWorkerRunHandle(
                runner = this,
                generation = generation,
                request = request,
                process = null,
                startupFailure = exception,
            ).also { activeRun = it }
        }
    }

    @Synchronized
    internal fun isCurrent(runGeneration: Long): Boolean = runGeneration == generation

    @Synchronized
    internal fun cache(request: ReasoningWorkerRequest, response: ReasoningWorkerResponse) {
        cache[CacheKey.from(request)] = response
    }

    internal data class CacheKey(
        val graphFingerprint: String,
        val importClosureFingerprint: String,
        val reasonerConfigurationFingerprint: String,
    ) {
        companion object {
            fun from(request: ReasoningWorkerRequest): CacheKey = CacheKey(
                graphFingerprint = request.graphFingerprint,
                importClosureFingerprint = request.importClosureFingerprint,
                reasonerConfigurationFingerprint = request.reasonerConfigurationFingerprint,
            )
        }
    }
}

public class ReasoningWorkerRunHandle internal constructor(
    private val runner: ReasoningWorkerRunner,
    private val generation: Long,
    private val request: ReasoningWorkerRequest,
    private val process: ReasoningWorkerProcess?,
    private val cachedResponse: ReasoningWorkerResponse? = null,
    private val startupFailure: Throwable? = null,
) {
    private var terminalResult: ReasoningWorkerRunResult? = null
    private var invalidated = false

    @Synchronized
    public fun await(timeoutMillis: Long): ReasoningWorkerRunResult {
        terminalResult?.let { return it }
        if (timeoutMillis < 0) return finish(ReasoningWorkerRunResult(ReasoningRunStatus.Failed, message = "Timeout must not be negative."))
        if (invalidated || !runner.isCurrent(generation)) {
            process?.terminate()
            return finish(ReasoningWorkerRunResult(ReasoningRunStatus.Cancelled, message = "Stale reasoning result discarded."))
        }
        cachedResponse?.let { return finish(ReasoningWorkerRunResult(ReasoningRunStatus.Completed, it, reused = true)) }
        startupFailure?.let {
            return finish(ReasoningWorkerRunResult(ReasoningRunStatus.Failed, message = "Reasoning worker could not start: ${it.message ?: it::class.simpleName}."))
        }

        val output = process?.awaitOutput(timeoutMillis)
        if (output == null) {
            process?.terminate()
            return finish(ReasoningWorkerRunResult(ReasoningRunStatus.TimedOut, message = "Reasoning worker timed out."))
        }
        val decoded = ReasoningWorkerProtocol.decodeResponse(output)
        if (decoded is com.entio.core.EntioResult.Failure) {
            return finish(ReasoningWorkerRunResult(ReasoningRunStatus.Failed, message = "Reasoning worker returned malformed output: ${decoded.message}"))
        }
        val response = (decoded as com.entio.core.EntioResult.Success).value
        if (!runner.isCurrent(generation)) {
            process?.terminate()
            return finish(ReasoningWorkerRunResult(ReasoningRunStatus.Cancelled, message = "Stale reasoning result discarded."))
        }
        if (response.status == ReasoningWorkerResponseStatus.Completed && !matchesRequest(response)) {
            return finish(ReasoningWorkerRunResult(ReasoningRunStatus.Failed, message = "Reasoning worker returned fingerprints that do not match the request."))
        }
        val result = when (response.status) {
            ReasoningWorkerResponseStatus.Completed -> ReasoningWorkerRunResult(ReasoningRunStatus.Completed, response)
            ReasoningWorkerResponseStatus.Failed -> ReasoningWorkerRunResult(ReasoningRunStatus.Failed, response, response.failure?.message)
            ReasoningWorkerResponseStatus.Cancelled -> ReasoningWorkerRunResult(ReasoningRunStatus.Cancelled, response, response.failure?.message)
            ReasoningWorkerResponseStatus.TimedOut -> ReasoningWorkerRunResult(ReasoningRunStatus.TimedOut, response, response.failure?.message)
        }
        if (result.status == ReasoningRunStatus.Completed) runner.cache(request, response)
        return finish(result)
    }

    @Synchronized
    public fun cancel(): ReasoningWorkerRunResult {
        terminalResult?.let { return it }
        invalidated = true
        process?.terminate()
        return finish(ReasoningWorkerRunResult(ReasoningRunStatus.Cancelled, message = "Reasoning worker cancelled."))
    }

    @Synchronized
    internal fun invalidateAsStale() {
        if (terminalResult == null) {
            invalidated = true
            process?.terminate()
        }
    }

    private fun matchesRequest(response: ReasoningWorkerResponse): Boolean {
        val output = response.output ?: return false
        return output.graphFingerprint == request.graphFingerprint &&
            output.importClosureFingerprint == request.importClosureFingerprint
    }

    private fun finish(result: ReasoningWorkerRunResult): ReasoningWorkerRunResult {
        terminalResult = result
        return result
    }
}

public class ProcessBuilderReasoningWorkerLauncher(
    private val command: List<String>,
) : ReasoningWorkerLauncher {
    override fun launch(request: ReasoningWorkerRequest): ReasoningWorkerProcess {
        val process = ProcessBuilder(command).start()
        BufferedWriter(OutputStreamWriter(process.outputStream, StandardCharsets.UTF_8)).use { writer ->
            writer.write(ReasoningWorkerProtocol.encodeRequest(request))
            writer.flush()
        }
        return ProcessBuilderReasoningWorkerProcess(process)
    }
}

private class ProcessBuilderReasoningWorkerProcess(
    private val process: Process,
) : ReasoningWorkerProcess {
    override fun awaitOutput(timeoutMillis: Long): String? {
        if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) return null
        return process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }

    override fun terminate() {
        if (process.isAlive) process.destroyForcibly()
    }
}
