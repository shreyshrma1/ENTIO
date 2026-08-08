package com.entio.semantic

import com.entio.core.DomainCustomizationClassification
import com.entio.core.DomainMaterializationClassification
import com.entio.core.DomainOntologyProfileIdentity
import com.entio.core.DomainReuseAction
import com.entio.core.DomainReuseCustomization
import com.entio.core.EntioResult
import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.RdfLiteral
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DomainReuseServiceTest {
    private val root = Path.of("..", DomainCorpusIdentity.OUTPUT_RELATIVE_PATH).toAbsolutePath().normalize()
    private val agreement = Iri(
        "https://spec.edmcouncil.org/fibo/ontology/FND/Agreements/Agreements/Agreement",
    )
    private val borrower = Iri(
        "https://spec.edmcouncil.org/fibo/ontology/FBC/DebtAndEquities/Debt/hasBorrower",
    )
    private val legalName = Iri(
        "https://spec.edmcouncil.org/fibo/ontology/FND/Relations/Relations/hasLegalName",
    )
    private val boardAgreement = Iri(
        "https://spec.edmcouncil.org/fibo/ontology/BE/Corporations/Corporations/BoardAgreement",
    )

    @Test
    fun classObjectAndDatatypeReuseMaterializeSupportedMeaningWithoutImports(): Unit {
        val service = DomainReuseService.open(root)
        listOf(agreement, borrower, legalName).forEach { iri ->
            val result = service.prepare(request(DomainReuseAction.Reuse, iri, acknowledged = true), GraphState(), GraphState())
            val batch = assertIs<EntioResult.Success<com.entio.core.DomainReusePreparedBatch>>(result).value
            val triples = batch.entries.flatMap { it.changeSet.changes }.map { it.triple }

            assertTrue(triples.any { it.subjectResource == iri })
            assertTrue(triples.none { it.predicate.value == "http://www.w3.org/2002/07/owl#imports" })
            assertTrue(batch.dependencies.size <= 100)
            assertTrue(batch.generatedStatementCount <= 2_000)
        }
    }

    @Test
    fun partialSourceMeaningRequiresAcknowledgementAndRemainsExplicit(): Unit {
        val service = DomainReuseService.open(root)
        val rejected = service.prepare(request(DomainReuseAction.Reuse, agreement), GraphState(), GraphState())
        assertEquals(
            "domain-partial-materialization-acknowledgement-required",
            assertIs<EntioResult.Failure>(rejected).issues.single().code,
        )

        val accepted = assertIs<EntioResult.Success<com.entio.core.DomainReusePreparedBatch>>(
            service.prepare(request(DomainReuseAction.Reuse, agreement, acknowledged = true), GraphState(), GraphState()),
        ).value
        assertEquals(DomainMaterializationClassification.PartialMaterialization, accepted.sourceSnapshot.classification)
        assertTrue(accepted.sourceSnapshot.omittedSourceAxioms.isNotEmpty())
    }

    @Test
    fun sourceMeaningThatCannotBeSafelyOmittedBlocksReuse(): Unit {
        val service = DomainReuseService.open(root)
        val result = service.prepare(
            request(DomainReuseAction.Reuse, boardAgreement, acknowledged = true),
            GraphState(),
            GraphState(),
        )

        assertEquals("domain-source-meaning-unsupported", assertIs<EntioResult.Failure>(result).issues.single().code)
    }

    @Test
    fun customizationLocksCanonicalIriAndSeparatesSourceFromProjectMeaning(): Unit {
        val service = DomainReuseService.open(root)
        val batch = assertIs<EntioResult.Success<com.entio.core.DomainReusePreparedBatch>>(
            service.prepare(
                request(DomainReuseAction.ReuseAndCustomize, agreement, acknowledged = true).copy(
                    customization = DomainReuseCustomization(
                        preferredLabel = "Commercial agreement",
                        definition = "An agreement used by this project.",
                    ),
                ),
                GraphState(),
                GraphState(),
            ),
        ).value
        val projectTriples = batch.entries.flatMap { it.changeSet.changes }.map { it.triple }

        assertTrue(projectTriples.all { triple ->
            triple.subjectResource == agreement || triple.subjectResource.value != agreement.value
        })
        assertTrue(projectTriples.any { it.subjectResource == agreement && it.objectTerm == RdfLiteral("Commercial agreement") })
        assertTrue(batch.sourceSnapshot.statements.any { it.subjectResource == agreement && it.objectTerm == RdfLiteral("agreement") })

        val difference = assertIs<EntioResult.Success<com.entio.core.DomainReuseDifference>>(
            service.describe(agreement, GraphState(projectTriples.toSet())),
        ).value
        assertEquals(DomainCustomizationClassification.AnnotationOnly, difference.classification)
    }

    @Test
    fun customizationCanExplicitlyRemoveSupportedAnnotationsAndStructure(): Unit {
        val service = DomainReuseService.open(root)
        val batch = assertIs<EntioResult.Success<com.entio.core.DomainReusePreparedBatch>>(
            service.prepare(
                request(DomainReuseAction.ReuseAndCustomize, agreement, acknowledged = true).copy(
                    customization = DomainReuseCustomization(
                        preferredLabel = "",
                        definition = "",
                        alternateLabels = emptyList(),
                        parentIris = emptyList(),
                        domainIris = emptyList(),
                        rangeIris = emptyList(),
                    ),
                ),
                GraphState(),
                GraphState(),
            ),
        ).value
        val rootStatements = batch.entries.flatMap { it.changeSet.changes }.map { it.triple }
            .filter { it.subjectResource == agreement }

        assertEquals(1, rootStatements.size)
        assertTrue(rootStatements.single().predicate.value.endsWith("type"))

        val customParent = Iri("https://example.com/ontology#relatedTo")
        val customDomain = Iri("https://example.com/ontology#Borrower")
        val customRange = Iri("https://example.com/ontology#Loan")
        val declarations = GraphState(
            setOf(customParent, customDomain, customRange).map { iri ->
                GraphTriple(
                    iri,
                    Iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                    Iri("http://www.w3.org/2002/07/owl#Class"),
                )
            }.toSet(),
        )
        val propertyBatch = assertIs<EntioResult.Success<com.entio.core.DomainReusePreparedBatch>>(
            service.prepare(
                request(DomainReuseAction.ReuseAndCustomize, borrower, acknowledged = true).copy(
                    customization = DomainReuseCustomization(
                        parentIris = listOf(customParent),
                        domainIris = listOf(customDomain),
                        rangeIris = listOf(customRange),
                    ),
                ),
                declarations,
                GraphState(),
            ),
        ).value
        val propertyStatements = propertyBatch.entries.flatMap { it.changeSet.changes }.map { it.triple }
            .filter { it.subjectResource == borrower }
        assertTrue(propertyStatements.any { it.predicate.value.endsWith("subPropertyOf") && it.objectTerm == customParent })
        assertTrue(propertyStatements.any { it.predicate.value.endsWith("domain") && it.objectTerm == customDomain })
        assertTrue(propertyStatements.any { it.predicate.value.endsWith("range") && it.objectTerm == customRange })
    }

    @Test
    fun extensionAndApprovedMappingsUseOnlyTypedSafeRelationships(): Unit {
        val service = DomainReuseService.open(root)
        val local = Iri("https://example.com/ontology#ProjectAgreement")
        val localDeclaration = GraphTriple(
            local,
            Iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
            Iri("http://www.w3.org/2002/07/owl#Class"),
        )
        val extension = assertIs<EntioResult.Success<com.entio.core.DomainReusePreparedBatch>>(
            service.prepare(
                request(DomainReuseAction.ExtendLocally, agreement, acknowledged = true).copy(
                    localIri = local,
                    localSourceId = "local",
                    localIriNamespace = Iri("https://example.com/ontology#"),
                ),
                GraphState(),
                GraphState(),
            ),
        ).value
        assertEquals(setOf("fibo-reuse", "local"), extension.entries.map { it.targetSourceId }.toSet())
        assertTrue(extension.entries.flatMap { it.changeSet.changes }.any {
            it.triple.subjectResource == local && it.triple.predicate.value.endsWith("subClassOf") && it.triple.objectTerm == agreement
        })

        val wrongNamespace = service.prepare(
            request(DomainReuseAction.ExtendLocally, agreement, acknowledged = true).copy(
                localIri = Iri("https://unapproved.example/ProjectAgreement"),
                localSourceId = "local",
                localIriNamespace = Iri("https://example.com/ontology#"),
            ),
            GraphState(),
            GraphState(),
        )
        assertEquals(
            "domain-local-iri-must-use-project-namespace",
            assertIs<EntioResult.Failure>(wrongNamespace).issues.single().code,
        )

        listOf(
            DomainReuseAction.MapClose to "closeMatch",
            DomainReuseAction.MapRelated to "relatedMatch",
        ).forEach { (action, predicate) ->
            val mapping = assertIs<EntioResult.Success<com.entio.core.DomainReusePreparedBatch>>(
                service.prepare(
                    request(action, agreement).copy(localIri = local, localSourceId = "local"),
                    GraphState(setOf(localDeclaration)),
                    GraphState(),
                ),
            ).value
            val triple = mapping.entries.single().changeSet.changes.single().triple
            assertTrue(triple.predicate.value.endsWith(predicate))
            assertEquals(agreement, triple.objectTerm)
            assertTrue(mapping.entries.flatMap { it.changeSet.changes }.none { it.triple.subjectResource == agreement })

            val duplicate = service.prepare(
                request(action, agreement).copy(localIri = local, localSourceId = "local"),
                GraphState(setOf(localDeclaration, triple)),
                GraphState(),
            )
            assertEquals("domain-reuse-no-op", assertIs<EntioResult.Failure>(duplicate).issues.single().code)
        }
    }

    @Test
    fun removalBlocksLocalDependenciesAndDifferenceDetectsLogicalCustomization(): Unit {
        val service = DomainReuseService.open(root)
        val declaration = GraphTriple(
            agreement,
            Iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
            Iri("http://www.w3.org/2002/07/owl#Class"),
        )
        val localDependency = GraphTriple(
            Iri("https://example.com/ontology#ProjectAgreement"),
            Iri("http://www.w3.org/2000/01/rdf-schema#subClassOf"),
            agreement,
        )
        val managed = GraphState(setOf(declaration))
        val blocked = service.prepare(
            request(DomainReuseAction.RemoveReuse, agreement),
            GraphState(setOf(declaration, localDependency)),
            managed,
        )
        assertEquals(
            "domain-reuse-removal-has-local-dependencies",
            assertIs<EntioResult.Failure>(blocked).issues.single().code,
        )

        val difference = assertIs<EntioResult.Success<com.entio.core.DomainReuseDifference>>(
            service.describe(agreement, GraphState(setOf(declaration, localDependency))),
        ).value
        assertEquals(DomainCustomizationClassification.LogicalStructureChanged, difference.classification)

        val mapping = GraphTriple(
            Iri("https://example.com/ontology#ProjectAgreement"),
            Iri("http://www.w3.org/2004/02/skos/core#closeMatch"),
            agreement,
        )
        val removable = service.prepare(
            request(DomainReuseAction.RemoveReuse, agreement),
            GraphState(setOf(declaration, mapping)),
            managed,
        )
        assertIs<EntioResult.Success<com.entio.core.DomainReusePreparedBatch>>(removable)
    }

    @Test
    fun cyclicDependenciesAreCycleSafeAndDeterministicallyOrdered(): Unit {
        val searchRoot = Files.createTempDirectory("entio-domain-reuse-cycle")
        val first = "https://spec.edmcouncil.org/fibo/ontology/Test/A"
        val second = "https://spec.edmcouncil.org/fibo/ontology/Test/B"
        Files.writeString(
            searchRoot.resolve("descriptors-v1.jsonl"),
            descriptor(first, "A", listOf(second)) + "\n" + descriptor(second, "B", listOf(first)) + "\n",
        )
        val service = DomainReuseService.open(searchRoot)

        val batch = assertIs<EntioResult.Success<com.entio.core.DomainReusePreparedBatch>>(
            service.prepare(
                request(DomainReuseAction.Reuse, Iri(first)),
                GraphState(),
                GraphState(),
            ),
        ).value

        assertEquals(listOf(first, second), batch.dependencies.map { it.iri.value })
        assertEquals(2, batch.dependencies.size)
    }

    private fun descriptor(iri: String, label: String, parents: List<String>): String {
        val parentJson = parents.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
        return """{"schema":"entio-domain-semantic-record-v1","iri":"$iri","kind":"Class","sourceFamily":"FIBO","sourcePath":"source/test.rdf","ontologyIri":"https://spec.edmcouncil.org/fibo/ontology/Test/","maturity":"Release","preferredLabel":"$label","alternateLabels":[],"definitions":[],"parents":$parentJson,"domains":[],"ranges":[],"descriptorText":"$label","dependencyFingerprint":"dependency","unsupportedConstructs":[],"recordFingerprint":"record-$label"}"""
    }

    private fun request(
        action: DomainReuseAction,
        iri: Iri,
        acknowledged: Boolean = false,
    ): DomainReusePreparationRequest = DomainReusePreparationRequest(
        action = action,
        canonicalIri = iri,
        managedSourceId = DomainOntologyProfileIdentity.MANAGED_SOURCE_ID,
        partialMaterializationAcknowledged = acknowledged,
    )
}
