package com.entio.semantic

import com.entio.core.EntioResult
import com.entio.core.ExternalDependency
import com.entio.core.ExternalDependencyCategory
import com.entio.core.ExternalDependencyClosure
import com.entio.core.ExternalDependencyRequirement
import com.entio.core.ExternalDependencySelection
import com.entio.core.ExternalDependencySet
import com.entio.core.ExternalDependencyVisibility
import com.entio.core.ExternalCatalogElement
import com.entio.core.ExternalElementCatalogStatus
import com.entio.core.ExternalElementLocality
import com.entio.core.ExternalEntityKind
import com.entio.core.ExternalSemanticDescriptor
import com.entio.core.ExternalOntologyMaturity
import com.entio.core.ExternalPackageFingerprint
import com.entio.core.ExternalProposalIntent
import com.entio.core.ExternalOntologyReference
import com.entio.core.GraphChangeKind
import com.entio.core.GraphState
import com.entio.core.GraphTriple
import com.entio.core.Iri
import com.entio.core.LocalizedText
import com.entio.core.LocalityStatus
import com.entio.core.OntologyEntityDescriptor
import com.entio.core.SemanticDescriptorCommon
import com.entio.core.SemanticDescriptorKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ExternalProposalIntentTranslatorTest {
    private val targetOntology = Iri("https://example.com/entio/simple")
    private val module = Iri("https://spec.edmcouncil.org/fibo/ontology/FND/Parties/Parties/")
    private val externalClass = Iri("https://spec.edmcouncil.org/fibo/ontology/FND/Parties/Parties/Party")

    @Test
    fun reusesExternalClassByAddingImportWithoutCopyingExternalContent(): Unit {
        val intent = ExternalProposalIntent.ReuseExternalClass(
            classIri = externalClass,
            sourceId = "fibo",
            dependencies = approvedDependencies(),
        )

        val result = ExternalProposalIntentTranslator().translate(intent, targetOntology)
        val changeSet = assertIs<EntioResult.Success<com.entio.core.ChangeSet>>(result).value

        assertEquals(1, changeSet.changes.size)
        assertEquals(GraphChangeKind.Addition, changeSet.changes.single().kind)
        assertEquals(targetOntology, changeSet.changes.single().triple.subjectResource)
        assertEquals(module, changeSet.changes.single().triple.objectTerm)
        assertTrue(changeSet.changes.none { it.triple.subjectResource == externalClass })
    }

    @Test
    fun materializesSelectedExternalClassAndItsDirectParent(): Unit {
        val parent = Iri("https://www.omg.org/spec/Commons/PartiesAndSituations/Situation")
        val selected = externalElement(
            iri = externalClass,
            kind = SemanticDescriptorKind.Class,
            label = "Party",
            definition = "A party.",
            parents = listOf(parent),
        )
        val parentElement = externalElement(
            iri = parent,
            kind = SemanticDescriptorKind.Class,
            label = "Situation",
            definition = "A situation.",
        )
        val intent = ExternalProposalIntent.ReuseExternalClass(
            classIri = externalClass,
            sourceId = "fibo",
            dependencies = approvedDependencies(),
        )

        val result = ExternalProposalIntentTranslator().translate(
            intent,
            targetOntology,
            listOf(selected, parentElement),
        )
        val changes = assertIs<EntioResult.Success<com.entio.core.ChangeSet>>(result).value.changes

        assertTrue(changes.any { it.triple.subjectResource == externalClass && it.triple.objectTerm == Iri("http://www.w3.org/2002/07/owl#Class") })
        assertTrue(changes.any { it.triple.subjectResource == externalClass && it.triple.objectTerm == parent })
        assertTrue(changes.any { it.triple.subjectResource == parent && it.triple.objectTerm == Iri("http://www.w3.org/2002/07/owl#Class") })
        assertTrue(changes.any { it.triple.subjectResource == externalClass && it.triple.objectTerm == com.entio.core.RdfLiteral("Party") })
    }

    @Test
    fun doesNotMaterializeUncheckedParent(): Unit {
        val parent = Iri("https://www.omg.org/spec/Commons/PartiesAndSituations/Situation")
        val selected = externalElement(
            iri = externalClass,
            kind = SemanticDescriptorKind.Class,
            label = "Party",
            definition = "A party.",
            parents = listOf(parent),
        )
        val intent = ExternalProposalIntent.ReuseExternalClass(
            classIri = externalClass,
            sourceId = "fibo",
            dependencies = approvedDependencies(),
        )

        val result = ExternalProposalIntentTranslator().translate(intent, targetOntology, listOf(selected))
        val changes = assertIs<EntioResult.Success<com.entio.core.ChangeSet>>(result).value.changes

        assertTrue(changes.none { it.triple.subjectResource == parent })
        assertTrue(changes.none { it.triple.predicate.value == "http://www.w3.org/2000/01/rdf-schema#subClassOf" })
    }

    @Test
    fun independentlyMaterializingParentConnectsToExistingChild(): Unit {
        val parent = Iri("https://www.omg.org/spec/Commons/PartiesAndSituations/Situation")
        val selected = externalElement(
            iri = parent,
            kind = SemanticDescriptorKind.Class,
            label = "Situation",
            definition = "A situation.",
            children = listOf(externalClass),
        )
        val existingGraph = GraphState(
            setOf(
                GraphTriple(externalClass, Iri(RDF_TYPE), Iri("http://www.w3.org/2002/07/owl#Class")),
            ),
        )
        val intent = ExternalProposalIntent.ReuseExternalClass(
            classIri = parent,
            sourceId = "fibo",
            dependencies = approvedDependencies(),
        )

        val result = ExternalProposalIntentTranslator().translate(intent, targetOntology, listOf(selected), existingGraph)
        val changes = assertIs<EntioResult.Success<com.entio.core.ChangeSet>>(result).value.changes

        assertTrue(changes.any { it.triple == GraphTriple(externalClass, RDFS_SUBCLASS_OF, parent) })
    }

    @Test
    fun createsLocalSubclassWithExplicitExternalSuperclassAndImport(): Unit {
        val localClass = Iri("https://example.com/entio/simple#PreferredParty")
        val intent = ExternalProposalIntent.CreateLocalSubclassOfExternalClass(
            localClassIri = localClass,
            externalSuperclassIri = externalClass,
            sourceId = "fibo",
            dependencies = approvedDependencies(),
        )

        val changeSet = assertIs<EntioResult.Success<com.entio.core.ChangeSet>>(
            ExternalProposalIntentTranslator().translate(intent, targetOntology),
        ).value

        assertEquals(3, changeSet.changes.size)
        assertTrue(changeSet.changes.any { it.triple.subjectResource == localClass && it.triple.objectTerm == externalClass })
        assertTrue(changeSet.changes.any { it.triple.subjectResource == targetOntology && it.triple.objectTerm == module })
    }

    @Test
    fun blocksTranslationUntilRequiredDependenciesAreApproved(): Unit {
        val intent = ExternalProposalIntent.ReuseExternalClass(
            classIri = externalClass,
            sourceId = "fibo",
            dependencies = approvedDependencies(selection = ExternalDependencySelection.Missing),
        )

        val result = ExternalProposalIntentTranslator().translate(intent, targetOntology)
        val failure = assertIs<EntioResult.Failure>(result)

        assertTrue(failure.issues.any { it.code == "unapproved-required-dependency" })
    }

    @Test
    fun translatesExternalReferenceModulesIntoImports(): Unit {
        val secondModule = Iri("https://spec.edmcouncil.org/fibo/ontology/FND/Agreements/Contracts/")
        val intent = ExternalProposalIntent.AddExternalOntologyReference(
            reference = ExternalOntologyReference(
                source = "fibo",
                release = "master_2026Q2",
                commitSha = "f59157fe156e3d91b1c045222d0a7dc06b7d78a2",
                packageFingerprint = ExternalPackageFingerprint("fingerprint"),
                modules = listOf(module, secondModule),
            ),
            sourceId = "fibo",
            dependencies = ExternalDependencySet(
                dependencies = listOf(
                    ExternalDependency(
                        category = ExternalDependencyCategory.SourceOntology,
                        requirement = ExternalDependencyRequirement.Required,
                        closure = ExternalDependencyClosure.Direct,
                        visibility = ExternalDependencyVisibility.UserVisible,
                        selection = ExternalDependencySelection.NewlySelected,
                        reason = "approved",
                        externalIri = module,
                        sourceModule = module,
                        maturity = ExternalOntologyMaturity.Release,
                    ),
                ),
            ),
        )

        val changeSet = assertIs<EntioResult.Success<com.entio.core.ChangeSet>>(
            ExternalProposalIntentTranslator().translate(intent, targetOntology),
        ).value

        assertEquals(
            setOf(module, secondModule),
            changeSet.changes.mapNotNull { it.triple.objectTerm as? Iri }.toSet(),
        )
    }

    private fun approvedDependencies(
        selection: ExternalDependencySelection = ExternalDependencySelection.NewlySelected,
    ): ExternalDependencySet = ExternalDependencySet(
        dependencies = listOf(
            ExternalDependency(
                category = ExternalDependencyCategory.SourceOntology,
                requirement = ExternalDependencyRequirement.Required,
                closure = ExternalDependencyClosure.Direct,
                visibility = ExternalDependencyVisibility.UserVisible,
                selection = selection,
                reason = "approved",
                externalIri = module,
                sourceModule = module,
                maturity = ExternalOntologyMaturity.Release,
            ),
        ),
    )

    private fun externalElement(
        iri: Iri,
        kind: SemanticDescriptorKind,
        label: String,
        definition: String,
        parents: List<Iri> = emptyList(),
        children: List<Iri> = emptyList(),
    ): ExternalCatalogElement {
        val common = SemanticDescriptorCommon(
            entity = iri,
            kind = kind,
            sourceId = "fibo",
            sourceOntologyId = module.value,
            locality = LocalityStatus.Imported,
            preferredLabel = LocalizedText(label),
            definitions = listOf(LocalizedText(definition)),
        )
        val descriptor = OntologyEntityDescriptor.Class(common, directSuperclasses = parents, directSubclasses = children)
        return ExternalCatalogElement(
            descriptor = ExternalSemanticDescriptor(
                descriptor = descriptor,
                sourceId = "fibo",
                release = "master_2026Q2",
                moduleIri = module,
                domain = "FND",
                maturity = ExternalOntologyMaturity.Release,
                locality = ExternalElementLocality.External,
                catalogStatus = ExternalElementCatalogStatus.Available,
            ),
            kind = ExternalEntityKind.Class,
        )
    }

    private companion object {
        private const val RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
        private val RDFS_SUBCLASS_OF = Iri("http://www.w3.org/2000/01/rdf-schema#subClassOf")
    }
}
