package com.entio.web.contract

import java.nio.file.Path

public data class RegisteredProject(
    val id: String,
    val displayName: String,
)

public interface ProjectRegistry {
    public fun list(): List<RegisteredProject>

    public fun find(projectId: String): RegisteredProject?

    public fun rootFor(projectId: String): Path
}

public class ProjectRegistryException(
    public val code: String,
    message: String,
) : IllegalArgumentException(message)

public class InMemoryProjectRegistry(
    allowedRoots: Set<Path>,
) : ProjectRegistry {
    private val allowedRoots: Set<Path> = allowedRoots.map(::normalizeProjectRoot).toSet()
    private val projects: MutableMap<String, RegisteredProject> = linkedMapOf()
    private val roots: MutableMap<String, Path> = linkedMapOf()

    public fun register(projectId: String, displayName: String, projectRoot: Path): RegisteredProject {
        require(projectId.isNotBlank()) { "project-id-required" }
        require(displayName.isNotBlank()) { "project-display-name-required" }

        if (projects.containsKey(projectId)) {
            throw ProjectRegistryException("duplicate-project-id", "Project $projectId is already registered.")
        }

        val normalizedRoot = normalizeProjectRoot(projectRoot)
        if (allowedRoots.none(normalizedRoot::startsWith)) {
            throw ProjectRegistryException(
                "project-root-not-allowlisted",
                "Project roots must be within the server allowlist.",
            )
        }

        val descriptor = RegisteredProject(projectId, displayName)
        projects[projectId] = descriptor
        roots[projectId] = normalizedRoot
        return descriptor
    }

    override fun list(): List<RegisteredProject> = projects.values.sortedBy(RegisteredProject::id)

    override fun find(projectId: String): RegisteredProject? = projects[projectId]

    override fun rootFor(projectId: String): Path = roots[projectId]
        ?: throw ProjectRegistryException("unknown-project", "Project $projectId is not registered.")
}
