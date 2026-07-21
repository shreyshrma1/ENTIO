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
    fun build(project: EntioProject, request: String, rawSources: String, includesPrivateDraft: Boolean = false): AiOntologyContext {
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
            appendLine("Graph scope: ${if (includesPrivateDraft) "effective private draft (applied graph plus current proposal)" else "applied project graph"}.")
            appendLine("Graph statistics: ${triples.size} triples, ${entities.size} resources, ${project.resolvedSources.size} source(s).")
            appendLine("Retrieval: ${if (matched.isEmpty()) "no direct label/IRI matches; showing a bounded overview" else "relevant entities plus their graph neighborhood"}.")
            appendLine("Naming: refer to entities by their preferred label. Stable IRIs below are internal identifiers for disambiguation and proposal edits, not default user-facing names.")
            appendLine()
            appendLine("PROJECT SEMANTIC BOUNDARY")
            appendLine("Project IRI namespace: ${project.config.iriNamespace?.namespace?.value ?: "not configured"}")
            project.resolvedSources.sortedBy { it.id }.forEach { source ->
                appendLine("- sourceId=${source.id}; roles=${source.roles.map { it.name }.sorted().joinToString(",")}; format=${source.format.name}")
            }
            appendLine("Proposal edits must use one of the sourceId values above. Source IDs are project identities, not names to invent from the request.")
            appendLine()
            appendLine(ONTOLOGY_ENGINEERING_GUIDE)
            appendLine()
            appendLine("SEMANTIC ENTITY DESCRIPTIONS (${if (includesPrivateDraft) "applied and private-draft meaning" else "asserted project meaning"})")
            relevantTriples.map { it.subjectResource }.distinct()
                .sortedWith(compareBy<RdfResource> { role(it, triples) }.thenBy { it.value })
                .forEach { entity -> appendSemanticDescription(entity, triples) }
            appendLine()
            appendLine("RELEVANT GRAPH EVIDENCE (${if (includesPrivateDraft) "applied and private-draft triples" else "asserted project triples"})")
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

    private fun StringBuilder.appendSemanticDescription(entity: RdfResource, triples: List<GraphTriple>) {
        val outgoing = triples.filter { it.subjectResource == entity }
        val types = outgoing.filter { it.predicate == RDF_TYPE }.map { term(it.objectTerm) }
        val labels = outgoing.filter { it.predicate in LABEL_PREDICATES }.map { term(it.objectTerm) }
        val definitions = outgoing.filter { it.predicate in DEFINITION_PREDICATES }.map { term(it.objectTerm) }
        val domains = outgoing.filter { it.predicate == RDFS_DOMAIN }.map { term(it.objectTerm) }
        val ranges = outgoing.filter { it.predicate == RDFS_RANGE }.map { term(it.objectTerm) }
        val parents = outgoing.filter { it.predicate == RDFS_SUBCLASS_OF }.map { term(it.objectTerm) }
        appendLine("- preferred label: ${preferredLabel(entity, triples) ?: localName(entity)}")
        appendLine("  stable IRI (internal identifier): <${entity.value}>")
        appendLine("  role: ${role(entity, triples)}")
        if (types.isNotEmpty()) appendLine("  declarations/types: ${types.joinToString()}")
        if (labels.isNotEmpty()) appendLine("  labels: ${labels.joinToString()}")
        if (definitions.isNotEmpty()) appendLine("  definitions: ${definitions.joinToString()}")
        if (parents.isNotEmpty()) appendLine("  direct superclasses: ${parents.joinToString()}")
        if (domains.isNotEmpty()) appendLine("  asserted domains: ${domains.joinToString()}")
        if (ranges.isNotEmpty()) appendLine("  asserted ranges: ${ranges.joinToString()}")
    }

    private fun preferredLabel(entity: RdfResource, triples: List<GraphTriple>): String? = triples
        .filter { it.subjectResource == entity && it.predicate in PREFERRED_LABEL_PREDICATES }
        .mapNotNull { (it.objectTerm as? RdfLiteral)?.lexicalForm?.takeIf(String::isNotBlank) }
        .firstOrNull()

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
        private val RDFS_DOMAIN = Iri("http://www.w3.org/2000/01/rdf-schema#domain")
        private val RDFS_RANGE = Iri("http://www.w3.org/2000/01/rdf-schema#range")
        private val RDFS_SUBCLASS_OF = Iri("http://www.w3.org/2000/01/rdf-schema#subClassOf")
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
        private val PREFERRED_LABEL_PREDICATES = setOf(
            Iri("http://www.w3.org/2000/01/rdf-schema#label"),
            Iri("http://www.w3.org/2004/02/skos/core#prefLabel"),
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
        private val ONTOLOGY_ENGINEERING_GUIDE = """
            ONTOLOGY ENGINEERING GUIDE (trusted semantic context)
            Build a semantic understanding of the supplied ontology before answering or proposing changes. Determine your own plan and use FIBO context only when it is relevant to the requested outcome.
            - Keep entity roles distinct: classes describe kinds of things, properties describe relationships or values, and individuals are instances.
            - A declaration/type axiom establishes an entity's role. For example, `p rdf:type owl:ObjectProperty` declares p as an object property. Domain and range axioms describe a declared property's semantics; they do not declare the property.
            - `p rdfs:domain C` has a property as its subject and supports inferring that a subject using p is an instance of C. It is not merely a form-field usage restriction.
            - `p rdfs:range C` supports inferring that an IRI object used with p is an instance of C; a datatype range describes the datatype of literal values.
            - `C rdfs:subClassOf D` means every instance of C is also an instance of D. It is different from an individual type assertion.
            - Object properties relate resources, datatype properties relate resources to literals, and annotation properties attach descriptive metadata.
            - Labels and definitions describe an entity for people but do not replace its logical declaration or axioms.
            - In user-facing prose, always refer to an entity by its preferred label when one is asserted (for example, `Account 101`), not by its local IRI name (for example, `Account101`). Use the stable IRI only when the user asks for it, when disambiguation is necessary, or inside structured proposal/evidence fields.
            - Do not infer a preferred label from an IRI when an asserted label exists. Treat the current graph's preferred label and shape label as authoritative, including renamed labels.
            - If an earlier assistant message conflicts with the current graph, correct the earlier message and answer from the current graph; conversation history is not ontology evidence.
            - Asserted triples occur in the supplied project graph. Inferred consequences must be identified as inferred rather than presented as asserted source content.
            - Before presenting an answer or proposal, check the semantic roles of every subject, predicate, and object and check that the complete result satisfies the user's requested outcome.
            This guide supplies general ontology semantics. It does not prescribe the plan or assert project-specific facts.
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
