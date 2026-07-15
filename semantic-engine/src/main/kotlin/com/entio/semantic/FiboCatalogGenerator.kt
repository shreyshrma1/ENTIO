package com.entio.semantic

import com.entio.core.ExternalOntologyMaturity
import com.entio.core.Phase5PackageIdentity
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Locale
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.RDFNode
import org.apache.jena.rdf.model.Resource
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr

/** Generates deterministic Phase 5 package indexes from committed local assets. */
public object FiboCatalogGenerator {
    private val OWL_CLASS = "http://www.w3.org/2002/07/owl#Class"
    private val OWL_OBJECT_PROPERTY = "http://www.w3.org/2002/07/owl#ObjectProperty"
    private val OWL_DATATYPE_PROPERTY = "http://www.w3.org/2002/07/owl#DatatypeProperty"
    private val OWL_ONTOLOGY = "http://www.w3.org/2002/07/owl#Ontology"
    private val OWL_IMPORTS = "http://www.w3.org/2002/07/owl#imports"
    private val OWL_VERSION_IRI = "http://www.w3.org/2002/07/owl#versionIRI"
    private val RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
    private val RDFS_LABEL = "http://www.w3.org/2000/01/rdf-schema#label"
    private val RDFS_SUBCLASS = "http://www.w3.org/2000/01/rdf-schema#subClassOf"
    private val RDFS_DOMAIN = "http://www.w3.org/2000/01/rdf-schema#domain"
    private val RDFS_RANGE = "http://www.w3.org/2000/01/rdf-schema#range"
    private val SKOS_DEFINITION = "http://www.w3.org/2004/02/skos/core#definition"
    private val SKOS_ALT_LABEL = "http://www.w3.org/2004/02/skos/core#altLabel"
    private val FIBO_MATURITY = "https://spec.edmcouncil.org/fibo/ontology/FND/Utilities/AnnotationVocabulary/hasMaturityLevel"
    private val FIBO_RELEASE = "https://spec.edmcouncil.org/fibo/ontology/FND/Utilities/AnnotationVocabulary/Release"
    private val FIBO_PROVISIONAL = "https://spec.edmcouncil.org/fibo/ontology/FND/Utilities/AnnotationVocabulary/Provisional"
    private val FIBO_INFORMATIVE = "https://spec.edmcouncil.org/fibo/ontology/FND/Utilities/AnnotationVocabulary/Informative"

    @JvmStatic
    public fun main(args: Array<String>): Unit {
        require(args.size == 1) { "Expected the FIBO package root." }
        // FIBO RDF/XML uses large, local entity declarations for namespace vocabulary.
        // The generator never resolves external entities and reads only committed files.
        System.setProperty("jdk.xml.totalEntitySizeLimit", "0")
        System.setProperty("jdk.xml.maxGeneralEntitySizeLimit", "0")
        System.setProperty("jdk.xml.maxParameterEntitySizeLimit", "0")
        System.setProperty("jdk.xml.entityExpansionLimit", "0")
        generate(Path.of(args[0]), Path.of(args[0]))
    }

    public fun generate(packageRoot: Path, outputRoot: Path): Unit {
        val sourceRoot = packageRoot.resolve("source")
        val sourceFiles = Files.walk(sourceRoot).use { stream ->
            stream.filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().endsWith(".rdf") || it.fileName.toString().endsWith(".ttl") }
                .sorted()
                .toList()
        }
        require(sourceFiles.isNotEmpty()) { "FIBO package contains no ontology files." }
        val archive = packageRoot.resolve("source/fibo-master_2026Q2-f59157f.zip")
        require(Files.exists(archive)) { "Missing pinned FIBO source archive." }

        val ontologyMappings = linkedMapOf<String, String>()
        val records = mutableListOf<CatalogRecord>()
        sourceFiles.forEach { path ->
            val relative = sourceRoot.relativize(path).toString().replace('\\', '/')
            val packageRelative = "source/$relative"
            val model = readModel(path)
            val ontologyIri = model.listResourcesWithProperty(model.createProperty(RDF_TYPE), model.createResource(OWL_ONTOLOGY))
                .asSequence()
                .mapNotNull { it.uri }
                .sorted()
                .firstOrNull()
            if (ontologyIri != null) ontologyMappings[ontologyIri] = packageRelative
            records += model.catalogRecords(packageRelative, ontologyIri)
        }
        dependencyFiles(packageRoot).forEach { path ->
            val relative = packageRoot.relativize(path).toString().replace('\\', '/')
            val model = readModel(path)
            model.listResourcesWithProperty(model.createProperty(RDF_TYPE), model.createResource(OWL_ONTOLOGY))
                .asSequence()
                .mapNotNull { it.uri }
                .sorted()
                .forEach { ontologyMappings[it] = relative }
        }
        verifyCuratedSeedMaturity(packageRoot, ontologyMappings)
        val importClosure = computeImportClosure(packageRoot, ontologyMappings)
        val packageFingerprint = packageFingerprint(packageRoot)

        val sortedRecords = records.distinctBy { it.iri to it.kind }.sortedWith(
            compareBy<CatalogRecord> { it.iri }.thenBy { it.kind },
        )
        val outputIndex = outputRoot.resolve("indexes")
        Files.createDirectories(outputIndex)
        Files.writeString(
            outputIndex.resolve("catalog-v1.jsonl"),
            sortedRecords.joinToString(separator = "\n", postfix = "\n", transform = CatalogRecord::toJson),
        )
        Files.writeString(
            outputIndex.resolve("catalog-metadata-v1.json"),
            "{\"schema\":\"${Phase5PackageIdentity.CATALOG_SCHEMA}\",\"release\":\"${Phase5PackageIdentity.RELEASE}\",\"generationTool\":\"entio-fibo-catalog-generator-v1\",\"packageFingerprint\":\"$packageFingerprint\",\"elementCount\":${sortedRecords.size},\"sourceFileCount\":${sourceFiles.size}}\n",
        )
        Files.writeString(
            outputIndex.resolve("ontology-iri-map-v1.json"),
            ontologyMappings.toSortedMap().entries.joinToString(separator = ",\n", prefix = "{\n", postfix = "\n}\n") {
                "  \"${jsonEscape(it.key)}\": \"${jsonEscape(it.value)}\""
            },
        )
        Files.writeString(
            outputIndex.resolve("curated-foundations-v1.json"),
            buildString {
                append("{\n  \"seeds\": [\n")
                append(Phase5PackageIdentity.CURATED_SEEDS.joinToString(",\n") { "    \"${jsonEscape(it.value)}\"" })
                append("\n  ],\n  \"importClosure\": [\n")
                append(importClosure.joinToString(",\n") { "    \"${jsonEscape(it)}\"" })
                append("\n  ]\n}\n")
            },
        )
        writeManifest(packageRoot, outputRoot, sourceFiles, ontologyMappings, sortedRecords, importClosure, packageFingerprint)
        writeChecksumLedger(packageRoot, outputRoot)
    }

    private fun writeManifest(
        packageRoot: Path,
        outputRoot: Path,
        sourceFiles: List<Path>,
        mappings: Map<String, String>,
        records: List<CatalogRecord>,
        importClosure: List<String>,
        packageFingerprint: String,
    ): Unit {
        val assets = packageAssetPaths(packageRoot)
        val checksums = assets.associate { path ->
            packageRoot.relativize(path).toString().replace('\\', '/') to sha256(path)
        }.toSortedMap()
        val manifest = buildString {
            appendLine("sourceId: ${Phase5PackageIdentity.SOURCE_ID}")
            appendLine("release: ${Phase5PackageIdentity.RELEASE}")
            appendLine("commitSha: ${Phase5PackageIdentity.COMMIT_SHA}")
            appendLine("packageSchema: ${Phase5PackageIdentity.PACKAGE_SCHEMA}")
            appendLine("catalogSchema: ${Phase5PackageIdentity.CATALOG_SCHEMA}")
            appendLine("checksumAlgorithm: ${Phase5PackageIdentity.CHECKSUM_ALGORITHM}")
            appendLine("commonsVersion: ${Phase5PackageIdentity.COMMONS_VERSION}")
            appendLine("packageFingerprint: $packageFingerprint")
            appendLine("sourceArchive: edmcouncil/fibo@${Phase5PackageIdentity.COMMIT_SHA}")
            appendLine("sourceArchivePath: source/fibo-master_2026Q2-f59157f.zip")
            appendLine("sourceArchiveSha256: ${checksums["source/fibo-master_2026Q2-f59157f.zip"]}")
            appendLine("sourceFileCount: ${sourceFiles.size}")
            appendLine("catalogElementCount: ${records.size}")
            appendLine("curatedSeedOntologyIris:")
            Phase5PackageIdentity.CURATED_SEEDS.forEach { appendLine("  - ${it.value}") }
            appendLine("importClosureOntologyIris:")
            importClosure.forEach { appendLine("  - $it") }
            appendLine("ontologyIriMappings:")
            mappings.toSortedMap().forEach { (iri, path) -> appendLine("  - iri: $iri\n    path: $path") }
            appendLine("assetChecksums:")
            checksums.forEach { (path, checksum) -> appendLine("  - path: $path\n    sha256: $checksum") }
            appendLine("commonsDependencies:")
            commonsProvenance(packageRoot, checksums).forEach { dependency ->
                appendLine("  - path: ${dependency.path}")
                appendLine("    ontologyIri: ${dependency.ontologyIri}")
                appendLine("    versionIri: ${dependency.versionIri}")
                appendLine("    officialSourceUrl: ${dependency.officialSourceUrl}")
                appendLine("    sha256: ${dependency.sha256}")
                appendLine("    copyright: Object Management Group")
                appendLine("    license: MIT")
            }
            appendLine("licenses:")
            appendLine("  fibo: MIT")
            appendLine("  omgCommons: MIT")
            appendLine("attribution: ATTRIBUTION.md")
        }
        Files.createDirectories(outputRoot)
        Files.writeString(outputRoot.resolve("manifest.yaml"), manifest)
    }

    private fun dependencyFiles(packageRoot: Path): List<Path> = Files.walk(packageRoot.resolve("dependencies")).use { stream ->
        stream.filter(Files::isRegularFile)
            .filter { it.fileName.toString().endsWith(".rdf") || it.fileName.toString().endsWith(".ttl") }
            .sorted()
            .toList()
    }

    private fun packageAssetPaths(packageRoot: Path): List<Path> {
        val roots = listOf(
            packageRoot.resolve("source"),
            packageRoot.resolve("dependencies"),
        )
        val assets = roots.flatMap { root ->
            Files.walk(root).use { stream -> stream.filter(Files::isRegularFile).sorted().toList() }
        }
        return (assets + listOf(
            packageRoot.resolve("LICENSE-FIBO-MIT.txt"),
            packageRoot.resolve("LICENSE-OMG-COMMONS-MIT.txt"),
            packageRoot.resolve("ATTRIBUTION.md"),
        )).filter(Files::exists).sorted()
    }

    private fun packageFingerprint(packageRoot: Path): String = sha256Text(
        packageAssetPaths(packageRoot)
            .map { path ->
                packageRoot.relativize(path).toString().replace('\\', '/') to sha256(path)
            }
            .toMap()
            .toSortedMap()
            .entries
            .joinToString("\n") { "${it.key}=${it.value}" },
    )

    private fun verifyCuratedSeedMaturity(packageRoot: Path, mappings: Map<String, String>): Unit {
        Phase5PackageIdentity.CURATED_SEEDS.forEach { seed ->
            val relative = mappings[seed.value] ?: error("Curated seed is not in the local IRI map: ${seed.value}")
            val model = readModel(packageRoot.resolve(relative))
            val ontology = model.listResourcesWithProperty(model.createProperty(RDF_TYPE), model.createResource(OWL_ONTOLOGY))
                .asSequence()
                .firstOrNull { it.uri == seed.value }
                ?: error("Curated seed ontology is missing: ${seed.value}")
            require(ontology.resourceValues(FIBO_MATURITY).contains(FIBO_RELEASE)) {
                "Curated seed is not Release maturity: ${seed.value}"
            }
        }
    }

    private fun commonsProvenance(packageRoot: Path, checksums: Map<String, String>): List<CommonsProvenance> =
        dependencyFiles(packageRoot).map { path ->
            val model = readModel(path)
            val ontology = model.listResourcesWithProperty(model.createProperty(RDF_TYPE), model.createResource(OWL_ONTOLOGY))
                .asSequence()
                .mapNotNull { it.uri }
                .sorted()
                .firstOrNull()
                ?: error("Commons file has no ontology IRI: $path")
            val relative = packageRoot.relativize(path).toString().replace('\\', '/')
            CommonsProvenance(
                path = relative,
                ontologyIri = ontology,
                versionIri = model.getResource(ontology).resourceValues(OWL_VERSION_IRI).firstOrNull().orEmpty(),
                officialSourceUrl = "https://www.omg.org/spec/Commons/${path.fileName.toString().removeSuffix(".rdf")}/",
                sha256 = checksums[relative] ?: error("Commons checksum is missing: $relative"),
            )
        }

    private fun computeImportClosure(packageRoot: Path, mappings: Map<String, String>): List<String> {
        val pending = ArrayDeque(Phase5PackageIdentity.CURATED_SEEDS.map { it.value })
        val visited = linkedSetOf<String>()
        while (pending.isNotEmpty()) {
            val ontologyIri = pending.removeFirst()
            if (!visited.add(ontologyIri)) continue
            val relative = mappings[ontologyIri] ?: error("No local package mapping for import: $ontologyIri")
            val model = readModel(packageRoot.resolve(relative))
            model.listObjectsOfProperty(model.createProperty(OWL_IMPORTS)).asSequence()
                .filter { it.isURIResource }
                .map { it.asResource().uri }
                .sorted()
                .forEach(pending::addLast)
        }
        return visited.sorted()
    }

    private fun writeChecksumLedger(packageRoot: Path, outputRoot: Path): Unit {
        val paths = packageAssetPaths(packageRoot) + listOf(
            outputRoot.resolve("manifest.yaml"),
            outputRoot.resolve("indexes/catalog-v1.jsonl"),
            outputRoot.resolve("indexes/catalog-metadata-v1.json"),
            outputRoot.resolve("indexes/ontology-iri-map-v1.json"),
            outputRoot.resolve("indexes/curated-foundations-v1.json"),
        )
        val lines = paths.filter(Files::exists).map { path ->
            val relative = if (path.startsWith(outputRoot)) {
                outputRoot.relativize(path).toString().replace('\\', '/')
            } else {
                packageRoot.relativize(path).toString().replace('\\', '/')
            }
            "${sha256(path)}  $relative"
        }.sorted()
        val checksumRoot = outputRoot.resolve("checksums")
        Files.createDirectories(checksumRoot)
        Files.writeString(checksumRoot.resolve("sha256sums.txt"), lines.joinToString("\n", postfix = "\n"))
    }

    private fun readModel(path: Path): Model = ModelFactory.createDefaultModel().also { model ->
        RDFDataMgr.read(model, path.toUri().toString(), if (path.fileName.toString().endsWith(".ttl")) Lang.TURTLE else Lang.RDFXML)
    }

    private fun Model.catalogRecords(sourcePath: String, ontologyIri: String?): List<CatalogRecord> {
        val typeProperty = createProperty(RDF_TYPE)
        val candidates = listOf(
            OWL_CLASS to "Class",
            OWL_OBJECT_PROPERTY to "ObjectProperty",
            OWL_DATATYPE_PROPERTY to "DatatypeProperty",
        )
        val sourceRelative = sourcePath.removePrefix("source/")
        val domain = sourceRelative.substringBefore('/').ifBlank { "FIBO" }
        val module = sourceRelative.substringAfter('/').substringBefore('/').ifBlank { domain }
        return candidates.flatMap { (type, kind) ->
            listResourcesWithProperty(typeProperty, createResource(type)).asSequence()
                .filter { it.isURIResource }
                .map { resource ->
                    CatalogRecord(
                        iri = resource.uri,
                        kind = kind,
                        preferredLabel = resource.literalValues(RDFS_LABEL).firstOrNull(),
                        definitions = resource.literalValues(SKOS_DEFINITION),
                        alternateLabels = resource.literalValues(SKOS_ALT_LABEL),
                        parents = resource.resourceValues(RDFS_SUBCLASS),
                        domains = resource.resourceValues(RDFS_DOMAIN),
                        ranges = resource.resourceValues(RDFS_RANGE),
                        sourcePath = sourcePath,
                        ontologyIri = ontologyIri.orEmpty(),
                        domain = domain,
                        module = module,
                        maturity = resource.resourceValues(FIBO_MATURITY).firstOrNull()?.let(::maturityFor)
                            ?: ExternalOntologyMaturity.Release.name,
                        curated = ontologyIri?.let { ontology ->
                            ontology in Phase5PackageIdentity.CURATED_SEEDS.map { it.value }
                        } ?: false,
                    )
                }.toList()
        }
    }

    private fun Resource.literalValues(predicate: String): List<String> =
        listProperties(ModelFactory.createDefaultModel().createProperty(predicate)).asSequence()
            .map { it.`object` }
            .filter(RDFNode::isLiteral)
            .map { it.asLiteral().lexicalForm }
            .sorted()
            .toList()

    private fun Resource.resourceValues(predicate: String): List<String> =
        listProperties(ModelFactory.createDefaultModel().createProperty(predicate)).asSequence()
            .map { it.`object` }
            .filter { it.isResource && it.asResource().isURIResource }
            .map { it.asResource().uri }
            .sorted()
            .toList()

    private fun maturityFor(value: String): String = when (value) {
        FIBO_RELEASE -> ExternalOntologyMaturity.Release.name
        FIBO_PROVISIONAL -> ExternalOntologyMaturity.Provisional.name
        FIBO_INFORMATIVE -> ExternalOntologyMaturity.Informative.name
        else -> ExternalOntologyMaturity.Unknown.name
    }

    private fun sha256(path: Path): String = sha256Bytes(Files.readAllBytes(path))

    private fun sha256Text(value: String): String = sha256Bytes(value.toByteArray(Charsets.UTF_8))

    private fun sha256Bytes(value: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(value)
        .joinToString("") { "%02x".format(Locale.ROOT, it) }

    private fun jsonEscape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")

    private data class CatalogRecord(
        val iri: String,
        val kind: String,
        val preferredLabel: String?,
        val definitions: List<String>,
        val alternateLabels: List<String>,
        val parents: List<String>,
        val domains: List<String>,
        val ranges: List<String>,
        val sourcePath: String,
        val ontologyIri: String,
        val domain: String,
        val module: String,
        val maturity: String,
        val curated: Boolean,
    ) {
        fun toJson(): String = buildString {
            append("{\"iri\":\"${jsonEscape(iri)}\",\"kind\":\"$kind\"")
            append(",\"preferredLabel\":${preferredLabel?.let { "\"${jsonEscape(it)}\"" } ?: "null"}")
            append(",\"definitions\":[${definitions.joinToString(",") { "\"${jsonEscape(it)}\"" }}]")
            append(",\"alternateLabels\":[${alternateLabels.joinToString(",") { "\"${jsonEscape(it)}\"" }}]")
            append(",\"parents\":[${parents.joinToString(",") { "\"${jsonEscape(it)}\"" }}]")
            append(",\"domains\":[${domains.joinToString(",") { "\"${jsonEscape(it)}\"" }}]")
            append(",\"ranges\":[${ranges.joinToString(",") { "\"${jsonEscape(it)}\"" }}]")
            append(",\"sourcePath\":\"${jsonEscape(sourcePath)}\"")
            append(",\"ontologyIri\":\"${jsonEscape(ontologyIri)}\"")
            append(",\"domain\":\"${jsonEscape(domain)}\",\"module\":\"${jsonEscape(module)}\"")
            append(",\"maturity\":\"$maturity\",\"curated\":$curated}")
        }
    }

    private data class CommonsProvenance(
        val path: String,
        val ontologyIri: String,
        val versionIri: String,
        val officialSourceUrl: String,
        val sha256: String,
    )

}
