package com.entio.semantic

import com.entio.core.ActiveDomainOntology
import com.entio.core.DomainOntologyProfile
import com.entio.core.DomainOntologyProfileIdentity
import com.entio.core.DomainOntologyProjectPaths
import com.entio.core.EntioResult
import com.entio.core.ValidationIssue
import com.entio.core.ValidationSeverity
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings

public data class DomainProfileReadResult(
    public val activeDomainOntology: ActiveDomainOntology?,
    public val paths: DomainOntologyProjectPaths,
)

/** Reads and deterministically serializes the exact Entio-owned domain profile. */
public class DomainProfileRepository {
    public fun read(projectRoot: Path): EntioResult<DomainProfileReadResult> {
        val paths = when (val result = resolvePaths(projectRoot)) {
            is EntioResult.Failure -> return result
            is EntioResult.Success -> result.value
        }
        if (!Files.exists(paths.profile, LinkOption.NOFOLLOW_LINKS)) {
            return EntioResult.Success(DomainProfileReadResult(null, paths))
        }
        if (!Files.isRegularFile(paths.profile, LinkOption.NOFOLLOW_LINKS)) {
            return failure("invalid-domain-profile-file", "The domain profile must be a regular file.", paths.profile)
        }

        val loaded = try {
            yamlLoader.loadFromString(Files.readString(paths.profile))
        } catch (exception: IOException) {
            return failure("domain-profile-read-failed", "The domain profile could not be read.", paths.profile, exception)
        } catch (exception: RuntimeException) {
            return failure("malformed-domain-profile", "The domain profile is not valid YAML.", paths.profile, exception)
        }
        val root = loaded as? Map<*, *>
            ?: return failure("malformed-domain-profile", "The domain profile must contain one YAML mapping.", paths.profile)
        if (root.keys != PROFILE_KEYS) {
            return failure(
                "invalid-domain-profile-fields",
                "The domain profile must contain exactly: ${PROFILE_KEYS.joinToString()}.",
                paths.profile,
            )
        }
        val values = linkedMapOf<String, String>()
        PROFILE_KEYS.forEach { key ->
            val value = root[key] as? String
                ?: return failure("invalid-domain-profile-value", "Domain profile '$key' must be a string.", paths.profile)
            values[key] = value
        }
        validate("schema", values.getValue("schema"), DomainOntologyProfileIdentity.SCHEMA, "unsupported-domain-profile-schema", paths.profile)
            ?.let { return it }
        validate("sourceId", values.getValue("sourceId"), DomainOntologyProfileIdentity.SOURCE_ID, "unsupported-domain-profile-source", paths.profile)
            ?.let { return it }
        validate("release", values.getValue("release"), DomainOntologyProfileIdentity.RELEASE, "stale-domain-profile-release", paths.profile)
            ?.let { return it }
        validate(
            "packageFingerprint",
            values.getValue("packageFingerprint"),
            DomainOntologyProfileIdentity.PACKAGE_FINGERPRINT,
            "stale-domain-profile-package",
            paths.profile,
        )?.let { return it }
        validate(
            "managedSourceId",
            values.getValue("managedSourceId"),
            DomainOntologyProfileIdentity.MANAGED_SOURCE_ID,
            "invalid-domain-managed-source-id",
            paths.profile,
        )?.let { return it }
        if (!Files.isRegularFile(paths.managedSource, LinkOption.NOFOLLOW_LINKS)) {
            return failure(
                "missing-domain-managed-source",
                "An active domain profile requires '${DomainOntologyProfileIdentity.MANAGED_SOURCE_PATH}'.",
                paths.managedSource,
            )
        }
        return EntioResult.Success(
            DomainProfileReadResult(
                activeDomainOntology = ActiveDomainOntology(DomainOntologyProfile(), paths),
                paths = paths,
            ),
        )
    }

    public fun serialize(profile: DomainOntologyProfile): EntioResult<String> {
        validateProfile(profile)?.let { return it }
        return EntioResult.Success(
            buildString {
                appendLine("schema: ${profile.schema}")
                appendLine("sourceId: ${profile.sourceId}")
                appendLine("release: ${profile.release}")
                appendLine("packageFingerprint: ${profile.packageFingerprint}")
                appendLine("managedSourceId: ${profile.managedSourceId}")
            },
        )
    }

    public fun resolvePaths(projectRoot: Path): EntioResult<DomainOntologyProjectPaths> {
        val realRoot = try {
            projectRoot.toRealPath()
        } catch (exception: IOException) {
            return failure("invalid-domain-project-root", "The project root could not be resolved.", projectRoot, exception)
        }
        val profile = safePath(realRoot, DomainOntologyProfileIdentity.PROFILE_PATH) ?: return unsafe(realRoot)
        val managed = safePath(realRoot, DomainOntologyProfileIdentity.MANAGED_SOURCE_PATH) ?: return unsafe(realRoot)
        val provenance = safePath(realRoot, DomainOntologyProfileIdentity.PROVENANCE_PATH) ?: return unsafe(realRoot)
        val journal = safePath(realRoot, DomainOntologyProfileIdentity.TRANSACTION_JOURNAL_PATH) ?: return unsafe(realRoot)
        val transactionDirectory = safePath(realRoot, DomainOntologyProfileIdentity.TRANSACTION_DIRECTORY_PATH)
            ?: return unsafe(realRoot)
        return EntioResult.Success(
            DomainOntologyProjectPaths(realRoot, profile, managed, provenance, journal, transactionDirectory),
        )
    }

    private fun safePath(root: Path, relative: String): Path? {
        val path = root.resolve(Path.of(relative)).normalize()
        if (!path.startsWith(root)) return null
        var current = root
        root.relativize(path).forEach { segment ->
            current = current.resolve(segment)
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) return null
        }
        return path
    }

    private fun validateProfile(profile: DomainOntologyProfile): EntioResult.Failure? =
        validate("schema", profile.schema, DomainOntologyProfileIdentity.SCHEMA, "unsupported-domain-profile-schema", null)
            ?: validate("sourceId", profile.sourceId, DomainOntologyProfileIdentity.SOURCE_ID, "unsupported-domain-profile-source", null)
            ?: validate("release", profile.release, DomainOntologyProfileIdentity.RELEASE, "stale-domain-profile-release", null)
            ?: validate(
                "packageFingerprint",
                profile.packageFingerprint,
                DomainOntologyProfileIdentity.PACKAGE_FINGERPRINT,
                "stale-domain-profile-package",
                null,
            )
            ?: validate(
                "managedSourceId",
                profile.managedSourceId,
                DomainOntologyProfileIdentity.MANAGED_SOURCE_ID,
                "invalid-domain-managed-source-id",
                null,
            )

    private fun validate(field: String, actual: String, expected: String, code: String, source: Path?): EntioResult.Failure? =
        if (actual == expected) null else failure(code, "Domain profile '$field' does not match the approved value.", source)

    private fun unsafe(root: Path): EntioResult.Failure = failure(
        "unsafe-domain-project-path",
        "A fixed domain profile path escapes the project root or crosses a symbolic link.",
        root,
    )

    private fun failure(code: String, message: String, source: Path?, cause: Throwable? = null): EntioResult.Failure =
        EntioResult.Failure(
            message = message,
            issues = listOf(ValidationIssue(ValidationSeverity.Error, code, message, source?.toString() ?: "domain-profile")),
            cause = cause,
        )

    private companion object {
        private val PROFILE_KEYS: Set<String> = linkedSetOf(
            "schema",
            "sourceId",
            "release",
            "packageFingerprint",
            "managedSourceId",
        )
        private val yamlLoader: Load = Load(LoadSettings.builder().setLabel("domain-profile.yaml").build())
    }
}
