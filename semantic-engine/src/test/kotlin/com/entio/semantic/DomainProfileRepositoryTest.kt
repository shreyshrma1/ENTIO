package com.entio.semantic

import com.entio.core.DomainOntologyProfile
import com.entio.core.DomainOntologyProfileIdentity
import com.entio.core.EntioResult
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DomainProfileRepositoryTest {
    private val repository = DomainProfileRepository()

    @Test
    fun absentProfileIsInactiveAndResolvesFixedPaths(): Unit {
        val root = Files.createTempDirectory("entio-domain-profile")

        val read = assertIs<EntioResult.Success<DomainProfileReadResult>>(repository.read(root)).value

        assertNull(read.activeDomainOntology)
        val realRoot = root.toRealPath()
        assertEquals(realRoot.resolve(".entio/domain-profile.yaml"), read.paths.profile)
        assertEquals(realRoot.resolve("ontology/fibo-reuse.ttl"), read.paths.managedSource)
        assertEquals(realRoot.resolve(".entio/domain-reuse/events-v1.jsonl"), read.paths.provenance)
    }

    @Test
    fun serializationIsStableAndRoundTripsExactly(): Unit {
        val root = Files.createTempDirectory("entio-domain-profile")
        val serialized = assertIs<EntioResult.Success<String>>(repository.serialize(DomainOntologyProfile())).value
        assertEquals(
            """
            schema: ${DomainOntologyProfileIdentity.SCHEMA}
            sourceId: ${DomainOntologyProfileIdentity.SOURCE_ID}
            release: ${DomainOntologyProfileIdentity.RELEASE}
            packageFingerprint: ${DomainOntologyProfileIdentity.PACKAGE_FINGERPRINT}
            managedSourceId: ${DomainOntologyProfileIdentity.MANAGED_SOURCE_ID}
            """.trimIndent() + "\n",
            serialized,
        )
        root.resolve(".entio").createDirectories()
        root.resolve("ontology").createDirectories()
        root.resolve(DomainOntologyProfileIdentity.PROFILE_PATH).writeText(serialized)
        root.resolve(DomainOntologyProfileIdentity.MANAGED_SOURCE_PATH).writeText(DomainProfileService.EMPTY_MANAGED_SOURCE)

        val read = assertIs<EntioResult.Success<DomainProfileReadResult>>(repository.read(root)).value

        assertEquals(DomainOntologyProfile(), read.activeDomainOntology?.profile)
        assertEquals(serialized, assertIs<EntioResult.Success<String>>(repository.serialize(read.activeDomainOntology!!.profile)).value)
    }

    @Test
    fun rejectsMalformedUnsupportedStaleAndUnexpectedProfiles(): Unit {
        val cases = listOf(
            "not: [yaml" to "malformed-domain-profile",
            validProfile().replace("schema: ${DomainOntologyProfileIdentity.SCHEMA}", "schema: future") to
                "unsupported-domain-profile-schema",
            validProfile().replace("sourceId: ${DomainOntologyProfileIdentity.SOURCE_ID}", "sourceId: other") to
                "unsupported-domain-profile-source",
            validProfile().replace("release: ${DomainOntologyProfileIdentity.RELEASE}", "release: old") to
                "stale-domain-profile-release",
            validProfile().replace(
                "packageFingerprint: ${DomainOntologyProfileIdentity.PACKAGE_FINGERPRINT}",
                "packageFingerprint: old",
            ) to "stale-domain-profile-package",
            validProfile() + "extra: value\n" to "invalid-domain-profile-fields",
        )

        cases.forEach { (profile, expectedCode) ->
            val root = profileProject(profile)
            val failure = assertIs<EntioResult.Failure>(repository.read(root), expectedCode)
            assertEquals(expectedCode, failure.issues.single().code)
        }
    }

    @Test
    fun activeProfileRequiresManagedSource(): Unit {
        val root = profileProject(validProfile(), managedSource = false)

        val failure = assertIs<EntioResult.Failure>(repository.read(root))

        assertEquals("missing-domain-managed-source", failure.issues.single().code)
    }

    @Test
    fun rejectsFixedPathThatCrossesSymbolicLink(): Unit {
        val root = Files.createTempDirectory("entio-domain-profile")
        val outside = Files.createTempDirectory("entio-domain-profile-outside")
        Files.createSymbolicLink(root.resolve(".entio"), outside)

        val failure = assertIs<EntioResult.Failure>(repository.resolvePaths(root))

        assertEquals("unsafe-domain-project-path", failure.issues.single().code)
        assertTrue(Files.exists(outside))
    }

    private fun profileProject(profile: String, managedSource: Boolean = true): Path {
        val root = Files.createTempDirectory("entio-domain-profile")
        root.resolve(".entio").createDirectories()
        root.resolve("ontology").createDirectories()
        root.resolve(DomainOntologyProfileIdentity.PROFILE_PATH).writeText(profile)
        if (managedSource) {
            root.resolve(DomainOntologyProfileIdentity.MANAGED_SOURCE_PATH).writeText(DomainProfileService.EMPTY_MANAGED_SOURCE)
        }
        return root
    }

    private fun validProfile(): String =
        assertIs<EntioResult.Success<String>>(repository.serialize(DomainOntologyProfile())).value
}
