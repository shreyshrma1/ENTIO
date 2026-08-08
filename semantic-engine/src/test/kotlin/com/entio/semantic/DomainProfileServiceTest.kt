package com.entio.semantic

import com.entio.core.DomainOntologyAvailability
import com.entio.core.DomainOntologyProfile
import com.entio.core.DomainOntologyProfileIdentity
import com.entio.core.DomainProfileDeactivationBlocker
import com.entio.core.DomainProfileDeactivationContext
import com.entio.core.DomainProfileDeactivationPreview
import com.entio.core.EntioResult
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DomainProfileServiceTest {
    private val repository = DomainProfileRepository()
    private val service = DomainProfileService(repository)

    @Test
    fun statusDistinguishesInactiveActiveInvalidAndStaleProfiles(): Unit {
        val inactive = Files.createTempDirectory("entio-domain-status")
        assertEquals(DomainOntologyAvailability.Inactive, service.status(inactive).availability)

        val active = activeProject()
        assertEquals(DomainOntologyAvailability.Active, service.status(active).availability)

        val invalid = activeProject(profile = "not: [yaml")
        assertEquals(DomainOntologyAvailability.Invalid, service.status(invalid).availability)

        val staleProfile = validProfile().replace("release: ${DomainOntologyProfileIdentity.RELEASE}", "release: old")
        val stale = activeProject(profile = staleProfile)
        assertEquals(DomainOntologyAvailability.Stale, service.status(stale).availability)
    }

    @Test
    fun deactivationEligibilityReturnsStableBlockedReasonsWithoutMutation(): Unit {
        val root = activeProject()
        val profileBefore = Files.readAllBytes(root.resolve(DomainOntologyProfileIdentity.PROFILE_PATH))
        val sourceBefore = Files.readAllBytes(root.resolve(DomainOntologyProfileIdentity.MANAGED_SOURCE_PATH))

        val preview = assertIs<EntioResult.Success<DomainProfileDeactivationPreview>>(
            service.previewDeactivation(
                root,
                DomainProfileDeactivationContext(
                    managedSourceStatementCount = 1,
                    hasLocalDependencies = true,
                    hasStagedDependencies = true,
                    hasProposalDependencies = true,
                    hasActiveProvenance = true,
                    compatibilityChecksPassed = false,
                ),
            ),
        ).value

        assertFalse(preview.eligible)
        assertEquals(DomainProfileDeactivationBlocker.entries, preview.blockers)
        assertTrue(profileBefore.contentEquals(Files.readAllBytes(root.resolve(DomainOntologyProfileIdentity.PROFILE_PATH))))
        assertTrue(sourceBefore.contentEquals(Files.readAllBytes(root.resolve(DomainOntologyProfileIdentity.MANAGED_SOURCE_PATH))))
    }

    @Test
    fun emptyActiveProfileIsEligibleForDeactivation(): Unit {
        val preview = assertIs<EntioResult.Success<DomainProfileDeactivationPreview>>(
            service.previewDeactivation(activeProject(), DomainProfileDeactivationContext(managedSourceStatementCount = 0)),
        ).value

        assertTrue(preview.active)
        assertTrue(preview.eligible)
        assertTrue(preview.removeEmptyManagedSource)
    }

    private fun activeProject(profile: String = validProfile()): Path {
        val root = Files.createTempDirectory("entio-domain-profile")
        root.resolve(".entio").createDirectories()
        root.resolve("ontology").createDirectories()
        root.resolve(DomainOntologyProfileIdentity.PROFILE_PATH).writeText(profile)
        root.resolve(DomainOntologyProfileIdentity.MANAGED_SOURCE_PATH).writeText(DomainProfileService.EMPTY_MANAGED_SOURCE)
        return root
    }

    private fun validProfile(): String =
        assertIs<EntioResult.Success<String>>(repository.serialize(DomainOntologyProfile())).value
}
