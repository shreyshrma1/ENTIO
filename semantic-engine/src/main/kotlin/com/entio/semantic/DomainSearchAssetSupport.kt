package com.entio.semantic

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Locale
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings

internal data class DomainSearchDocument(
    val iri: String,
    val preferredLabel: String,
    val alternateLabels: List<String>,
    val definitions: List<String>,
    val descriptorText: String,
    val recordFingerprint: String,
) {
    fun toJson(): String = buildString {
        append("{\"iri\":\"${jsonEscape(iri)}\",\"preferredLabel\":\"${jsonEscape(preferredLabel)}\"")
        append(",\"alternateLabels\":${jsonArray(alternateLabels)},\"definitions\":${jsonArray(definitions)}")
        append(",\"descriptorText\":\"${jsonEscape(descriptorText)}\"")
        append(",\"recordFingerprint\":\"$recordFingerprint\"}")
    }
}

internal object DomainSearchAssetSupport {
    const val INDEX_SCHEMA: String = "entio-domain-search-index-v1"
    const val LEXICAL_CONTRACT: String = "lucene-bm25-standard-analyzer-v1"
    const val TEXT_CONTRACT: String = "domain-embedding-text-v1"
    const val VECTOR_CONTRACT: String = "canonical-iri-float32-le-v1"
    const val VECTOR_FILE: String = "vectors/float32-le-v1.bin"
    const val IRI_FILE: String = "vectors/iris-v1.txt"
    const val LEXICAL_FILE: String = "lexical-index/documents-v1.jsonl"
    const val SEARCH_MANIFEST: String = "search-manifest.yaml"
    const val SEARCH_CHECKSUMS: String = "checksums/search-sha256sums.txt"

    private val loader = Load(LoadSettings.builder().setLabel("domain-search-asset").build())

    fun readDocuments(path: Path): List<DomainSearchDocument> = Files.readAllLines(path)
        .filter(String::isNotBlank)
        .map { line ->
            val map = loader.loadFromString(line) as? Map<*, *> ?: error("Expected a domain search record.")
            DomainSearchDocument(
                iri = map.string("iri"),
                preferredLabel = map.string("preferredLabel"),
                alternateLabels = map.stringList("alternateLabels"),
                definitions = map.stringList("definitions"),
                descriptorText = map.string("descriptorText"),
                recordFingerprint = map.string("recordFingerprint"),
            )
        }

    fun mapping(path: Path): Map<*, *> =
        loader.loadFromString(Files.readString(path)) as? Map<*, *> ?: error("Expected a manifest mapping.")

    fun Map<*, *>.string(key: String): String = this[key] as? String ?: error("Missing string: $key")
    fun Map<*, *>.int(key: String): Int = (this[key] as? Number)?.toInt() ?: error("Missing integer: $key")
    fun Map<*, *>.stringList(key: String): List<String> = (this[key] as? List<*>)
        ?.map { it as? String ?: error("Non-string value in $key") }
        ?: error("Missing list: $key")

    fun sha256(path: Path): String = sha256(Files.readAllBytes(path))
    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(Locale.ROOT, it) }
}

private fun jsonArray(values: List<String>): String =
    values.joinToString(prefix = "[", postfix = "]") { "\"${jsonEscape(it)}\"" }

private fun jsonEscape(value: String): String = value
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\n", "\\n")
    .replace("\r", "\\r")
    .replace("\t", "\\t")
