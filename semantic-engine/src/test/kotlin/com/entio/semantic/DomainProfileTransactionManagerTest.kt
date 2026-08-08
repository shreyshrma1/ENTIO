package com.entio.semantic

import com.entio.core.DomainOntologyProfileIdentity
import com.entio.core.EntioResult
import com.entio.core.ValidationIssue
import com.entio.core.ValidationSeverity
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DomainProfileTransactionManagerTest {
    private val repository = DomainProfileRepository()
    private val transactions = DomainFileTransactionManager(repository)
    private val service = DomainProfileService(repository, transactions)

    @Test
    fun previewMakesNoChangesAndPreparedActivationCreatesOnlyTransactionArtifacts(): Unit {
        val root = Files.createTempDirectory("entio-domain-transaction")

        assertIs<EntioResult.Success<*>>(service.previewActivation(root))
        assertEquals(emptyList(), Files.list(root).use { it.toList() })

        assertIs<EntioResult.Success<PreparedDomainTransaction>>(service.prepareActivation(root))
        val paths = assertIs<EntioResult.Success<com.entio.core.DomainOntologyProjectPaths>>(
            repository.resolvePaths(root),
        ).value
        assertFalse(Files.exists(paths.profile))
        assertFalse(Files.exists(paths.managedSource))
        assertTrue(Files.isRegularFile(paths.transactionJournal))
        assertTrue(Files.isDirectory(paths.transactionDirectory))
    }

    @Test
    fun activationCommitsFixedFilesAndDeactivationRemovesOnlyEligibleEmptyFiles(): Unit {
        val root = Files.createTempDirectory("entio-domain-transaction")
        val prepared = assertIs<EntioResult.Success<PreparedDomainTransaction>>(service.prepareActivation(root)).value

        assertIs<EntioResult.Success<Unit>>(transactions.commit(prepared))
        val paths = assertIs<EntioResult.Success<com.entio.core.DomainOntologyProjectPaths>>(
            repository.resolvePaths(root),
        ).value
        assertTrue(Files.isRegularFile(paths.profile))
        assertEquals(DomainProfileService.EMPTY_MANAGED_SOURCE, paths.managedSource.readText())
        assertFalse(Files.exists(paths.transactionJournal))

        val context = com.entio.core.DomainProfileDeactivationContext(managedSourceStatementCount = 0)
        val deactivation = assertIs<EntioResult.Success<PreparedDomainTransaction>>(
            service.prepareDeactivation(root, context),
        ).value
        assertIs<EntioResult.Success<Unit>>(transactions.commit(deactivation))
        assertFalse(Files.exists(paths.profile))
        assertFalse(Files.exists(paths.managedSource))
    }

    @Test
    fun simulatedReplacementFailureRestoresOriginalState(): Unit {
        val root = Files.createTempDirectory("entio-domain-transaction")
        val prepared = assertIs<EntioResult.Success<PreparedDomainTransaction>>(service.prepareActivation(root)).value
        val paths = assertIs<EntioResult.Success<com.entio.core.DomainOntologyProjectPaths>>(
            repository.resolvePaths(root),
        ).value
        Files.delete(paths.transactionDirectory.resolve("ManagedSource.intended"))

        val failure = assertIs<EntioResult.Failure>(transactions.commit(prepared))

        assertEquals("domain-transaction-rolled-back", failure.issues.single().code)
        assertFalse(Files.exists(paths.profile))
        assertFalse(Files.exists(paths.managedSource))
        assertFalse(Files.exists(paths.transactionJournal))
    }

    @Test
    fun failedDeactivationVerificationRestoresBothExistingFiles(): Unit {
        val root = Files.createTempDirectory("entio-domain-transaction")
        val activation = assertIs<EntioResult.Success<PreparedDomainTransaction>>(service.prepareActivation(root)).value
        assertIs<EntioResult.Success<Unit>>(transactions.commit(activation))
        val paths = assertIs<EntioResult.Success<com.entio.core.DomainOntologyProjectPaths>>(
            repository.resolvePaths(root),
        ).value
        val profileBefore = Files.readAllBytes(paths.profile)
        val sourceBefore = Files.readAllBytes(paths.managedSource)
        val deactivation = assertIs<EntioResult.Success<PreparedDomainTransaction>>(
            service.prepareDeactivation(
                root,
                com.entio.core.DomainProfileDeactivationContext(managedSourceStatementCount = 0),
            ),
        ).value

        val failure = assertIs<EntioResult.Failure>(
            transactions.commit(deactivation) {
                EntioResult.Failure(
                    "semantic verification failed",
                    listOf(ValidationIssue(ValidationSeverity.Error, "verification-failed", "failed", "test")),
                )
            },
        )

        assertEquals("domain-transaction-rolled-back", failure.issues.single().code)
        assertTrue(profileBefore.contentEquals(Files.readAllBytes(paths.profile)))
        assertTrue(sourceBefore.contentEquals(Files.readAllBytes(paths.managedSource)))
        assertFalse(Files.exists(paths.transactionJournal))
    }

    @Test
    fun recoveryRemovesOrphanEmptyManagedSourceProvenByJournal(): Unit {
        val root = Files.createTempDirectory("entio-domain-transaction")
        assertIs<EntioResult.Success<PreparedDomainTransaction>>(service.prepareActivation(root))
        val paths = assertIs<EntioResult.Success<com.entio.core.DomainOntologyProjectPaths>>(
            repository.resolvePaths(root),
        ).value
        paths.managedSource.parent.createDirectories()
        Files.move(
            paths.transactionDirectory.resolve("ManagedSource.intended"),
            paths.managedSource,
            StandardCopyOption.REPLACE_EXISTING,
        )

        val recovery = assertIs<EntioResult.Success<DomainTransactionRecoveryOutcome>>(transactions.recover(root)).value

        assertEquals(DomainTransactionRecoveryOutcome.RolledBackToOriginal, recovery)
        assertFalse(Files.exists(paths.managedSource))
        assertFalse(Files.exists(paths.profile))
        assertFalse(Files.exists(paths.transactionJournal))
    }

    @Test
    fun recoveryPreservesJournalWhenTargetBytesAreUnknown(): Unit {
        val root = Files.createTempDirectory("entio-domain-transaction")
        assertIs<EntioResult.Success<PreparedDomainTransaction>>(service.prepareActivation(root))
        val paths = assertIs<EntioResult.Success<com.entio.core.DomainOntologyProjectPaths>>(
            repository.resolvePaths(root),
        ).value
        paths.managedSource.parent.createDirectories()
        paths.managedSource.writeText("unexpected")

        val failure = assertIs<EntioResult.Failure>(transactions.recover(root))

        assertEquals("domain-transaction-unknown-state", failure.issues.single().code)
        assertTrue(Files.exists(paths.transactionJournal))
        assertEquals("unexpected", paths.managedSource.readText())
    }

    @Test
    fun recoveryRejectsJournalPathsOutsideFixedTransactionDirectory(): Unit {
        val root = Files.createTempDirectory("entio-domain-transaction")
        assertIs<EntioResult.Success<PreparedDomainTransaction>>(service.prepareActivation(root))
        val paths = assertIs<EntioResult.Success<com.entio.core.DomainOntologyProjectPaths>>(
            repository.resolvePaths(root),
        ).value
        val journal = Files.readString(paths.transactionJournal)
            .replace("ManagedSource.intended", "../../outside")
        Files.writeString(paths.transactionJournal, journal)

        val failure = assertIs<EntioResult.Failure>(transactions.recover(root))

        assertEquals("invalid-domain-transaction-journal", failure.issues.single().code)
        assertTrue(Files.exists(paths.transactionJournal))
    }

    @Test
    fun allIntendedCrashStateIsRolledBackWhenSemanticVerificationFails(): Unit {
        val root = Files.createTempDirectory("entio-domain-transaction")
        assertIs<EntioResult.Success<PreparedDomainTransaction>>(service.prepareActivation(root))
        val paths = assertIs<EntioResult.Success<com.entio.core.DomainOntologyProjectPaths>>(
            repository.resolvePaths(root),
        ).value
        paths.managedSource.parent.createDirectories()
        Files.move(paths.transactionDirectory.resolve("ManagedSource.intended"), paths.managedSource)
        Files.move(paths.transactionDirectory.resolve("Profile.intended"), paths.profile)

        val recovery = assertIs<EntioResult.Success<DomainTransactionRecoveryOutcome>>(
            transactions.recover(root) {
                EntioResult.Failure(
                    "semantic verification failed",
                    listOf(ValidationIssue(ValidationSeverity.Error, "verification-failed", "failed", "test")),
                )
            },
        ).value

        assertEquals(DomainTransactionRecoveryOutcome.RolledBackToOriginal, recovery)
        assertFalse(Files.exists(paths.profile))
        assertFalse(Files.exists(paths.managedSource))
        assertFalse(Files.exists(paths.transactionJournal))
    }

    @Test
    fun copiedFixtureIsPreservedByPreviewAndPreparation(): Unit {
        val source = java.nio.file.Path.of("..", "examples", "simple-ontology").toAbsolutePath().normalize()
        val root = Files.createTempDirectory("entio-domain-fixture")
        Files.walk(source).use { entries ->
            entries.forEach { path ->
                val target = root.resolve(source.relativize(path).toString())
                if (Files.isDirectory(path)) Files.createDirectories(target) else Files.copy(path, target)
            }
        }
        val original = Files.walk(root).use { entries ->
            entries.filter(Files::isRegularFile).toList()
                .associate { root.relativize(it).toString() to Files.readAllBytes(it) }
        }

        assertIs<EntioResult.Success<*>>(service.previewActivation(root))
        assertIs<EntioResult.Success<PreparedDomainTransaction>>(service.prepareActivation(root))

        original.forEach { (relative, bytes) -> assertTrue(bytes.contentEquals(Files.readAllBytes(root.resolve(relative)))) }
        assertFalse(Files.exists(root.resolve(DomainOntologyProfileIdentity.PROFILE_PATH)))
        assertFalse(Files.exists(root.resolve(DomainOntologyProfileIdentity.MANAGED_SOURCE_PATH)))
        assertIs<EntioResult.Success<DomainTransactionRecoveryOutcome>>(transactions.recover(root))
    }
}
