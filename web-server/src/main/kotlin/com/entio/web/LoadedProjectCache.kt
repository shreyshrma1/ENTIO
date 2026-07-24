package com.entio.web

import com.entio.core.EntioProject
import com.entio.core.EntioResult
import com.entio.semantic.ProjectLoader
import java.nio.file.Files
import java.nio.file.Path

/**
 * Reuses immutable loaded-project snapshots while their config and ontology sources remain unchanged.
 *
 * Access is synchronized so concurrent read routes do not parse the same project more than once.
 */
public class LoadedProjectCache(
    private val projectLoader: ProjectLoader = ProjectLoader(),
    private val maximumEntries: Int = DEFAULT_MAXIMUM_ENTRIES,
) {
    init {
        require(maximumEntries > 0) { "maximumEntries must be positive." }
    }

    private val entries: LinkedHashMap<Path, CacheEntry> =
        object : LinkedHashMap<Path, CacheEntry>(maximumEntries, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Path, CacheEntry>?): Boolean =
                size > maximumEntries
        }

    @Synchronized
    public fun load(projectRoot: Path): EntioResult<EntioProject> {
        val normalizedRoot = projectRoot.toAbsolutePath().normalize()
        val cached = entries[normalizedRoot]
        if (cached != null && cached.stamps == stamps(normalizedRoot, cached.project)) {
            return EntioResult.Success(cached.project)
        }

        val loaded = projectLoader.loadProject(normalizedRoot)
        if (loaded is EntioResult.Success) {
            entries[normalizedRoot] = CacheEntry(
                project = loaded.value,
                stamps = stamps(normalizedRoot, loaded.value),
            )
        } else {
            entries.remove(normalizedRoot)
        }
        return loaded
    }

    private fun stamps(projectRoot: Path, project: EntioProject): List<FileStamp> =
        (listOf(projectRoot.resolve(CONFIG_FILE_NAME)) + project.resolvedSources.map { it.path })
            .distinct()
            .sortedBy { it.toAbsolutePath().normalize().toString() }
            .map(::stamp)

    private fun stamp(path: Path): FileStamp {
        val normalized = path.toAbsolutePath().normalize()
        if (!Files.exists(normalized)) {
            return FileStamp(normalized, exists = false, size = null, modifiedMillis = null)
        }
        return FileStamp(
            path = normalized,
            exists = true,
            size = Files.size(normalized),
            modifiedMillis = Files.getLastModifiedTime(normalized).toMillis(),
        )
    }

    private data class CacheEntry(
        val project: EntioProject,
        val stamps: List<FileStamp>,
    )

    private data class FileStamp(
        val path: Path,
        val exists: Boolean,
        val size: Long?,
        val modifiedMillis: Long?,
    )

    private companion object {
        const val CONFIG_FILE_NAME: String = "entio.yaml"
        const val DEFAULT_MAXIMUM_ENTRIES: Int = 16
        const val LOAD_FACTOR: Float = 0.75f
    }
}
