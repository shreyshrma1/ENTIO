package com.entio.semantic

import com.entio.core.Iri

/** IRIs recognized by Phase 3 metadata extraction. */
public object AnnotationVocabulary {
    public val rdfsLabel: Iri = Iri("http://www.w3.org/2000/01/rdf-schema#label")
    public val rdfsComment: Iri = Iri("http://www.w3.org/2000/01/rdf-schema#comment")
    public val skosPrefLabel: Iri = Iri("http://www.w3.org/2004/02/skos/core#prefLabel")
    public val skosAltLabel: Iri = Iri("http://www.w3.org/2004/02/skos/core#altLabel")
    public val skosDefinition: Iri = Iri("http://www.w3.org/2004/02/skos/core#definition")
    public val dctermsSource: Iri = Iri("http://purl.org/dc/terms/source")

    public val recognizedProperties: Set<Iri> = setOf(
        rdfsLabel,
        rdfsComment,
        skosPrefLabel,
        skosAltLabel,
        skosDefinition,
        dctermsSource,
    )

    public val structuralProperties: Set<Iri> = setOf(
        Iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
        Iri("http://www.w3.org/2000/01/rdf-schema#subClassOf"),
        Iri("http://www.w3.org/2000/01/rdf-schema#subPropertyOf"),
        Iri("http://www.w3.org/2000/01/rdf-schema#domain"),
        Iri("http://www.w3.org/2000/01/rdf-schema#range"),
        Iri("http://www.w3.org/2002/07/owl#equivalentClass"),
        Iri("http://www.w3.org/2002/07/owl#equivalentProperty"),
        Iri("http://www.w3.org/2002/07/owl#inverseOf"),
    )

    public fun isRecognized(property: Iri): Boolean = property in recognizedProperties

    public fun isStructural(property: Iri): Boolean = property in structuralProperties
}
