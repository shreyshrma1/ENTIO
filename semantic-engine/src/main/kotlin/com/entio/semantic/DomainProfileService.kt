package com.entio.semantic

import com.entio.core.DomainOntologyAvailability
import com.entio.core.DomainOntologyIssue
import com.entio.core.DomainOntologyIssueType
import com.entio.core.DomainOntologyProfile
import com.entio.core.DomainOntologyProfileIdentity
import com.entio.core.DomainOntologyStatus
import com.entio.core.DomainProfileActivationPreview
import com.entio.core.DomainProfileDeactivationBlocker
import com.entio.core.DomainProfileDeactivationContext
import com.entio.core.DomainProfileDeactivationPreview
import com.entio.core.EntioResult
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/** Previews profile changes and prepares fixed-file transactions without exposing public mutation wiring. */
public class DomainProfileService(
    private val repository: DomainProfileRepository = DomainProfileRepository(),
    private val transactions: DomainFileTransactionManager = DomainFileTransactionManager(repository),
) {
    public fun status(projectRoot: Path): DomainOntologyStatus {
        if (recoveryRequired(projectRoot)) {
            return DomainOntologyStatus(
                availability = DomainOntologyAvailability.RecoveryRequired,
                issues = listOf(
                    DomainOntologyIssue(
                        DomainOntologyIssueType.TransactionRecoveryRequired,
                        "domain-transaction-recovery-required",
                        "A domain transaction must be recovered before the profile can be used.",
                    ),
                ),
            )
        }
        return when (val result = repository.read(projectRoot)) {
            is EntioResult.Success -> DomainOntologyStatus(
                availability = if (result.value.activeDomainOntology == null) {
                    DomainOntologyAvailability.Inactive
                } else {
                    DomainOntologyAvailability.Active
                },
                profile = result.value.activeDomainOntology?.profile,
            )
            is EntioResult.Failure -> {
                val issue = result.issues.firstOrNull()
                val stale = issue?.code in setOf("stale-domain-profile-release", "stale-domain-profile-package")
                DomainOntologyStatus(
                    availability = if (stale) DomainOntologyAvailability.Stale else DomainOntologyAvailability.Invalid,
                    issues = listOf(
                        DomainOntologyIssue(
                            type = issueType(issue?.code),
                            code = issue?.code ?: "invalid-domain-profile",
                            message = result.message,
                            path = issue?.source,
                        ),
                    ),
                )
            }
        }
    }

    public fun previewActivation(projectRoot: Path): EntioResult<DomainProfileActivationPreview> {
        val read = when (val result = repository.read(projectRoot)) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }
        if (read.activeDomainOntology != null) {
            return failure("domain-profile-already-active", "The approved FIBO domain profile is already active.")
        }
        val compatibility = checkManagedSourceCompatibility(read.paths.managedSource)
        if (compatibility is EntioResult.Failure) return compatibility
        val profile = DomainOntologyProfile()
        val serialized = when (val result = repository.serialize(profile)) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }
        return EntioResult.Success(
            DomainProfileActivationPreview(
                profile = profile,
                profilePath = DomainOntologyProfileIdentity.PROFILE_PATH,
                managedSourcePath = DomainOntologyProfileIdentity.MANAGED_SOURCE_PATH,
                serializedProfile = serialized,
                serializedEmptyManagedSource = EMPTY_MANAGED_SOURCE,
            ),
        )
    }

    public fun prepareActivation(projectRoot: Path): EntioResult<PreparedDomainTransaction> {
        val preview = when (val result = previewActivation(projectRoot)) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }
        return transactions.prepare(
            projectRoot,
            DomainTransactionOperation.Activation,
            mapOf(
                DomainTransactionFile.Profile to preview.serializedProfile.toByteArray(Charsets.UTF_8),
                DomainTransactionFile.ManagedSource to preview.serializedEmptyManagedSource.toByteArray(Charsets.UTF_8),
            ),
        )
    }

    public fun previewDeactivation(
        projectRoot: Path,
        context: DomainProfileDeactivationContext,
    ): EntioResult<DomainProfileDeactivationPreview> {
        val read = when (val result = repository.read(projectRoot)) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }
        if (read.activeDomainOntology == null) {
            return EntioResult.Success(
                DomainProfileDeactivationPreview(active = false, eligible = false, blockers = emptyList()),
            )
        }
        val blockers = buildList {
            if (context.managedSourceStatementCount > 0) add(DomainProfileDeactivationBlocker.ManagedSourceNotEmpty)
            if (context.hasLocalDependencies) add(DomainProfileDeactivationBlocker.LocalDependencyExists)
            if (context.hasStagedDependencies) add(DomainProfileDeactivationBlocker.StagedDependencyExists)
            if (context.hasProposalDependencies) add(DomainProfileDeactivationBlocker.ProposalDependencyExists)
            if (context.hasActiveProvenance) add(DomainProfileDeactivationBlocker.ActiveProvenanceExists)
            if (!context.compatibilityChecksPassed) add(DomainProfileDeactivationBlocker.CompatibilityCheckFailed)
        }.distinct().sortedBy { it.ordinal }
        return EntioResult.Success(
            DomainProfileDeactivationPreview(
                active = true,
                eligible = blockers.isEmpty(),
                blockers = blockers,
                removeEmptyManagedSource = blockers.isEmpty(),
            ),
        )
    }

    public fun prepareDeactivation(
        projectRoot: Path,
        context: DomainProfileDeactivationContext,
    ): EntioResult<PreparedDomainTransaction> {
        val preview = when (val result = previewDeactivation(projectRoot, context)) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }
        if (!preview.active || !preview.eligible) {
            return failure("domain-profile-deactivation-blocked", "The domain profile is not eligible for deactivation.")
        }
        return transactions.prepare(
            projectRoot,
            DomainTransactionOperation.Deactivation,
            mapOf(
                DomainTransactionFile.Profile to null,
                DomainTransactionFile.ManagedSource to null,
            ),
        )
    }

    private fun checkManagedSourceCompatibility(path: Path): EntioResult<Unit> {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return EntioResult.Success(Unit)
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            return failure("invalid-domain-managed-source", "The fixed managed source path is not a regular file.")
        }
        return try {
            if (Files.readString(path) == EMPTY_MANAGED_SOURCE) {
                EntioResult.Success(Unit)
            } else {
                failure(
                    "domain-managed-source-conflict",
                    "The fixed managed source path already contains unrecognized content.",
                )
            }
        } catch (exception: IOException) {
            failure("domain-managed-source-read-failed", "The fixed managed source could not be read.", exception)
        }
    }

    private fun recoveryRequired(projectRoot: Path): Boolean = when (val result = repository.resolvePaths(projectRoot)) {
        is EntioResult.Failure -> false
        is EntioResult.Success -> Files.exists(result.value.transactionJournal) || Files.exists(result.value.transactionDirectory)
    }

    private fun issueType(code: String?): DomainOntologyIssueType = when (code) {
        "unsupported-domain-profile-schema" -> DomainOntologyIssueType.UnsupportedSchema
        "unsupported-domain-profile-source" -> DomainOntologyIssueType.UnsupportedSource
        "stale-domain-profile-release" -> DomainOntologyIssueType.StaleRelease
        "stale-domain-profile-package" -> DomainOntologyIssueType.StalePackageFingerprint
        "missing-domain-managed-source", "invalid-domain-managed-source-id" -> DomainOntologyIssueType.InvalidManagedSource
        "unsafe-domain-project-path" -> DomainOntologyIssueType.UnsafeProjectPath
        else -> DomainOntologyIssueType.MalformedProfile
    }

    private fun failure(code: String, message: String, cause: Throwable? = null): EntioResult.Failure =
        EntioResult.Failure(
            message = message,
            issues = listOf(
                com.entio.core.ValidationIssue(
                    com.entio.core.ValidationSeverity.Error,
                    code,
                    message,
                    "domain-profile",
                ),
            ),
            cause = cause,
        )

    public companion object {
        public const val EMPTY_MANAGED_SOURCE: String =
            "# Entio managed FIBO reuse source. Statements are added only through approved proposals.\n"
    }
}
