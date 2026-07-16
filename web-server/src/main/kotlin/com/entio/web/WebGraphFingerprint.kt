package com.entio.web

import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.RdfResource
import com.entio.core.RdfTerm
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal fun webGraphFingerprint(graph: GraphState): String = sha256(
    graph.triples
        .sortedWith(compareBy<GraphTriple>({ it.subjectResource.value }, { it.predicate.value }, { it.objectTerm.canonicalWebKey() }))
        .joinToString("\n") { triple ->
            "${triple.subjectResource.canonicalWebKey()}|${triple.predicate.value}|${triple.objectTerm.canonicalWebKey()}"
        },
)

internal fun webProposalFingerprint(proposalId: String, graph: GraphState): String = sha256(
    "$proposalId|${webGraphFingerprint(graph)}",
)

private fun RdfResource.canonicalWebKey(): String = when (this) {
    is com.entio.core.Iri -> "iri:$value"
    is com.entio.core.BlankNodeResource -> "blank:$id"
}

private fun RdfTerm.canonicalWebKey(): String = when (this) {
    is RdfResource -> canonicalWebKey()
    is com.entio.core.RdfLiteral -> "literal:$lexicalForm|${datatypeIri?.value.orEmpty()}|${languageTag.orEmpty()}"
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(StandardCharsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte) }
