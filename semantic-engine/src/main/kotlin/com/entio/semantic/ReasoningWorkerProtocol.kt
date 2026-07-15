package com.entio.semantic

import com.entio.core.EntioResult
import com.entio.core.FactOrigin
import com.entio.core.ValidationIssue
import com.entio.core.ValidationSeverity
import java.nio.charset.StandardCharsets
import java.util.Base64

public const val REASONING_WORKER_PROTOCOL_VERSION: Int = 1

public data class ReasoningWorkerRequest(
    public val ontologySourcePaths: List<String>,
    public val importSourcePaths: List<String>,
    public val graphFingerprint: String,
    public val importClosureFingerprint: String,
    public val reasonerConfigurationFingerprint: String,
    public val protocolVersion: Int = REASONING_WORKER_PROTOCOL_VERSION,
)

public enum class ReasoningWorkerResponseStatus {
    Completed,
    Failed,
    Cancelled,
    TimedOut,
}

public enum class ReasoningWorkerFailureKind {
    Startup,
    Crash,
    Timeout,
    Cancellation,
    MalformedOutput,
    ProtocolMismatch,
}

public data class ReasoningWorkerFailure(
    public val kind: ReasoningWorkerFailureKind,
    public val message: String,
)

public data class ReasoningWorkerFact(
    public val subject: String,
    public val predicate: String,
    public val objectValue: String,
    public val origin: FactOrigin,
)

public data class ReasoningWorkerNormalizedOutput(
    public val graphFingerprint: String,
    public val importClosureFingerprint: String,
    public val facts: List<ReasoningWorkerFact> = emptyList(),
)

public data class ReasoningWorkerResponse(
    public val status: ReasoningWorkerResponseStatus,
    public val output: ReasoningWorkerNormalizedOutput? = null,
    public val failure: ReasoningWorkerFailure? = null,
    public val protocolVersion: Int = REASONING_WORKER_PROTOCOL_VERSION,
)

public object ReasoningWorkerProtocol {
    public fun encodeRequest(request: ReasoningWorkerRequest): String = buildString {
        appendLine("protocolVersion=${request.protocolVersion}")
        appendLine("messageType=request")
        appendLine("ontologySourcePaths=${encodeList(request.ontologySourcePaths)}")
        appendLine("importSourcePaths=${encodeList(request.importSourcePaths)}")
        appendLine("graphFingerprint=${encode(request.graphFingerprint)}")
        appendLine("importClosureFingerprint=${encode(request.importClosureFingerprint)}")
        append("reasonerConfigurationFingerprint=${encode(request.reasonerConfigurationFingerprint)}")
    }

    public fun decodeRequest(payload: String): EntioResult<ReasoningWorkerRequest> {
        val fields = parseFields(payload) ?: return invalid("Worker request contains a malformed line.")
        if (fields["messageType"]?.singleOrNull() != "request") {
            return invalid("Worker request has an invalid message type.")
        }
        val protocolVersion = fields["protocolVersion"]?.singleOrNull()?.toIntOrNull()
            ?: return invalid("Worker request does not contain a valid protocol version.")
        if (protocolVersion != REASONING_WORKER_PROTOCOL_VERSION) {
            return invalid("Unsupported reasoning worker protocol version: $protocolVersion.")
        }

        val ontologyPaths = decodeList(fields["ontologySourcePaths"])
            ?: return invalid("Worker request does not contain valid ontology source paths.")
        val importPaths = decodeList(fields["importSourcePaths"])
            ?: return invalid("Worker request does not contain valid import source paths.")
        val graphFingerprint = decodeRequired(fields, "graphFingerprint")
            ?: return invalid("Worker request does not contain a graph fingerprint.")
        val importFingerprint = decodeRequired(fields, "importClosureFingerprint")
            ?: return invalid("Worker request does not contain an import closure fingerprint.")
        val configurationFingerprint = decodeRequired(fields, "reasonerConfigurationFingerprint")
            ?: return invalid("Worker request does not contain a reasoner configuration fingerprint.")

        return EntioResult.Success(
            ReasoningWorkerRequest(
                ontologySourcePaths = ontologyPaths,
                importSourcePaths = importPaths,
                graphFingerprint = graphFingerprint,
                importClosureFingerprint = importFingerprint,
                reasonerConfigurationFingerprint = configurationFingerprint,
                protocolVersion = protocolVersion,
            ),
        )
    }

    public fun encodeResponse(response: ReasoningWorkerResponse): String = buildString {
        appendLine("protocolVersion=${response.protocolVersion}")
        appendLine("messageType=response")
        appendLine("status=${response.status.name}")
        response.output?.let { output ->
            appendLine("graphFingerprint=${encode(output.graphFingerprint)}")
            appendLine("importClosureFingerprint=${encode(output.importClosureFingerprint)}")
            output.facts.forEach { fact ->
                appendLine("fact=${encodeFact(fact)}")
            }
        }
        response.failure?.let { failure ->
            appendLine("failureKind=${failure.kind.name}")
            append("failureMessage=${encode(failure.message)}")
        }
    }

    public fun decodeResponse(payload: String): EntioResult<ReasoningWorkerResponse> {
        val fields = parseFields(payload) ?: return invalid("Worker response contains a malformed line.")
        if (fields["messageType"]?.singleOrNull() != "response") {
            return invalid("Worker response has an invalid message type.")
        }
        val protocolVersion = fields["protocolVersion"]?.singleOrNull()?.toIntOrNull()
            ?: return invalid("Worker response does not contain a valid protocol version.")
        if (protocolVersion != REASONING_WORKER_PROTOCOL_VERSION) {
            return invalid("Unsupported reasoning worker protocol version: $protocolVersion.")
        }
        val status = fields["status"]?.singleOrNull()?.let { value ->
            runCatching { ReasoningWorkerResponseStatus.valueOf(value) }.getOrNull()
        } ?: return invalid("Worker response does not contain a valid status.")

        val failure = fields["failureKind"]?.singleOrNull()?.let { kind ->
            val failureKind = runCatching { ReasoningWorkerFailureKind.valueOf(kind) }.getOrNull()
                ?: return invalid("Worker response contains an invalid failure kind.")
            val message = decodeRequired(fields, "failureMessage")
                ?: return invalid("Worker response failure does not contain a message.")
            ReasoningWorkerFailure(failureKind, message)
        }

        val graphFingerprint = fields["graphFingerprint"]?.singleOrNull()?.let(::decode)
        val importFingerprint = fields["importClosureFingerprint"]?.singleOrNull()?.let(::decode)
        val factValues = fields.getOrDefault("fact", emptyList())
        val facts = factValues.map { value ->
            decodeFact(value) ?: return invalid("Worker response contains a malformed fact.")
        }
        val output = if (graphFingerprint != null || importFingerprint != null || facts.isNotEmpty()) {
            if (graphFingerprint == null || importFingerprint == null) {
                return invalid("Worker response output is missing a graph fingerprint.")
            }
            ReasoningWorkerNormalizedOutput(graphFingerprint, importFingerprint, facts)
        } else {
            null
        }

        if (status == ReasoningWorkerResponseStatus.Completed && output == null) {
            return invalid("Completed worker response does not contain normalized output.")
        }

        return EntioResult.Success(
            ReasoningWorkerResponse(
                status = status,
                output = output,
                failure = failure,
                protocolVersion = protocolVersion,
            ),
        )
    }

    private fun parseFields(payload: String): Map<String, List<String>>? {
        if (payload.isBlank()) return null
        val fields = linkedMapOf<String, MutableList<String>>()
        payload.lineSequence().filter(String::isNotEmpty).forEach { line ->
            val separator = line.indexOf('=')
            if (separator <= 0) return null
            val key = line.substring(0, separator)
            fields.getOrPut(key) { mutableListOf() }.add(line.substring(separator + 1))
        }
        return fields
    }

    private fun encodeList(values: List<String>): String = values.joinToString(",", transform = ::encode)

    private fun decodeList(value: List<String>?): List<String>? {
        val encoded = value?.singleOrNull() ?: return null
        if (encoded.isEmpty()) return emptyList()
        return encoded.split(',').map { token -> decode(token) ?: return null }
    }

    private fun decodeRequired(fields: Map<String, List<String>>, key: String): String? =
        fields[key]?.singleOrNull()?.let(::decode)

    private fun encodeFact(fact: ReasoningWorkerFact): String = listOf(
        encode(fact.subject),
        encode(fact.predicate),
        encode(fact.objectValue),
        fact.origin.name,
    ).joinToString("|")

    private fun decodeFact(value: String): ReasoningWorkerFact? {
        val parts = value.split('|')
        if (parts.size != 4) return null
        val origin = runCatching { FactOrigin.valueOf(parts[3]) }.getOrNull() ?: return null
        return ReasoningWorkerFact(
            subject = decode(parts[0]) ?: return null,
            predicate = decode(parts[1]) ?: return null,
            objectValue = decode(parts[2]) ?: return null,
            origin = origin,
        )
    }

    private fun encode(value: String): String = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decode(value: String): String? = runCatching {
        String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
    }.getOrNull()

    private fun invalid(message: String): EntioResult<Nothing> = EntioResult.Failure(
        message = message,
        issues = listOf(
            ValidationIssue(
                severity = ValidationSeverity.Error,
                code = "reasoning-worker-protocol-invalid",
                message = message,
            ),
        ),
    )
}
