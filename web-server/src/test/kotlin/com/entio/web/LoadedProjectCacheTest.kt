package com.entio.web

import com.entio.core.EntioResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.appendText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class LoadedProjectCacheTest {
    @Test
    fun reusesUnchangedSnapshotsAndReloadsChangedSources(): Unit {
        val projectRoot = copyProjectFixture()
        val cache = LoadedProjectCache()

        val first = cache.load(projectRoot).success()
        val second = cache.load(projectRoot).success()
        assertSame(first, second)

        val source = first.resolvedSources.first { it.id == "simple" }.path
        source.appendText(System.lineSeparator())

        val reloaded = cache.load(projectRoot).success()
        assertNotSame(first, reloaded)
        assertEquals(first.graph, reloaded.graph)
    }

    private fun copyProjectFixture(): Path {
        val sourceRoot = Path.of("../examples/simple-ontology").toAbsolutePath().normalize()
        val targetRoot = Files.createTempDirectory("entio-loaded-project-cache")
        Files.walk(sourceRoot).use { paths ->
            paths.forEach { source ->
                val target = targetRoot.resolve(sourceRoot.relativize(source).toString())
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target)
                } else {
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
        return targetRoot
    }

    private fun EntioResult<com.entio.core.EntioProject>.success(): com.entio.core.EntioProject =
        when (this) {
            is EntioResult.Success -> value
            is EntioResult.Failure -> error(message)
        }
}
