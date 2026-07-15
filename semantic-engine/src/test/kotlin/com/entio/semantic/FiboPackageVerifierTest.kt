package com.entio.semantic

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FiboPackageVerifierTest {
    private val packageRoot: Path = Path.of("..", "external-ontologies", "fibo").toAbsolutePath().normalize()

    @Test
    fun verifiesPinnedPackageAndDeterministicIndexes(): Unit {
        FiboPackageVerifier.verify(packageRoot)

        assertTrue(Files.exists(packageRoot.resolve("source/fibo-master_2026Q2-f59157f.zip")))
        assertTrue(Files.exists(packageRoot.resolve("dependencies/omg-commons-1.3")))
        assertTrue(Files.exists(packageRoot.resolve("checksums/sha256sums.txt")))
    }

    @Test
    fun rejectsMalformedPackageWithoutTouchingCommittedAssets(): Unit {
        val malformed = Files.createTempDirectory("entio-malformed-fibo")
        try {
            Files.writeString(malformed.resolve("manifest.yaml"), "release: wrong\n")

            assertFailsWith<IllegalArgumentException> {
                FiboPackageVerifier.verify(malformed)
            }
        } finally {
            malformed.toFile().deleteRecursively()
        }
    }
}
