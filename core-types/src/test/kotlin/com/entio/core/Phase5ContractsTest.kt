package com.entio.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Phase5ContractsTest {
    @Test
    fun manifestRequiresApprovedPackageIdentity(): Unit {
        val manifest = approvedManifest()

        assertEquals(Phase5PackageIdentity.RELEASE, manifest.release)
        assertEquals(Phase5PackageIdentity.CURATED_SEEDS, manifest.curatedSeedOntologyIris)
        assertFailsWith<IllegalArgumentException> {
            manifest.copy(commitSha = "different")
        }
        assertFailsWith<IllegalArgumentException> {
            manifest.copy(curatedSeedOntologyIris = emptyList())
        }
    }

    @Test
    fun externalReferencesAndDescriptorsPreserveStableStates(): Unit {
        val reference = ExternalOntologyReference(
            source = "fibo",
            release = Phase5PackageIdentity.RELEASE,
            commitSha = Phase5PackageIdentity.COMMIT_SHA,
            packageFingerprint = ExternalPackageFingerprint("package-hash"),
            modules = listOf(Phase5PackageIdentity.CURATED_SEEDS.first()),
        )
        val descriptor = ExternalSemanticDescriptor(
            descriptor = OntologyEntityDescriptor.Class(
                common = SemanticDescriptorCommon(
                    entity = BlankNodeResource("parser-local"),
                    kind = SemanticDescriptorKind.Class,
                    sourceId = "fibo",
                ),
            ),
            sourceId = "fibo",
            release = Phase5PackageIdentity.RELEASE,
            moduleIri = Phase5PackageIdentity.CURATED_SEEDS.first(),
            domain = "Foundations",
            maturity = ExternalOntologyMaturity.Release,
            locality = ExternalElementLocality.External,
            catalogStatus = ExternalElementCatalogStatus.Curated,
        )

        assertEquals("fibo", reference.source)
        assertEquals(ExternalElementCatalogStatus.Curated, descriptor.catalogStatus)
        assertEquals(BlankNodeResource("parser-local"), descriptor.descriptor.common.entity)
    }

    @Test
    fun alreadyUsedDependsOnlyOnAssertedConditions(): Unit {
        val unused = ExternalAlreadyUsedState(Iri("https://example.com/fibo/Loan"))
        val asserted = unused.copy(assertedInLocalGraph = true)

        assertFalse(unused.alreadyUsed)
        assertTrue(asserted.alreadyUsed)
    }

    @Test
    fun dependencyFlagsSeparateUserReviewFromPackageRuntime(): Unit {
        val dependencies = ExternalDependencySet(
            dependencies = listOf(
                ExternalDependency(
                    category = ExternalDependencyCategory.SemanticParent,
                    requirement = ExternalDependencyRequirement.Required,
                    closure = ExternalDependencyClosure.Direct,
                    visibility = ExternalDependencyVisibility.UserVisible,
                    selection = ExternalDependencySelection.NewlySelected,
                    reason = "The selected class has this direct parent.",
                ),
                ExternalDependency(
                    category = ExternalDependencyCategory.PackageRuntime,
                    requirement = ExternalDependencyRequirement.Required,
                    closure = ExternalDependencyClosure.PackageTransitive,
                    visibility = ExternalDependencyVisibility.ImplementationOnly,
                    selection = ExternalDependencySelection.AlreadyAvailable,
                    reason = "Included in the fixed package closure.",
                ),
            ),
        )

        assertEquals(1, dependencies.requiredUserVisibleDependencies.size)
        assertEquals(ExternalDependencyCategory.SemanticParent, dependencies.requiredUserVisibleDependencies.single().category)
    }

    @Test
    fun searchQueryEnforcesStablePageBoundsAndProposalKinds(): Unit {
        val query = ExternalSchemaSearchQuery(
            text = "business loan",
            pageSize = 100,
            context = ExternalSearchContext(domainRequired = true),
        )
        val intent = ExternalProposalIntent.CreateLocalSubclassOfExternalClass(
            localClassIri = Iri("https://example.com/local#CommercialLoan"),
            externalSuperclassIri = Iri("https://spec.edmcouncil.org/fibo/ontology/FND/ProductsAndServices/Loans/Loan/"),
            sourceId = "simple",
        )

        assertEquals(100, query.pageSize)
        assertTrue(query.context.domainRequired)
        assertEquals(ExternalProposalIntentKind.CreateLocalSubclass, intent.kind)
        assertFailsWith<IllegalArgumentException> {
            query.copy(pageSize = 101)
        }
    }

    private fun approvedManifest(): ExternalOntologyManifest = ExternalOntologyManifest(
        sourceId = Phase5PackageIdentity.SOURCE_ID,
        release = Phase5PackageIdentity.RELEASE,
        commitSha = Phase5PackageIdentity.COMMIT_SHA,
        packageSchema = Phase5PackageIdentity.PACKAGE_SCHEMA,
        catalogSchema = Phase5PackageIdentity.CATALOG_SCHEMA,
        checksumAlgorithm = Phase5PackageIdentity.CHECKSUM_ALGORITHM,
        commonsVersion = Phase5PackageIdentity.COMMONS_VERSION,
        packageFingerprint = ExternalPackageFingerprint("package-hash"),
        curatedSeedOntologyIris = Phase5PackageIdentity.CURATED_SEEDS,
    )
}
