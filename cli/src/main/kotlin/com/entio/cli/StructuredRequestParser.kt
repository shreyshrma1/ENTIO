package com.entio.cli

import com.entio.core.EntioResult
import com.entio.core.ValidationIssue
import com.entio.core.ValidationSeverity
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.Path

internal data class StructuredProposalRequest(
    val schemaVersion: Int,
    val proposalId: String,
    val title: String,
    val targetSourceId: String,
    val edits: List<StructuredEditRequest>,
    val baseline: StructuredBaselineRequest?,
)

internal data class StructuredEditRequest(
    val kind: String,
    val fields: Map<String, String?>,
)

internal data class StructuredBaselineRequest(
    val projectFingerprint: String?,
    val targetSourceFingerprint: String?,
    val graphFingerprint: String?,
)

internal class StructuredRequestParser {
    fun parse(path: Path): EntioResult<StructuredProposalRequest> {
        val root = try {
            mapper.readTree(Files.readString(path))
        } catch (exception: Exception) {
            return failure("malformed-request-json", "Structured request JSON could not be parsed: ${exception.message ?: "invalid JSON"}.")
        }
        if (!root.isObject) {
            return failure("invalid-request-schema", "Structured request must be a JSON object.")
        }

        val schemaVersion = root.intValue("schemaVersion", 1)
        if (schemaVersion != 1) {
            return failure("unsupported-request-schema", "Unsupported structured request schema version $schemaVersion.")
        }
        val targetSourceId = root.requiredText("targetSourceId") ?: return failure("missing-target-source", "Structured request must define targetSourceId.")
        val editsNode = root["edits"]
        if (editsNode == null || !editsNode.isArray || editsNode.isEmpty) {
            return failure("missing-request-edits", "Structured request must define a non-empty edits array.")
        }

        val edits = mutableListOf<StructuredEditRequest>()
        editsNode.forEachIndexed { index, node ->
            if (!node.isObject) {
                return failure("invalid-request-edit", "edits[$index] must be an object.")
            }
            val kind = node.requiredText("kind")
                ?: return failure("missing-request-edit-kind", "edits[$index] must define kind.")
            val fields = linkedMapOf<String, String?>()
            node.fields().forEachRemaining { (name, value) ->
                if (name != "kind") fields[name] = value.asNullableText()
            }
            edits += StructuredEditRequest(kind, fields)
        }

        val baseline = root["baseline"]?.takeUnless(JsonNode::isNull)?.let { node ->
            if (!node.isObject) return failure("invalid-request-baseline", "baseline must be an object.")
            StructuredBaselineRequest(
                projectFingerprint = node.textOrNull("projectFingerprint"),
                targetSourceFingerprint = node.textOrNull("targetSourceFingerprint"),
                graphFingerprint = node.textOrNull("graphFingerprint"),
            )
        }

        return EntioResult.Success(
            StructuredProposalRequest(
                schemaVersion = schemaVersion,
                proposalId = root.textOrDefault("proposalId", "cli-proposal"),
                title = root.textOrDefault("title", "CLI ontology proposal"),
                targetSourceId = targetSourceId,
                edits = edits,
                baseline = baseline,
            ),
        )
    }

    private fun JsonNode.requiredText(name: String): String? = textOrNull(name)?.takeIf { it.isNotBlank() }

    private fun JsonNode.textOrDefault(name: String, default: String): String = textOrNull(name) ?: default

    private fun JsonNode.textOrNull(name: String): String? = this[name]?.asNullableText()

    private fun JsonNode.asNullableText(): String? = when {
        isNull -> null
        isTextual -> textValue()
        isBoolean || isNumber -> asText()
        else -> null
    }

    private fun JsonNode.intValue(name: String, default: Int): Int =
        this[name]?.takeIf(JsonNode::isInt)?.intValue() ?: default

    private fun failure(code: String, message: String): EntioResult.Failure =
        EntioResult.Failure(
            message = message,
            issues = listOf(ValidationIssue(ValidationSeverity.Error, code, message, "request")),
        )

    private companion object {
        private val mapper = jacksonObjectMapper()
    }
}
