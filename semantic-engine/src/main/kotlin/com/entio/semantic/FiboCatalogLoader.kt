package com.entio.semantic

import com.entio.core.EntioProject
import com.entio.core.EntioResult
import com.entio.core.ExternalCatalogElement
import com.entio.core.ExternalElementCatalogStatus
import com.entio.core.ExternalElementLocality
import com.entio.core.ExternalEntityKind
import com.entio.core.ExternalOntologyCatalog
import com.entio.core.ExternalOntologyManifest
import com.entio.core.ExternalOntologyMaturity
import com.entio.core.ExternalOntologyModule
import com.entio.core.ExternalOntologySource
import com.entio.core.ExternalSemanticDescriptor
import com.entio.core.Iri
import com.entio.core.LocalityStatus
import com.entio.core.LocalizedText
import com.entio.core.OntologyEntityDescriptor
import com.entio.core.ValidationIssue
import com.entio.core.ValidationSeverity
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings

/** Loads the committed FIBO compact indexes once and exposes read-only browse operations. */
internal data class LoadedFiboIndex(
    val source: ExternalOntologySource,
    val manifest: ExternalOntologyManifest,
    val catalog: ExternalOntologyCatalog,
    val elements: List<ExternalCatalogElement>,
    val modules: List<ExternalOntologyModule>,
    val curatedModules: List<ExternalOntologyModule>,
    val iriMap: Map<Iri, String>,
    val packageRoot: Path,
)

public class FiboCatalogLoader(
    private val packageRoot: Path,
) {
    private var cachedIndex: LoadedFiboIndex? = null

    /** Opens a session over the immutable package without parsing the RDF release. */
    public fun load(project: EntioProject? = null): EntioResult<ExternalFiboCatalogSession> = try {
        EntioResult.Success(ExternalFiboCatalogSession(index(), project))
    } catch (exception: RuntimeException) {
        EntioResult.Failure(
            message = "The committed FIBO catalog could not be loaded.",
            issues = listOf(
                ValidationIssue(
                    severity = ValidationSeverity.Error,
                    code = "external-fibo-catalog-invalid",
                    message = exception.message ?: "The committed FIBO catalog is invalid.",
                    source = "fibo",
                ),
            ),
            cause = exception,
        )
    }

    private fun index(): LoadedFiboIndex {
        cachedIndex?.let { return it }
        val parsed = parseIndex()
        cachedIndex = parsed
        return parsed
    }

    private fun parseIndex(): LoadedFiboIndex {
        require(Files.isDirectory(packageRoot)) { "FIBO package directory does not exist: $packageRoot" }
        val manifest = parseManifest()
        val catalogPath = packageRoot.resolve("indexes/catalog-v1.jsonl")
        val metadataPath = packageRoot.resolve("indexes/catalog-metadata-v1.json")
        val mapPath = packageRoot.resolve("indexes/ontology-iri-map-v1.json")
        require(Files.exists(catalogPath)) { "FIBO catalog index is missing." }
        require(Files.exists(metadataPath)) { "FIBO catalog metadata is missing." }
        require(Files.exists(mapPath)) { "FIBO ontology IRI map is missing." }

        val records = Files.readAllLines(catalogPath)
            .filter(String::isNotBlank)
            .map(::parseCatalogRecord)
            .filterNot { it.domain.equals("EXMP", ignoreCase = true) }
        val metadata = parseMap(Files.readString(metadataPath))
        require(metadata.string("schema") == manifest.catalogSchema) { "Catalog schema does not match the manifest." }
        require(metadata.string("release") == manifest.release) { "Catalog release does not match the manifest." }
        require(metadata.int("elementCount") >= records.size) { "Catalog metadata count is smaller than loaded records." }

        val iriMap = parseMap(Files.readString(mapPath)).entries.associate { (key, value) ->
            Iri(key) to value.toString()
        }
        val elements = records.map { it.toElement(records) }.sortedWith(elementComparator)
        val modules = elements
            .groupBy { Iri(it.descriptor.moduleIri.value) }
            .map { (moduleIri, moduleElements) -> moduleElements.toModule(moduleIri, iriMap) }
            .sortedWith(moduleComparator)
        val curatedModules = manifest.curatedSeedOntologyIris.mapNotNull { seed -> modules.firstOrNull { it.ontologyIri == seed } }
        val catalog = ExternalOntologyCatalog(
            sourceId = manifest.sourceId,
            release = manifest.release,
            catalogSchema = manifest.catalogSchema,
            modules = modules,
            elementCount = elements.size,
        )
        return LoadedFiboIndex(
            source = ExternalOntologySource(
                id = manifest.sourceId,
                displayName = "Financial Industry Business Ontology",
                version = manifest.release,
                description = "Pinned read-only FIBO catalog.",
                availability = com.entio.core.ExternalOntologyAvailability.Available,
                curatedPackageId = "fibo-foundations-v1",
                catalogId = manifest.catalogSchema,
                attribution = packageRoot.resolve("ATTRIBUTION.md").toString(),
            ),
            manifest = manifest,
            catalog = catalog,
            elements = elements,
            modules = modules,
            curatedModules = curatedModules,
            iriMap = iriMap,
            packageRoot = packageRoot,
        )
    }

    private fun parseManifest(): ExternalOntologyManifest {
        val path = packageRoot.resolve("manifest.yaml")
        require(Files.exists(path)) { "FIBO manifest is missing." }
        val map = parseMap(Files.readString(path))
        val assetChecksums = map.listOfMaps("assetChecksums").associate { it.string("path") to it.string("sha256") }
        return ExternalOntologyManifest(
            sourceId = map.string("sourceId"),
            release = map.string("release"),
            commitSha = map.string("commitSha"),
            packageSchema = map.string("packageSchema"),
            catalogSchema = map.string("catalogSchema"),
            checksumAlgorithm = map.string("checksumAlgorithm"),
            commonsVersion = map.string("commonsVersion"),
            packageFingerprint = com.entio.core.ExternalPackageFingerprint(map.string("packageFingerprint")),
            curatedSeedOntologyIris = map.stringList("curatedSeedOntologyIris").map(::Iri),
            importClosureOntologyIris = map.stringList("importClosureOntologyIris").map(::Iri),
            ontologyIriMappings = map.listOfMaps("ontologyIriMappings").associate { Iri(it.string("iri")) to it.string("path") },
            assetChecksums = assetChecksums,
            assetLicenses = map.listOfMaps("commonsDependencies").associate { it.string("path") to it.string("license") },
            attributionComplete = Files.exists(packageRoot.resolve("ATTRIBUTION.md")),
        )
    }

    private fun parseCatalogRecord(line: String): CatalogRecord {
        val map = parseMap(line)
        return CatalogRecord(
            iri = Iri(map.string("iri")),
            kind = when (map.string("kind")) {
                "Class" -> ExternalEntityKind.Class
                "ObjectProperty" -> ExternalEntityKind.ObjectProperty
                "DatatypeProperty" -> ExternalEntityKind.DatatypeProperty
                else -> error("Unsupported external catalog kind.")
            },
            preferredLabel = map.optionalString("preferredLabel"),
            alternateLabels = map.stringList("alternateLabels"),
            definitions = map.stringList("definitions"),
            parents = map.stringList("parents").map(::Iri),
            domains = map.stringList("domains").map(::Iri),
            ranges = map.stringList("ranges").map(::Iri),
            sourcePath = map.string("sourcePath"),
            ontologyIri = Iri(map.string("ontologyIri")),
            domain = map.string("domain"),
            module = map.string("module").removeSuffix(".rdf").removeSuffix(".ttl"),
            maturity = ExternalOntologyMaturity.valueOf(map.string("maturity")),
            curated = map.boolean("curated"),
        )
    }

    private fun parseMap(value: String): Map<String, Any?> {
        val parsed = Load(LoadSettings.builder().build()).loadFromString(value)
        @Suppress("UNCHECKED_CAST")
        return parsed as? Map<String, Any?> ?: error("Expected a mapping.")
    }

    private data class CatalogRecord(
        val iri: Iri,
        val kind: ExternalEntityKind,
        val preferredLabel: String?,
        val alternateLabels: List<String>,
        val definitions: List<String>,
        val parents: List<Iri>,
        val domains: List<Iri>,
        val ranges: List<Iri>,
        val sourcePath: String,
        val ontologyIri: Iri,
        val domain: String,
        val module: String,
        val maturity: ExternalOntologyMaturity,
        val curated: Boolean,
    ) {
        fun toElement(allRecords: List<CatalogRecord>): ExternalCatalogElement {
            val kind = when (kind) {
                ExternalEntityKind.Class -> com.entio.core.SemanticDescriptorKind.Class
                ExternalEntityKind.ObjectProperty -> com.entio.core.SemanticDescriptorKind.ObjectProperty
                ExternalEntityKind.DatatypeProperty -> com.entio.core.SemanticDescriptorKind.DatatypeProperty
            }
            val common = com.entio.core.SemanticDescriptorCommon(
                entity = iri,
                kind = kind,
                sourceId = "fibo",
                sourceOntologyId = ontologyIri.value,
                locality = LocalityStatus.Imported,
                preferredLabel = preferredLabel?.let(::LocalizedText),
                alternateLabels = alternateLabels.map(::LocalizedText),
                definitions = definitions.map(::LocalizedText),
            )
            val descriptor = when (this.kind) {
                ExternalEntityKind.Class -> OntologyEntityDescriptor.Class(
                    common = common,
                    directSuperclasses = parents,
                    directSubclasses = allRecords.filter { iri in it.parents }.map { it.iri }.sortedBy { it.value },
                )
                ExternalEntityKind.ObjectProperty -> OntologyEntityDescriptor.ObjectProperty(
                    common = common,
                    domains = domains,
                    ranges = ranges,
                )
                ExternalEntityKind.DatatypeProperty -> OntologyEntityDescriptor.DatatypeProperty(
                    common = common,
                    domains = domains,
                    datatypeRanges = ranges,
                )
            }
            return ExternalCatalogElement(
                descriptor = ExternalSemanticDescriptor(
                    descriptor = descriptor,
                    sourceId = "fibo",
                    release = "master_2026Q2",
                    moduleIri = ontologyIri,
                    domain = domain,
                    maturity = maturity,
                    locality = ExternalElementLocality.External,
                    catalogStatus = if (curated) ExternalElementCatalogStatus.Curated else ExternalElementCatalogStatus.Available,
                ),
                kind = this.kind,
            )
        }
    }

    private fun List<ExternalCatalogElement>.toModule(
        ontologyIri: Iri,
        iriMap: Map<Iri, String>,
    ): ExternalOntologyModule {
        val first = first()
        return ExternalOntologyModule(
            ontologyIri = ontologyIri,
            label = moduleLabel(ontologyIri),
            domain = first.descriptor.domain,
            sourcePath = iriMap[ontologyIri].orEmpty(),
            maturity = first.descriptor.maturity,
            curated = first.descriptor.catalogStatus == ExternalElementCatalogStatus.Curated,
        )
    }

    private fun moduleLabel(ontologyIri: Iri): String = ontologyIri.value
        .trimEnd('/')
        .substringAfterLast('/')
        .replace(Regex("([a-z])([A-Z])"), "$1 $2")
        .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1 $2")
        .replace('_', ' ')
        .replace('-', ' ')

    private fun Map<*, *>.string(key: String): String = get(key)?.toString()?.takeIf(String::isNotBlank)
        ?: error("Missing catalog field: $key")

    private fun Map<*, *>.optionalString(key: String): String? = get(key)?.toString()?.takeIf { it != "null" && it.isNotBlank() }

    private fun Map<*, *>.boolean(key: String): Boolean = get(key) as? Boolean ?: error("Missing boolean catalog field: $key")

    private fun Map<*, *>.int(key: String): Int = (get(key) as? Number)?.toInt() ?: error("Missing numeric catalog field: $key")

    private fun Map<*, *>.stringList(key: String): List<String> = (get(key) as? List<*>)
        ?.map { it.toString() }
        ?: error("Missing list catalog field: $key")

    private fun Map<*, *>.listOfMaps(key: String): List<Map<*, *>> = (get(key) as? List<*>)
        ?.map { it as? Map<*, *> ?: error("Invalid catalog mapping in $key") }
        ?: emptyList()

    private companion object {
        private val elementComparator = compareBy<ExternalCatalogElement> {
            it.descriptor.descriptor.common.preferredLabel?.lexicalForm?.lowercase(Locale.ROOT).orEmpty()
        }.thenBy { it.kind.name }.thenBy { it.descriptor.descriptor.common.entity.value }
        private val moduleComparator = compareBy<ExternalOntologyModule> { it.label.lowercase(Locale.ROOT) }
            .thenBy { it.ontologyIri.value }
    }
}

/** A compact, read-only FIBO session suitable for a workbench or CLI request lifecycle. */
public class ExternalFiboCatalogSession internal constructor(
    private val index: LoadedFiboIndex,
    private val project: EntioProject?,
) {
    private val loaded: LoadedFiboIndex get() = index
    private val importsCache = mutableMapOf<Iri, List<Iri>>()

    public val source: ExternalOntologySource get() = loaded.source
    public val manifest: ExternalOntologyManifest get() = loaded.manifest
    public val catalog: ExternalOntologyCatalog get() = loaded.catalog

    public fun browseCuratedModules(page: Int = 0, pageSize: Int = 25): ExternalCatalogPage<ExternalOntologyModule> =
        page(loaded.curatedModules, page, pageSize)

    public fun browseModules(page: Int = 0, pageSize: Int = 25): ExternalCatalogPage<ExternalOntologyModule> =
        page(loaded.modules, page, pageSize)

    public fun browseModule(moduleIri: Iri, page: Int = 0, pageSize: Int = 25): ExternalCatalogPage<ExternalCatalogElement> =
        page(loaded.elements.filter { it.descriptor.moduleIri == moduleIri }.map { it.withProjectStatus() }, page, pageSize)

    public fun find(iri: Iri, kind: ExternalEntityKind? = null): ExternalCatalogElement? =
        loaded.elements.firstOrNull { element ->
            element.descriptor.descriptor.common.entity == iri && (kind == null || element.kind == kind)
        }?.withProjectStatus()

    /** Returns the loaded compact index in deterministic order for read-only services. */
    public fun allElements(): List<ExternalCatalogElement> = loaded.elements.map { it.withProjectStatus() }

    /**
     * Returns read-only descriptors from the approved FIBO modules imported by [project].
     * The descriptors are a view over the pinned catalog and do not alter the project graph.
     */
    public fun importedDescriptors(project: EntioProject): List<OntologyEntityDescriptor> {
        val roots = project.graph.triples
            .filter { it.predicate.value == "http://www.w3.org/2002/07/owl#imports" }
            .mapNotNull { it.objectTerm as? Iri }
            .toSet()
        val importedModules = moduleImportClosure(roots)
        val importedElements = loaded.elements
            .filter { it.descriptor.moduleIri in importedModules }
        val importedIris = importedElements
            .mapNotNull { it.descriptor.descriptor.common.entity as? Iri }
            .toSet()
        return importedElements
            .map { it.descriptor.descriptor.restrictTo(importedIris) }
            .sortedWith(compareBy({ it.common.entity.value }, { it.common.sourceId }, { it.common.kind.name }))
    }

    private fun OntologyEntityDescriptor.restrictTo(availableIris: Set<Iri>): OntologyEntityDescriptor = when (this) {
        is OntologyEntityDescriptor.Class -> copy(
            directSubclasses = directSubclasses.filter(availableIris::contains),
        )
        else -> this
    }

    /** Returns the supplied modules and all mapped transitive imports in deterministic order. */
    public fun moduleImportClosure(roots: Collection<Iri>): Set<Iri> {
        val pending = ArrayDeque(roots.sortedBy(Iri::value))
        val visited = linkedSetOf<Iri>()
        while (pending.isNotEmpty()) {
            val moduleIri = pending.removeFirst()
            if (!visited.add(moduleIri)) continue
            importsFor(moduleIri)
                .sortedBy(Iri::value)
                .forEach(pending::addLast)
        }
        return visited
    }

    /** Returns the direct owl:imports declared by a mapped FIBO module. */
    public fun importsFor(moduleIri: Iri): List<Iri> = importsCache.getOrPut(moduleIri) {
        val relativePath = loaded.iriMap[moduleIri] ?: return@getOrPut emptyList()
        val source = Files.readString(loaded.packageRoot.resolve(relativePath))
        val rdfXmlImports = Regex("<owl:imports\\s+rdf:resource=\\\"([^\\\"]+)\\\"")
            .findAll(source)
            .map { Iri(it.groupValues[1]) }
        val turtleImports = Regex("owl:imports\\s+<([^>]+)>")
            .findAll(source)
            .map { Iri(it.groupValues[1]) }
        (rdfXmlImports + turtleImports).distinct().sortedBy(Iri::value).toList()
    }

    private fun <T> page(items: List<T>, page: Int, pageSize: Int): ExternalCatalogPage<T> {
        require(page >= 0) { "Page must not be negative." }
        require(pageSize in 1..100) { "Page size must be between 1 and 100." }
        val start = page * pageSize
        val values = if (start >= items.size) emptyList() else items.drop(start).take(pageSize)
        return ExternalCatalogPage(values, items.size, page, pageSize)
    }

    private fun ExternalCatalogElement.withProjectStatus(): ExternalCatalogElement {
        val externalIri = descriptor.descriptor.common.entity
        val alreadyUsed = project?.graph?.triples?.any { triple ->
            triple.subjectResource == externalIri || triple.objectTerm == externalIri
        } == true
        if (!alreadyUsed) return this
        return copy(descriptor = descriptor.copy(catalogStatus = ExternalElementCatalogStatus.AlreadyUsed))
    }
}

public data class ExternalCatalogPage<T>(
    public val items: List<T>,
    public val totalCount: Int,
    public val page: Int,
    public val pageSize: Int,
) {
    public val hasNext: Boolean get() = (page + 1) * pageSize < totalCount
}
