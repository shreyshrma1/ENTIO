package com.entio.cli

import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CliExampleProjectTest {
    @Test
    fun exampleProjectSupportsFullCliPath(): Unit {
        val exampleProject = repositoryRoot().resolve("examples/simple-ontology")

        val validation = runCli("validate", exampleProject.toString())
        assertEquals(0, validation.exitCode)
        assertEquals("Validation: valid\n", validation.out)
        assertEquals("", validation.err)

        val symbols = runCli("symbols", exampleProject.toString())
        assertEquals(0, symbols.exitCode)
        assertEquals(
            """
                Individual https://example.com/entio/simple#20874 "Invoice 20874" [simple]
                Class https://example.com/entio/simple#Account "Account" [simple]
                Class https://example.com/entio/simple#Customer "Customer" [simple]
                Class https://example.com/entio/simple#Invoice "Invoice" [simple]
                Individual https://example.com/entio/simple#Shrey "Shrey" [simple]
                Property https://example.com/entio/simple#ownsAccount "owns account" [simple]
                Property https://example.com/entio/simple#recievedInvoice "recieved invoice" [simple]
            """.trimIndent() + "\n",
            symbols.out,
        )
        assertEquals("", symbols.err)

        val afterProject = copyProject(exampleProject)
        Files.writeString(
            afterProject.resolve("ontology/simple.ttl"),
            Files.readString(afterProject.resolve("ontology/simple.ttl")) + """

                <https://example.com/entio/simple#Order> a <http://www.w3.org/2000/01/rdf-schema#Class> ;
                  <http://www.w3.org/2000/01/rdf-schema#label> "Order" .
            """.trimIndent() + "\n",
        )

        val diff = runCli("diff", exampleProject.toString(), afterProject.toString())
        assertEquals(1, diff.exitCode)
        assertTrue(diff.out.contains("Added triple"))
        assertTrue(diff.out.contains("https://example.com/entio/simple#Order"))
        assertEquals("", diff.err)
    }

    @Test
    fun invalidExampleShapeReportsValidationFailure(): Unit {
        val invalidProject = Files.createTempDirectory("entio-invalid-example")
        Files.writeString(
            invalidProject.resolve("entio.yaml"),
            """
                name: invalid-example
                ontologySources: []
            """.trimIndent(),
        )

        val validation = runCli("validate", invalidProject.toString())

        assertEquals(1, validation.exitCode)
        assertTrue(validation.out.contains("Validation: invalid"))
        assertTrue(validation.out.contains("ERROR empty-ontology-sources"))
        assertEquals("", validation.err)
    }

    private fun runCli(vararg args: String): CliRun {
        val out = StringWriter()
        val err = StringWriter()

        val exitCode = EntioCli().execute(
            args = args.toList().toTypedArray(),
            out = PrintWriter(out, true),
            err = PrintWriter(err, true),
        )

        return CliRun(
            exitCode = exitCode,
            out = out.toString(),
            err = err.toString(),
        )
    }

    private fun repositoryRoot(): Path {
        var current = Path.of("").toAbsolutePath().normalize()

        while (true) {
            if (Files.isRegularFile(current.resolve("examples/simple-ontology/entio.yaml"))) {
                return current
            }

            current = current.parent
                ?: error("Could not locate repository root containing examples/simple-ontology.")
        }
    }

    private fun copyProject(source: Path): Path {
        val target = Files.createTempDirectory("entio-example-after")
        val paths = Files.walk(source)

        try {
            paths.forEach { path ->
                val targetPath = target.resolve(source.relativize(path).toString())

                if (Files.isDirectory(path)) {
                    Files.createDirectories(targetPath)
                } else {
                    Files.copy(path, targetPath, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        } finally {
            paths.close()
        }

        return target
    }

    private data class CliRun(
        val exitCode: Int,
        val out: String,
        val err: String,
    )
}
