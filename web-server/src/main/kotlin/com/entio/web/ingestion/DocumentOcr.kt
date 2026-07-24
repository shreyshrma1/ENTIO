package com.entio.web.ingestion

import com.entio.core.DocumentPageGeometry
import com.entio.core.DocumentTextRectangle
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit

public data class TesseractConfiguration(
    val executable: Path,
    val tessdataDirectory: Path,
) {
    init {
        require(executable.isAbsolute && tessdataDirectory.isAbsolute) {
            "Tesseract paths must be absolute startup configuration."
        }
    }
}

internal data class OcrPageResult(
    val text: String,
    val confidence: Int,
    val rectangles: List<DocumentTextRectangle>,
)

internal fun interface DocumentPageOcr {
    fun recognize(image: Path, geometry: DocumentPageGeometry, timeout: Duration): OcrPageResult
}

internal class FixedTesseractOcr(
    configuration: TesseractConfiguration,
) : DocumentPageOcr {
    private val executable = configuration.executable.toAbsolutePath().normalize()
    private val tessdata = configuration.tessdataDirectory.toAbsolutePath().normalize()

    init {
        validateRuntime()
    }

    override fun recognize(image: Path, geometry: DocumentPageGeometry, timeout: Duration): OcrPageResult {
        requireSafeImage(image)
        val outputFile = Files.createTempFile(image.parent, ".entio-ocr-", ".tsv")
        val process = ProcessBuilder(
            executable.toString(),
            image.toString(),
            "stdout",
            "-l",
            "eng",
            "--oem",
            "1",
            "--psm",
            "3",
            "tsv",
        ).apply {
            redirectErrorStream(true)
            redirectOutput(outputFile.toFile())
            environment().clear()
            environment()["TESSDATA_PREFIX"] = tessdata.toString()
        }.start()
        try {
            val deadline = System.nanoTime() + timeout.toNanos()
            while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                if (Files.size(outputFile) > MAX_TESSERACT_OUTPUT_BYTES) {
                    terminate(process)
                    throw DocumentIngestionFailure("ocr-output-limit", "OCR output exceeded the approved bound.")
                }
                if (System.nanoTime() >= deadline) {
                    terminate(process)
                    throw DocumentIngestionFailure("ocr-time-limit", "OCR exceeded the approved document time limit.")
                }
            }
            if (Files.size(outputFile) > MAX_TESSERACT_OUTPUT_BYTES) {
                throw DocumentIngestionFailure("ocr-output-limit", "OCR output exceeded the approved bound.")
            }
            if (process.exitValue() != 0) {
                throw DocumentIngestionFailure("ocr-failed", "The configured OCR runtime could not process this page.")
            }
            return parseTsv(Files.readString(outputFile, StandardCharsets.UTF_8), geometry)
        } finally {
            Files.deleteIfExists(outputFile)
        }
    }

    private fun terminate(process: Process): Unit {
        process.destroy()
        if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
    }

    private fun validateRuntime(): Unit {
        if (Files.isSymbolicLink(executable) ||
            !Files.isRegularFile(executable, LinkOption.NOFOLLOW_LINKS) ||
            !Files.isExecutable(executable)
        ) {
            throw DocumentIngestionFailure("ocr-runtime-invalid", "The configured Tesseract executable is invalid.")
        }
        if (Files.isSymbolicLink(tessdata) || !Files.isDirectory(tessdata, LinkOption.NOFOLLOW_LINKS) ||
            !Files.isReadable(tessdata.resolve("eng.traineddata"))
        ) {
            throw DocumentIngestionFailure("ocr-runtime-invalid", "The configured English OCR data is invalid.")
        }
        val process = ProcessBuilder(executable.toString(), "--version").apply {
            redirectErrorStream(true)
            environment().clear()
        }.start()
        val output = process.inputStream.readNBytes(4_097).toString(StandardCharsets.UTF_8)
        if (!process.waitFor(5, TimeUnit.SECONDS) || process.exitValue() != 0 ||
            !Regex("""(?m)^tesseract 5\.5\.2(?:\s|$)""").containsMatchIn(output)
        ) {
            process.destroyForcibly()
            throw DocumentIngestionFailure("ocr-runtime-invalid", "Entio requires Tesseract 5.5.2.")
        }
    }

    private fun requireSafeImage(image: Path): Unit {
        if (!image.isAbsolute || Files.isSymbolicLink(image) || !Files.isRegularFile(image, LinkOption.NOFOLLOW_LINKS)) {
            throw DocumentIngestionFailure("ocr-image-invalid", "The server-rendered OCR page image is unavailable.")
        }
    }

    private fun parseTsv(tsv: String, geometry: DocumentPageGeometry): OcrPageResult {
        val words = tsv.lineSequence().drop(1).mapNotNull { line ->
            val fields = line.split('\t', limit = 12)
            if (fields.size != 12) return@mapNotNull null
            val text = fields[11].trim()
            val confidence = fields[10].toDoubleOrNull()?.toInt() ?: return@mapNotNull null
            if (text.isEmpty() || confidence < 0) return@mapNotNull null
            val left = fields[6].toDoubleOrNull() ?: return@mapNotNull null
            val top = fields[7].toDoubleOrNull() ?: return@mapNotNull null
            val width = fields[8].toDoubleOrNull() ?: return@mapNotNull null
            val height = fields[9].toDoubleOrNull() ?: return@mapNotNull null
            OcrWord(
                text,
                confidence.coerceIn(0, 100),
                DocumentTextRectangle(
                    (left / geometry.widthPixels).coerceIn(0.0, 1.0),
                    (top / geometry.heightPixels).coerceIn(0.0, 1.0),
                    ((left + width) / geometry.widthPixels).coerceIn(0.0, 1.0),
                    ((top + height) / geometry.heightPixels).coerceIn(0.0, 1.0),
                ),
            )
        }.toList()
        if (words.isEmpty()) throw DocumentIngestionFailure("ocr-no-text", "OCR did not find usable English text on this page.")
        return OcrPageResult(
            text = words.joinToString(" ", transform = OcrWord::text),
            confidence = words.map(OcrWord::confidence).average().toInt(),
            rectangles = words.map(OcrWord::rectangle).distinctBy(DocumentTextRectangle::stableKey),
        )
    }

    private data class OcrWord(
        val text: String,
        val confidence: Int,
        val rectangle: DocumentTextRectangle,
    )

    private companion object {
        const val MAX_TESSERACT_OUTPUT_BYTES: Int = 10 * 1024 * 1024
    }
}
