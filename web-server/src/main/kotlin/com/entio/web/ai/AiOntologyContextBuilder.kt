package com.entio.web.ai

import com.entio.core.BlankNodeResource
import com.entio.core.EntioProject
import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.RdfLiteral
import com.entio.core.RdfResource
import com.entio.core.RdfTerm
import com.entio.web.contract.WebAiEvidence
import java.util.Locale

/**
 * Builds a bounded, graph-derived context for the provider.
 *
 * The index is intentionally local to a run. It is linear in the number of
 * graph statements, while the prompt contains only the most relevant entity
 * neighborhood and a deterministic overview for large projects.
 */
internal class AiOntologyContextBuilder(
    private val maxEntities: Int = 48,
    private val maxTriples: Int = 280,
    private val maxCharacters: Int = 48_000,
    private val rawFallbackCharacters: Int = 24_000,
) {
    fun build(project: EntioProject, request: String, rawSources: String): AiOntologyContext {
        val triples = project.graph.triples.toList().sortedWith(tripleComparator)
        val entities = linkedSetOf<RdfResource>().apply {
            triples.forEach { triple ->
                add(triple.subjectResource)
                add(triple.predicate)
                (triple.objectTerm as? RdfResource)?.let(::add)
            }
        }
        val tokens = tokenize(request)
        val scores = entities.associateWith { entity -> score(entity, triples, tokens) }
        val matched = scores.entries
            .filter { it.value > 0 }
            .sortedWith(compareByDescending<Map.Entry<RdfResource, Int>> { it.value }.thenBy { it.key.value })
            .take(maxEntities)
            .map { it.key }
            .toSet()
        val selected = if (matched.isEmpty()) entities.sortedBy { it.value }.take(maxEntities).toSet() else matched
        val neighborhood = linkedSetOf<RdfResource>().apply {
            addAll(selected)
            triples.forEach { triple ->
                val touchesSelected = triple.subjectResource in selected ||
                    triple.predicate in selected ||
                    (triple.objectTerm as? RdfResource)?.let { it in selected } == true
                if (touchesSelected) {
                    add(triple.subjectResource)
                    add(triple.predicate)
                    (triple.objectTerm as? RdfResource)?.let(::add)
                }
            }
        }
        val relevantTriples = triples.filter { triple ->
            triple.subjectResource in neighborhood ||
                triple.predicate in neighborhood ||
                (triple.objectTerm as? RdfResource)?.let { it in neighborhood } == true
        }.take(maxTriples)

        val output = buildString {
            appendLine("ENTIO TYPED ONTOLOGY CONTEXT")
            appendLine("Graph statistics: ${triples.size} triples, ${entities.size} resources, ${project.resolvedSources.size} source(s).")
            appendLine("Retrieval: ${if (matched.isEmpty()) "no direct label/IRI matches; showing a bounded overview" else "relevant entities plus their graph neighborhood"}.")
            appendLine()
            appendLine(TRUSTED_GLOSSARY)
            appendLine()
            appendLine("ENTITY INDEX")
            entities.asSequence()
                .sortedWith(compareBy<RdfResource> { role(it, triples) }.thenBy { it.value })
                .take(maxEntities)
                .forEach { entity -> appendLine("- ${role(entity, triples)} ${term(entity)}") }
            appendLine()
            appendLine("RELEVANT GRAPH EVIDENCE (asserted project triples)")
            relevantTriples.forEach { triple -> appendLine(formatTriple(triple)) }
            if (relevantTriples.isEmpty()) appendLine("(No matching asserted triples were found.)")
            if (rawSources.length <= rawFallbackCharacters) {
                appendLine()
                appendLine("RAW SOURCE CONTEXT (small-project fallback; read-only)")
                appendLine(rawSources)
            }
        }.take(maxCharacters)
        return AiOntologyContext(output, triples.size, entities.size, relevantTriples.size)
    }

    fun verifyEvidence(
        graph: GraphState,
        claims: List<AiEvidenceClaim>,
        draftEdits: List<com.entio.web.contract.WebAiProposalEdit> = emptyList(),
    ): List<WebAiEvidence> {
        val known = graph.triples.toMutableSet()
        draftEdits.forEach { edit ->
            val triple = edit.toGraphTriple() ?: return@forEach
            if (edit.operation == "add") known += triple else known -= triple
        }
        return claims.mapNotNull { claim ->
            val triple = claim.toGraphTriple() ?: return@mapNotNull null
            val trustedGlossaryClaim = claim.source == "trusted-vocabulary" &&
                claim.subject in TRUSTED_GLOSSARY_SUBJECTS &&
                claim.predicate == RDFS_COMMENT
            if (triple !in known && !trustedGlossaryClaim) return@mapNotNull null
            WebAiEvidence(
                subject = claim.subject,
                predicate = claim.predicate,
                objectKind = claim.objectKind,
                objectValue = claim.objectValue,
                source = claim.source,
            )
        }.distinctBy { listOf(it.subject, it.predicate, it.objectKind, it.objectValue, it.source) }
    }

    private fun score(entity: RdfResource, triples: List<GraphTriple>, tokens: Set<String>): Int {
        if (tokens.isEmpty()) return 0
        val text = buildString {
            append(entity.value)
            append(' ')
            triples.filter { it.subjectResource == entity }.forEach { triple ->
                if (triple.predicate in LABEL_PREDICATES || triple.predicate in DEFINITION_PREDICATES) {
                    append(' ')
                    append((triple.objectTerm as? RdfLiteral)?.lexicalForm.orEmpty())
                }
            }
        }.lowercase(Locale.ROOT)
        val entityTokens = tokenize(text)
        return tokens.fold(0) { score, token ->
            when {
                token in entityTokens && localName(entity).contains(token) -> score + 6
                token in entityTokens -> score + 2
                else -> score
            }
        }
    }

    private fun role(entity: RdfResource, triples: List<GraphTriple>): String {
        val types = triples.filter { it.subjectResource == entity && it.predicate == RDF_TYPE }
            .mapNotNull { (it.objectTerm as? RdfResource)?.value }
        return when {
            types.any { it in CLASS_TYPES } -> "class"
            types.any { it in PROPERTY_TYPES } -> "property"
            types.isNotEmpty() -> "individual"
            else -> "resource"
        }
    }

    private fun formatTriple(triple: GraphTriple): String =
        "${term(triple.subjectResource)} ${term(triple.predicate)} ${term(triple.objectTerm)} ."

    private fun term(value: RdfTerm): String = when (value) {
        is RdfResource -> "<${value.value}>"
        is RdfLiteral -> buildString {
            append('"')
            append(value.lexicalForm.replace("\\", "\\\\").replace("\"", "\\\""))
            append('"')
            value.languageTag?.let { append("@$it") }
            value.datatypeIri?.let { append("^^<${it.value}>") }
        }
    }

    private fun localName(resource: RdfResource): String = resource.value.substringAfterLast('#').substringAfterLast('/').lowercase(Locale.ROOT)

    private fun tokenize(value: String): Set<String> = TOKEN_PATTERN.findAll(value.lowercase(Locale.ROOT))
        .map { it.value }
        .filter { it.length > 1 && it !in STOP_WORDS }
        .toSet()

    private companion object {
        private val TOKEN_PATTERN = Regex("[a-z0-9]+")
        private val STOP_WORDS = setOf("the", "and", "for", "with", "from", "into", "that", "this", "what", "does", "mean", "how", "why", "which", "where", "are", "is", "can", "please", "add", "write", "give")
        private val RDF_TYPE = Iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
        private val RDFS_CLASS = "http://www.w3.org/2000/01/rdf-schema#Class"
        private val OWL_CLASS = "http://www.w3.org/2002/07/owl#Class"
        private val RDF_PROPERTY = "http://www.w3.org/1999/02/22-rdf-syntax-ns#Property"
        private val OWL_OBJECT_PROPERTY = "http://www.w3.org/2002/07/owl#ObjectProperty"
        private val OWL_DATATYPE_PROPERTY = "http://www.w3.org/2002/07/owl#DatatypeProperty"
        private val OWL_ANNOTATION_PROPERTY = "http://www.w3.org/2002/07/owl#AnnotationProperty"
        private val CLASS_TYPES = setOf(RDFS_CLASS, OWL_CLASS)
        private val PROPERTY_TYPES = setOf(RDF_PROPERTY, OWL_OBJECT_PROPERTY, OWL_DATATYPE_PROPERTY, OWL_ANNOTATION_PROPERTY)
        private val LABEL_PREDICATES = setOf(
            Iri("http://www.w3.org/2000/01/rdf-schema#label"),
            Iri("http://www.w3.org/2004/02/skos/core#prefLabel"),
            Iri("http://www.w3.org/2004/02/skos/core#altLabel"),
        )
        private val DEFINITION_PREDICATES = setOf(
            Iri("http://www.w3.org/2004/02/skos/core#definition"),
            Iri("http://www.w3.org/2000/01/rdf-schema#comment"),
        )
        private val RDFS_COMMENT = "http://www.w3.org/2000/01/rdf-schema#comment"
        private val TRUSTED_GLOSSARY_SUBJECTS = setOf(
            "http://www.w3.org/1999/02/22-rdf-syntax-ns#type",
            "http://www.w3.org/1999/02/22-rdf-syntax-ns#Property",
            "http://www.w3.org/2000/01/rdf-schema#Class",
            "http://www.w3.org/2000/01/rdf-schema#domain",
            "http://www.w3.org/2000/01/rdf-schema#range",
            "http://www.w3.org/2000/01/rdf-schema#subClassOf",
            "http://www.w3.org/2000/01/rdf-schema#label",
            "http://www.w3.org/2000/01/rdf-schema#comment",
            "http://www.w3.org/2004/02/skos/core#definition",
            "http://www.w3.org/2002/07/owl#ObjectProperty",
            "http://www.w3.org/2002/07/owl#DatatypeProperty",
        )
        private val tripleComparator = compareBy<GraphTriple>({ it.subjectResource.value }, { it.predicate.value }, { it.objectTerm.toString() })
        private val TRUSTED_GLOSSARY = """
            TRUSTED VOCABULARY GLOSSARY (canonical RDF/RDFS/OWL meanings)
            - rdf:type declares that the subject is an instance of the object class or vocabulary type.
            - rdf:Property declares an RDF property; it is separate from the property's domain and range axioms.
            - rdfs:Class identifies a class.
            - rdfs:domain has a property as its subject and identifies classes whose instances may be subjects of that property.
            - rdfs:range has a property as its subject and identifies the expected value class or datatype.
            - rdfs:subClassOf states that instances of the subject class are also instances of the object class.
            - rdfs:label is a human-readable label; skos:definition is a semantic definition annotation.
            - owl:ObjectProperty and owl:DatatypeProperty specialize properties by the kind of value they relate to.
            The glossary is trusted vocabulary guidance, not an assertion about the project graph.
        """.trimIndent()
    }
}

internal data class AiOntologyContext(
    val text: String,
    val tripleCount: Int,
    val resourceCount: Int,
    val relevantTripleCount: Int,
)

internal data class AiEvidenceClaim(
    val subject: String,
    val predicate: String,
    val objectKind: String,
    val objectValue: String,
    val datatype: String? = null,
    val language: String? = null,
    val source: String = "current-ontology",
) {
    fun toGraphTriple(): GraphTriple? {
        if (subject.isBlank() || predicate.isBlank() || objectValue.isBlank()) return null
        val objectTerm = when (objectKind) {
            "iri" -> Iri(objectValue)
            "blank" -> BlankNodeResource(objectValue.removePrefix("_:"))
            "literal" -> RdfLiteral(objectValue, datatype?.let(::Iri), language)
            else -> return null
        }
        return GraphTriple(Iri(subject), Iri(predicate), objectTerm)
    }
}

internal fun com.entio.web.contract.WebAiProposalEdit.toGraphTriple(): GraphTriple? =
    AiEvidenceClaim(subject, predicate, objectKind, objectValue, datatype, language).toGraphTriple()
