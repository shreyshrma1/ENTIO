package com.entio.semantic

import com.entio.core.AddObjectPropertyAssertionEdit
import com.entio.core.AddSuperclassEdit
import com.entio.core.AssignTypeEdit
import com.entio.core.BlankNodeResource
import com.entio.core.ConsistencyStatus
import com.entio.core.EntioProject
import com.entio.core.EntioProjectConfig
import com.entio.core.FactOrigin
import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.InferenceImportDependenceState
import com.entio.core.InferenceMaterializationKind
import com.entio.core.InferenceStageability
import com.entio.core.Iri
import com.entio.core.LoadedOntology
import com.entio.core.LoadedSymbol
import com.entio.core.OntologyFormat
import com.entio.core.OntologySourceReference
import com.entio.core.ReasoningClassRelationship
import com.entio.core.ReasoningFingerprints
import com.entio.core.ReasoningIndividualType
import com.entio.core.ReasoningPropertyRelationship
import com.entio.core.ReasoningResult
import com.entio.core.ReasoningRunMetadata
import com.entio.core.ReasoningRunStatus
import com.entio.core.ResolvedOntologySource
import com.entio.core.SymbolKind
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class InferenceMaterializationServiceTest {
    private val service = InferenceMaterializationService()
    private val child = Iri("https://example.com/MortgageLoan")
    private val parent = Iri("https://example.com/Loan")
    private val loan = Iri("https://example.com/loan123")
    private val borrower = Iri("https://example.com/customer456")
    private val hasBorrower = Iri("https://example.com/hasBorrower")
    private val context = InferenceMaterializationIdentityContext("project-1", "alice", "job-1")

    @Test
    fun mapsAllSupportedInferredFactsToExistingTypedEdits(): Unit {
        val analyses = service.analyze(project(), reasoning(), context)

        assertEquals(
            listOf(
                InferenceMaterializationKind.SubclassRelationship,
                InferenceMaterializationKind.IndividualType,
                InferenceMaterializationKind.ObjectPropertyAssertion,
            ),
            analyses.map(InferenceMaterializationAnalysis::kind),
        )
        assertIs<AddSuperclassEdit>(analyses[0].edit)
        assertIs<AssignTypeEdit>(analyses[1].edit)
        assertIs<AddObjectPropertyAssertionEdit>(analyses[2].edit)
        assertEquals(List(3) { InferenceStageability.Stageable }, analyses.map(InferenceMaterializationAnalysis::stageability))
        assertEquals(List(3) { "simple" }, analyses.map { it.candidate?.selectedSourceId })
    }

    @Test
    fun excludesAssertedReasoningRowsAndDetectsAppliedDuplicates(): Unit {
        val assertedRow = ReasoningClassRelationship(child, parent, FactOrigin.Asserted, "simple")
        val result = reasoning().copy(classRelationships = listOf(assertedRow))
        assertEquals(2, service.analyze(project(), result, context).size)

        val duplicate = GraphTriple(child, RDFS_SUBCLASS, parent)
        val duplicateProject = project(extraTriples = setOf(duplicate))
        val analysis = service.analyze(duplicateProject, reasoning().copy(
            individualTypes = emptyList(),
            propertyRelationships = emptyList(),
        ), context).single()
        assertEquals(InferenceStageability.AlreadyAsserted, analysis.stageability)
    }

    @Test
    fun rejectsAnonymousTermsAndNonObjectPropertyPredicates(): Unit {
        val anonymous = reasoning().copy(
            classRelationships = listOf(
                ReasoningClassRelationship(BlankNodeResource("child"), parent, FactOrigin.Inferred, "simple"),
            ),
            individualTypes = emptyList(),
            propertyRelationships = emptyList(),
        )
        val anonymousAnalysis = service.analyze(project(), anonymous, context).single()
        assertEquals(InferenceStageability.UnsupportedTerm, anonymousAnalysis.stageability)
        assertNull(anonymousAnalysis.candidate)

        val invalidPredicateProject = project(includeObjectPropertyDeclaration = false)
        val propertyOnly = reasoning().copy(classRelationships = emptyList(), individualTypes = emptyList())
        assertEquals(
            InferenceStageability.InvalidPredicate,
            service.analyze(invalidPredicateProject, propertyOnly, context).single().stageability,
        )
    }

    @Test
    fun createsStableSemanticKeysAndContextBoundFactIds(): Unit {
        val fact = service.analyze(project(), reasoning(), context).first().candidate!!.fact
        val first = service.semanticFactKey(fact)
        val second = service.semanticFactKey(fact)

        assertEquals(first, second)
        assertNotEquals(
            service.factId(context, "graph-1", first),
            service.factId(context.copy(submittingUserId = "bob"), "graph-1", first),
        )
        assertNotEquals(
            service.factId(context, "graph-1", first),
            service.factId(context.copy(projectId = "project-2"), "graph-1", first),
        )
        assertNotEquals(
            service.factId(context, "graph-1", first),
            service.factId(context.copy(reasoningJobId = "job-2"), "graph-1", first),
        )
        assertNotEquals(
            service.factId(context, "graph-1", first),
            service.factId(context, "graph-2", first),
        )
    }

    @Test
    fun resolvesOneManyAndNoSubjectOwnersDeterministically(): Unit {
        val subclassOnly = reasoning().copy(individualTypes = emptyList(), propertyRelationships = emptyList())
        val ambiguousProject = project(
            additionalSources = listOf("secondary"),
            additionalSymbols = listOf(LoadedSymbol(child, "Mortgage Loan", SymbolKind.Class, "secondary")),
        )

        val ambiguous = service.analyze(ambiguousProject, subclassOnly, context).single()
        assertEquals(InferenceStageability.AmbiguousSource, ambiguous.stageability)
        assertEquals(listOf("secondary", "simple"), ambiguous.candidate!!.sourceCandidates.map { it.sourceId })

        val noOwnerBase = project(
            additionalSources = listOf("imports"),
            importMappings = mapOf("https://example.com/imports" to "imports"),
            additionalSymbols = listOf(LoadedSymbol(child, "Mortgage Loan", SymbolKind.Class, "imports")),
        )
        val noOwner = noOwnerBase.copy(
            symbols = noOwnerBase.symbols.filterNot { it.iri == child && it.sourceId == "simple" },
        )
        assertEquals(
            InferenceStageability.NoWritableSource,
            service.analyze(noOwner, subclassOnly, context).single().stageability,
        )
    }

    @Test
    fun permitsSafeImportedReferencesButRejectsUnknownDependence(): Unit {
        val importedProject = project(
            additionalSources = listOf("imports"),
            importMappings = mapOf("https://example.com/imports" to "imports"),
            additionalSymbols = listOf(LoadedSymbol(parent, "Loan", SymbolKind.Class, "imports")),
        ).copy(
            symbols = project(
                additionalSources = listOf("imports"),
                importMappings = mapOf("https://example.com/imports" to "imports"),
                additionalSymbols = listOf(LoadedSymbol(parent, "Loan", SymbolKind.Class, "imports")),
            ).symbols.filterNot { it.iri == parent && it.sourceId == "simple" },
        )
        val subclassOnly = reasoning().copy(individualTypes = emptyList(), propertyRelationships = emptyList())
        val imported = service.analyze(importedProject, subclassOnly, context).single()

        assertEquals(InferenceStageability.Stageable, imported.stageability)
        assertEquals(InferenceImportDependenceState.Imported, imported.candidate!!.importDependence.state)
        assertEquals(listOf("imports"), imported.candidate.importDependence.sourceIds)

        val unknown = service.analyze(
            project(
                additionalSources = listOf("imports"),
                importMappings = mapOf("https://example.com/imports" to "imports"),
            ),
            subclassOnly,
            context,
        ).single()
        assertEquals(InferenceStageability.ImportDependencyUnsafe, unknown.stageability)
    }

    private fun reasoning(): ReasoningResult = ReasoningResult(
        metadata = ReasoningRunMetadata(
            status = ReasoningRunStatus.Completed,
            reasonerName = "HermiT",
            reasonerVersion = "test",
            owlApiVersion = "test",
            fingerprints = ReasoningFingerprints("graph", "imports", "reasoner"),
            importClosureComplete = true,
        ),
        consistency = ConsistencyStatus.Consistent,
        classRelationships = listOf(ReasoningClassRelationship(child, parent, FactOrigin.Inferred, "simple")),
        individualTypes = listOf(ReasoningIndividualType(loan, parent, FactOrigin.Inferred, "simple")),
        propertyRelationships = listOf(
            ReasoningPropertyRelationship(loan, hasBorrower, borrower, FactOrigin.Inferred, "simple"),
        ),
    )

    private fun project(
        extraTriples: Set<GraphTriple> = emptySet(),
        includeObjectPropertyDeclaration: Boolean = true,
        additionalSources: List<String> = emptyList(),
        additionalSymbols: List<LoadedSymbol> = emptyList(),
        importMappings: Map<String, String> = emptyMap(),
    ): EntioProject {
        val sourceIds = listOf("simple") + additionalSources
        val sources = sourceIds.map { ResolvedOntologySource(it, Path.of("/tmp/$it.ttl"), OntologyFormat.Turtle) }
        val declarations = buildSet {
            add(GraphTriple(child, RDF_TYPE, OWL_CLASS))
            add(GraphTriple(parent, RDF_TYPE, OWL_CLASS))
            add(GraphTriple(loan, RDF_TYPE, OWL_NAMED_INDIVIDUAL))
            add(GraphTriple(borrower, RDF_TYPE, OWL_NAMED_INDIVIDUAL))
            if (includeObjectPropertyDeclaration) add(GraphTriple(hasBorrower, RDF_TYPE, OWL_OBJECT_PROPERTY))
            addAll(extraTriples)
        }
        val ontologies = sources.map { source ->
            LoadedOntology(source, GraphState(if (source.id == "simple") declarations else emptySet()))
        }
        val symbols = listOf(
            LoadedSymbol(child, "Mortgage Loan", SymbolKind.Class, "simple"),
            LoadedSymbol(parent, "Loan", SymbolKind.Class, "simple"),
            LoadedSymbol(loan, "Loan 123", SymbolKind.Individual, "simple"),
            LoadedSymbol(borrower, "Borrower", SymbolKind.Individual, "simple"),
            LoadedSymbol(hasBorrower, "has borrower", SymbolKind.Property, "simple"),
        ) + additionalSymbols
        return EntioProject(
            config = EntioProjectConfig(
                name = "test",
                ontologySources = sourceIds.map { OntologySourceReference(it, "$it.ttl", OntologyFormat.Turtle) },
                importMappings = importMappings,
            ),
            resolvedSources = sources,
            ontologies = ontologies,
            symbols = symbols,
            graph = GraphState(declarations),
        )
    }

    private companion object {
        private val RDF_TYPE = Iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
        private val RDFS_SUBCLASS = Iri("http://www.w3.org/2000/01/rdf-schema#subClassOf")
        private val OWL_CLASS = Iri("http://www.w3.org/2002/07/owl#Class")
        private val OWL_OBJECT_PROPERTY = Iri("http://www.w3.org/2002/07/owl#ObjectProperty")
        private val OWL_NAMED_INDIVIDUAL = Iri("http://www.w3.org/2002/07/owl#NamedIndividual")
    }
}
