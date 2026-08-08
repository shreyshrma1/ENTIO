package com.entio.semantic

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
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings

public enum class DomainSourceFamily {
    FIBO,
    OMG_COMMONS,
}

public object DomainCorpusIdentity {
    public const val SCHEMA: String = "entio-domain-descriptor-package-v1"
    public const val RECORD_SCHEMA: String = "entio-domain-semantic-record-v1"
    public const val FOUNDATION_SCHEMA: String = "entio-domain-foundation-profile-v1"
    public const val UNSUPPORTED_SCHEMA: String = "entio-domain-unsupported-construct-v1"
    public const val DESCRIPTOR_CONTRACT: String = "domain-semantic-descriptor-v1"
    public const val GRAPH_CONTEXT_CONTRACT: String = "domain-graph-context-v1"
    public const val FOUNDATION_PROFILE: String = "entio-fibo-foundation-master-2026q2-v1"
    public const val EXPECTED_ENTITY_COUNT: Int = 4_579
    public const val EXPECTED_FIBO_COUNT: Int = 4_232
    public const val EXPECTED_COMMONS_COUNT: Int = 347
    public const val EXPECTED_CLASS_COUNT: Int = 3_169
    public const val EXPECTED_OBJECT_PROPERTY_COUNT: Int = 1_114
    public const val EXPECTED_DATATYPE_PROPERTY_COUNT: Int = 296
    public const val OUTPUT_RELATIVE_PATH: String = "external-ontologies/domain-search/fibo/master_2026Q2"
}

/** Generates deterministic Phase 13 text assets from the verified local Phase 5 package. */
public object DomainCorpusGenerator {
    private const val RDF_TYPE: String = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
    private const val RDFS_SUBCLASS: String = "http://www.w3.org/2000/01/rdf-schema#subClassOf"
    private const val RDFS_SUBPROPERTY: String = "http://www.w3.org/2000/01/rdf-schema#subPropertyOf"
    private const val RDFS_DOMAIN: String = "http://www.w3.org/2000/01/rdf-schema#domain"
    private const val RDFS_RANGE: String = "http://www.w3.org/2000/01/rdf-schema#range"
    private const val OWL_CLASS: String = "http://www.w3.org/2002/07/owl#Class"
    private const val OWL_OBJECT_PROPERTY: String = "http://www.w3.org/2002/07/owl#ObjectProperty"
    private const val OWL_DATATYPE_PROPERTY: String = "http://www.w3.org/2002/07/owl#DatatypeProperty"
    private const val OWL_ONTOLOGY: String = "http://www.w3.org/2002/07/owl#Ontology"
    private const val OWL_DEPRECATED: String = "http://www.w3.org/2002/07/owl#deprecated"
    private const val FIBO_MATURITY: String =
        "https://spec.edmcouncil.org/fibo/ontology/FND/Utilities/AnnotationVocabulary/hasMaturityLevel"
    private const val FIBO_RELEASE: String =
        "https://spec.edmcouncil.org/fibo/ontology/FND/Utilities/AnnotationVocabulary/Release"
    private const val FIBO_PROVISIONAL: String =
        "https://spec.edmcouncil.org/fibo/ontology/FND/Utilities/AnnotationVocabulary/Provisional"
    private const val FIBO_INFORMATIVE: String =
        "https://spec.edmcouncil.org/fibo/ontology/FND/Utilities/AnnotationVocabulary/Informative"
    private const val MAX_DESCRIPTOR_TEXT_BYTES: Int = 65_536
    private const val MAX_GRAPH_CONTEXT_IRIS: Int = 128

    private val unsupportedPredicates: Set<String> = setOf(
        "http://www.w3.org/2002/07/owl#complementOf",
        "http://www.w3.org/2002/07/owl#disjointWith",
        "http://www.w3.org/2002/07/owl#equivalentClass",
        "http://www.w3.org/2002/07/owl#equivalentProperty",
        "http://www.w3.org/2002/07/owl#inverseOf",
        "http://www.w3.org/2002/07/owl#propertyChainAxiom",
        "http://www.w3.org/2002/07/owl#propertyDisjointWith",
        "http://www.w3.org/2002/07/owl#unionOf",
    )
    private val structuralPredicates: Set<String> = setOf(RDFS_SUBCLASS, RDFS_SUBPROPERTY, RDFS_DOMAIN, RDFS_RANGE)
    private val yamlLoader: Load = Load(LoadSettings.builder().setLabel("phase-5-catalog-record").build())

    @JvmStatic
    public fun main(args: Array<String>): Unit {
        require(args.size == 2) { "Expected the Phase 5 package root and Phase 13 output root." }
        generate(Path.of(args[0]), Path.of(args[1]))
    }

    public fun generate(packageRoot: Path, outputRoot: Path): Unit {
        configureLocalRdfXmlParsing()
        val manifest = Files.readString(packageRoot.resolve("manifest.yaml"))
        val packageFingerprint = manifestLine(manifest, "packageFingerprint")
        val records = Files.readAllLines(packageRoot.resolve("indexes/catalog-v1.jsonl"))
            .filter(String::isNotBlank)
            .map(::parseCatalogRecord)
            .sortedWith(compareBy<SourceRecord> { it.iri }.thenBy { it.kind })
        require(records.size == DomainCorpusIdentity.EXPECTED_ENTITY_COUNT) {
            "Expected ${DomainCorpusIdentity.EXPECTED_ENTITY_COUNT} eligible records, found ${records.size}."
        }
        require(records.map { it.iri }.distinct().size == records.size) { "Duplicate eligible canonical IRI." }

        val models = records.map(SourceRecord::sourcePath).distinct().associateWith { relative ->
            val path = packageRoot.resolve(relative)
            require(Files.isRegularFile(path)) { "Descriptor source is missing: $relative" }
            readModel(path)
        }
        val semanticRecords = records.map { record -> record.toSemanticRecord(models.getValue(record.sourcePath)) }
        verifyCounts(semanticRecords)

        Files.createDirectories(outputRoot)
        val descriptorsPath = outputRoot.resolve("descriptors-v1.jsonl")
        Files.writeString(
            descriptorsPath,
            semanticRecords.joinToString(separator = "\n", postfix = "\n", transform = SemanticRecord::toJson),
        )
        val unsupported = semanticRecords.flatMap { record ->
            record.unsupportedConstructs.map { construct -> UnsupportedRecord(record.iri, record.sourcePath, construct) }
        }.sortedWith(compareBy<UnsupportedRecord> { it.iri }.thenBy { it.construct })
        Files.writeString(
            outputRoot.resolve("unsupported-constructs-v1.jsonl"),
            unsupported.joinToString(separator = "\n", postfix = if (unsupported.isEmpty()) "" else "\n") { it.toJson() },
        )
        Files.writeString(outputRoot.resolve("foundation-profile-v1.json"), foundationProfile(semanticRecords))
        Files.writeString(outputRoot.resolve("ATTRIBUTION.md"), attribution())
        writeManifest(outputRoot, packageFingerprint, semanticRecords, unsupported)
        writeChecksums(outputRoot)
    }

    private fun SourceRecord.toSemanticRecord(model: Model): SemanticRecord {
        val resource = model.getResource(iri)
        val type = when (kind) {
            "Class" -> OWL_CLASS
            "ObjectProperty" -> OWL_OBJECT_PROPERTY
            "DatatypeProperty" -> OWL_DATATYPE_PROPERTY
            else -> error("Unsupported eligible kind: $kind")
        }
        require(model.contains(resource, model.createProperty(RDF_TYPE), model.createResource(type))) {
            "Eligible record is not traceable to its typed source statement: $iri in $sourcePath"
        }
        val sourceFamily = sourceFamily(sourcePath)
        val maturity = maturity(resource, model, ontologyIri, sourceFamily)
        require((parents + domains + ranges).distinct().size <= MAX_GRAPH_CONTEXT_IRIS) {
            "Named graph context exceeds the approved bound: $iri"
        }
        val textValues = listOfNotNull(preferredLabel) + alternateLabels + definitions
        val descriptorText = textValues.filter(String::isNotBlank).distinct().joinToString(". ")
        require(descriptorText.toByteArray(Charsets.UTF_8).size <= MAX_DESCRIPTOR_TEXT_BYTES) {
            "Descriptor text exceeds the approved bound: $iri"
        }
        val unsupported = resource.listProperties().asSequence().mapNotNull { statement ->
            when {
                statement.predicate.uri in unsupportedPredicates -> statement.predicate.uri
                statement.predicate.uri in structuralPredicates && statement.`object`.isAnon ->
                    "${statement.predicate.uri}|anonymous-expression"
                else -> null
            }
        }.distinct().sorted().toList()
        val dependencyFingerprint = sha256Text(
            buildList {
                parents.forEach { add("parent=$it") }
                domains.forEach { add("domain=$it") }
                ranges.forEach { add("range=$it") }
            }.sorted().joinToString("\n"),
        )
        val base = SemanticRecord(
            iri = iri,
            kind = kind,
            sourceFamily = sourceFamily,
            sourcePath = sourcePath,
            ontologyIri = ontologyIri,
            maturity = maturity,
            preferredLabel = preferredLabel,
            alternateLabels = alternateLabels.distinct().sorted(),
            definitions = definitions.distinct().sorted(),
            parents = parents.distinct().sorted(),
            domains = domains.distinct().sorted(),
            ranges = ranges.distinct().sorted(),
            descriptorText = descriptorText,
            dependencyFingerprint = dependencyFingerprint,
            unsupportedConstructs = unsupported,
            recordFingerprint = "",
        )
        return base.copy(recordFingerprint = sha256Text(base.canonicalWithoutFingerprint()))
    }

    private fun maturity(
        resource: Resource,
        model: Model,
        ontologyIri: String,
        sourceFamily: DomainSourceFamily,
    ): String {
        val deprecated = resource.listProperties(model.createProperty(OWL_DEPRECATED)).asSequence()
            .map { it.`object` }
            .filter(RDFNode::isLiteral)
            .any { it.asLiteral().boolean }
        if (deprecated) return "Deprecated"
        if (sourceFamily == DomainSourceFamily.OMG_COMMONS) return "Release"
        val ontology = model.getResource(ontologyIri)
        return ontology.listProperties(model.createProperty(FIBO_MATURITY)).asSequence()
            .map { it.`object` }
            .filter { it.isURIResource }
            .map { it.asResource().uri }
            .map { value ->
                when (value) {
                    FIBO_RELEASE -> "Release"
                    FIBO_PROVISIONAL -> "Provisional"
                    FIBO_INFORMATIVE -> "Informative"
                    else -> "Unknown"
                }
            }.firstOrNull() ?: "Release"
    }

    private fun sourceFamily(path: String): DomainSourceFamily = when {
        path.startsWith("source/") -> DomainSourceFamily.FIBO
        path.startsWith("dependencies/omg-commons-1.3/") -> DomainSourceFamily.OMG_COMMONS
        else -> error("Eligible source path has no approved source family: $path")
    }

    private fun verifyCounts(records: List<SemanticRecord>): Unit {
        require(records.count { it.sourceFamily == DomainSourceFamily.FIBO } == DomainCorpusIdentity.EXPECTED_FIBO_COUNT)
        require(records.count { it.sourceFamily == DomainSourceFamily.OMG_COMMONS } == DomainCorpusIdentity.EXPECTED_COMMONS_COUNT)
        require(records.count { it.kind == "Class" } == DomainCorpusIdentity.EXPECTED_CLASS_COUNT)
        require(records.count { it.kind == "ObjectProperty" } == DomainCorpusIdentity.EXPECTED_OBJECT_PROPERTY_COUNT)
        require(records.count { it.kind == "DatatypeProperty" } == DomainCorpusIdentity.EXPECTED_DATATYPE_PROPERTY_COUNT)
    }

    private fun writeManifest(
        outputRoot: Path,
        packageFingerprint: String,
        records: List<SemanticRecord>,
        unsupported: List<UnsupportedRecord>,
    ): Unit {
        val maturityCounts = records.groupingBy(SemanticRecord::maturity).eachCount().toSortedMap()
        val text = buildString {
            appendLine("schema: ${DomainCorpusIdentity.SCHEMA}")
            appendLine("sourceId: fibo")
            appendLine("release: master_2026Q2")
            appendLine("packageFingerprint: $packageFingerprint")
            appendLine("recordSchema: ${DomainCorpusIdentity.RECORD_SCHEMA}")
            appendLine("descriptorContract: ${DomainCorpusIdentity.DESCRIPTOR_CONTRACT}")
            appendLine("graphContextContract: ${DomainCorpusIdentity.GRAPH_CONTEXT_CONTRACT}")
            appendLine("foundationSchema: ${DomainCorpusIdentity.FOUNDATION_SCHEMA}")
            appendLine("foundationProfile: ${DomainCorpusIdentity.FOUNDATION_PROFILE}")
            appendLine("generationTool: entio-domain-corpus-generator-v1")
            appendLine("entityCount: ${records.size}")
            appendLine("fiboCount: ${records.count { it.sourceFamily == DomainSourceFamily.FIBO }}")
            appendLine("omgCommonsCount: ${records.count { it.sourceFamily == DomainSourceFamily.OMG_COMMONS }}")
            appendLine("classCount: ${records.count { it.kind == "Class" }}")
            appendLine("objectPropertyCount: ${records.count { it.kind == "ObjectProperty" }}")
            appendLine("datatypePropertyCount: ${records.count { it.kind == "DatatypeProperty" }}")
            maturityCounts.forEach { (maturity, count) -> appendLine("maturity${maturity}Count: $count") }
            appendLine("unsupportedConstructCount: ${unsupported.size}")
            appendLine("orderedRecordFingerprint: ${sha256Text(records.joinToString("\n") { it.recordFingerprint })}")
            appendLine("descriptorsSha256: ${sha256(outputRoot.resolve("descriptors-v1.jsonl"))}")
            appendLine("foundationSha256: ${sha256(outputRoot.resolve("foundation-profile-v1.json"))}")
            appendLine("unsupportedSha256: ${sha256(outputRoot.resolve("unsupported-constructs-v1.jsonl"))}")
            appendLine("attribution: ATTRIBUTION.md")
        }
        Files.writeString(outputRoot.resolve("manifest.yaml"), text)
    }

    private fun writeChecksums(outputRoot: Path): Unit {
        val relativePaths = listOf(
            "ATTRIBUTION.md",
            "descriptors-v1.jsonl",
            "foundation-profile-v1.json",
            "manifest.yaml",
            "unsupported-constructs-v1.jsonl",
        )
        val checksumRoot = outputRoot.resolve("checksums")
        Files.createDirectories(checksumRoot)
        Files.writeString(
            checksumRoot.resolve("sha256sums.txt"),
            relativePaths.joinToString("\n", postfix = "\n") { relative ->
                "${sha256(outputRoot.resolve(relative))}  $relative"
            },
        )
    }

    private fun foundationProfile(records: List<SemanticRecord>): String {
        val byIri = records.associateBy(SemanticRecord::iri)
        val groups = foundationGroups()
        groups.flatMap(FoundationGroup::members).forEach { iri -> require(iri in byIri) { "Unknown foundation member: $iri" } }
        return buildString {
            append("{\n  \"schema\":\"${DomainCorpusIdentity.FOUNDATION_SCHEMA}\",\n")
            append("  \"profileId\":\"${DomainCorpusIdentity.FOUNDATION_PROFILE}\",\n")
            append("  \"release\":\"master_2026Q2\",\n  \"reviewed\":true,\n  \"groups\":[\n")
            groups.forEachIndexed { groupIndex, group ->
                append("    {\"id\":\"${group.id}\",\"label\":\"${jsonEscape(group.label)}\",\"members\":[\n")
                group.members.forEachIndexed { memberIndex, iri ->
                    val record = byIri.getValue(iri)
                    val label = record.preferredLabel ?: iri.trimEnd('/').substringAfterLast('/')
                    append("      {\"iri\":\"${jsonEscape(iri)}\",\"kind\":\"${record.kind}\",\"label\":\"${jsonEscape(label)}\",\"sourceFamily\":\"${record.sourceFamily.name}\"}")
                    appendLine(if (memberIndex == group.members.lastIndex) "" else ",")
                }
                append("    ]}")
                appendLine(if (groupIndex == groups.lastIndex) "" else ",")
            }
            append("  ]\n}\n")
        }
    }

    private fun foundationGroups(): List<FoundationGroup> = listOf(
        FoundationGroup("agents-organizations", "Agents and organizations", listOf(
            "https://www.omg.org/spec/Commons/PartiesAndSituations/Agent",
            "https://www.omg.org/spec/Commons/PartiesAndSituations/Party",
            "https://spec.edmcouncil.org/fibo/ontology/FND/AgentsAndPeople/People/Person",
            "https://www.omg.org/spec/Commons/Organizations/Organization",
            "https://www.omg.org/spec/Commons/Organizations/LegalEntity",
            "https://www.omg.org/spec/Commons/Organizations/hasOrganizationMember",
        )),
        FoundationGroup("agreements-commitments", "Agreements and commitments", listOf(
            "https://spec.edmcouncil.org/fibo/ontology/FND/Agreements/Agreements/Agreement",
            "https://spec.edmcouncil.org/fibo/ontology/FND/Agreements/Agreements/Commitment",
            "https://spec.edmcouncil.org/fibo/ontology/FND/Agreements/Contracts/Contract",
            "https://spec.edmcouncil.org/fibo/ontology/FND/Agreements/Contracts/ContractualCommitment",
            "https://spec.edmcouncil.org/fibo/ontology/FND/Agreements/Contracts/hasContractParty",
        )),
        FoundationGroup("identifiers-classifications", "Identifiers and classifications", listOf(
            "https://www.omg.org/spec/Commons/Identifiers/Identifier",
            "https://www.omg.org/spec/Commons/Classifiers/Classifier",
            "https://www.omg.org/spec/Commons/Classifiers/ClassificationScheme",
            "https://www.omg.org/spec/Commons/Organizations/OrganizationIdentifier",
            "https://www.omg.org/spec/Commons/Identifiers/identifies",
        )),
        FoundationGroup("dates-temporal", "Dates and temporal concepts", listOf(
            "https://www.omg.org/spec/Commons/DatesAndTimes/Date",
            "https://www.omg.org/spec/Commons/DatesAndTimes/DatePeriod",
            "https://www.omg.org/spec/Commons/DatesAndTimes/DateTime",
            "https://spec.edmcouncil.org/fibo/ontology/FND/DatesAndTimes/FinancialDates/CalendarPeriod",
            "https://www.omg.org/spec/Commons/DatesAndTimes/hasStartDate",
        )),
        FoundationGroup("quantities-units-measures", "Quantities, units, and measures", listOf(
            "https://www.omg.org/spec/Commons/QuantitiesAndUnits/Measure",
            "https://www.omg.org/spec/Commons/QuantitiesAndUnits/MeasurementUnit",
            "https://www.omg.org/spec/Commons/QuantitiesAndUnits/QuantityKind",
            "https://spec.edmcouncil.org/fibo/ontology/FND/Accounting/CurrencyAmount/MonetaryAmount",
            "https://www.omg.org/spec/Commons/QuantitiesAndUnits/hasMeasurementUnit",
            "https://www.omg.org/spec/Commons/QuantitiesAndUnits/hasNumericValue",
        )),
        FoundationGroup("ownership-control", "Ownership and control", listOf(
            "https://spec.edmcouncil.org/fibo/ontology/FND/OwnershipAndControl/Ownership/Ownership",
            "https://spec.edmcouncil.org/fibo/ontology/FND/OwnershipAndControl/Ownership/Owner",
            "https://spec.edmcouncil.org/fibo/ontology/FND/OwnershipAndControl/Control/Control",
            "https://spec.edmcouncil.org/fibo/ontology/FND/OwnershipAndControl/Control/ControllingParty",
            "https://spec.edmcouncil.org/fibo/ontology/FND/OwnershipAndControl/Ownership/isOwnedBy",
        )),
        FoundationGroup("products-services", "Products and services", listOf(
            "https://spec.edmcouncil.org/fibo/ontology/FND/ProductsAndServices/ProductsAndServices/Product",
            "https://www.omg.org/spec/Commons/Organizations/Service",
            "https://spec.edmcouncil.org/fibo/ontology/FND/ProductsAndServices/ProductsAndServices/ServiceAgreement",
            "https://spec.edmcouncil.org/fibo/ontology/FND/ProductsAndServices/PaymentsAndSchedules/Payment",
            "https://www.omg.org/spec/Commons/Organizations/provides",
        )),
        FoundationGroup("places-addresses", "Places and addresses", listOf(
            "https://www.omg.org/spec/Commons/Locations/Location",
            "https://spec.edmcouncil.org/fibo/ontology/FND/Places/Addresses/Address",
            "https://spec.edmcouncil.org/fibo/ontology/FND/Places/Addresses/PhysicalAddress",
            "https://spec.edmcouncil.org/fibo/ontology/FND/Places/RealProperty/RealProperty",
            "https://spec.edmcouncil.org/fibo/ontology/FND/Places/Addresses/hasAddress",
        )),
    )

    private fun attribution(): String = """
        # Phase 13 FIBO Descriptor Attribution

        These deterministic semantic descriptor and foundation-profile assets
        were generated by Entio from the pinned, locally verified
        `master_2026Q2` FIBO package at commit
        `f59157fe156e3d91b1c045222d0a7dc06b7d78a2` and OMG Commons 1.3.

        FIBO and OMG Commons source terms are distributed under their MIT
        licenses. See `external-ontologies/fibo/ATTRIBUTION.md`,
        `LICENSE-FIBO-MIT.txt`, and `LICENSE-OMG-COMMONS-MIT.txt`. This package
        is an Entio-approved snapshot of FIBO master, not an official production
        publication. Generation is offline and does not modify the Phase 5
        source package or indexes.
    """.trimIndent() + "\n"

    private fun parseCatalogRecord(line: String): SourceRecord {
        val map = yamlLoader.loadFromString(line) as? Map<*, *> ?: error("Catalog record is not an object.")
        return SourceRecord(
            iri = map.string("iri"),
            kind = map.string("kind"),
            preferredLabel = map.optionalString("preferredLabel"),
            definitions = map.stringList("definitions"),
            alternateLabels = map.stringList("alternateLabels"),
            parents = map.stringList("parents"),
            domains = map.stringList("domains"),
            ranges = map.stringList("ranges"),
            sourcePath = map.string("sourcePath"),
            ontologyIri = map.string("ontologyIri"),
        )
    }

    private fun readModel(path: Path): Model = ModelFactory.createDefaultModel().also { model ->
        RDFDataMgr.read(model, path.toUri().toString(), if (path.fileName.toString().endsWith(".ttl")) Lang.TURTLE else Lang.RDFXML)
    }

    private fun configureLocalRdfXmlParsing(): Unit {
        System.setProperty("jdk.xml.totalEntitySizeLimit", "0")
        System.setProperty("jdk.xml.maxGeneralEntitySizeLimit", "0")
        System.setProperty("jdk.xml.maxParameterEntitySizeLimit", "0")
        System.setProperty("jdk.xml.entityExpansionLimit", "0")
    }

    private fun manifestLine(manifest: String, field: String): String = Regex("(?m)^$field: (.+)$")
        .find(manifest)?.groupValues?.get(1) ?: error("Missing Phase 5 manifest field: $field")

    private fun Map<*, *>.string(key: String): String = this[key] as? String ?: error("Missing catalog string: $key")
    private fun Map<*, *>.optionalString(key: String): String? = (this[key] as? String)?.takeIf(String::isNotBlank)
    private fun Map<*, *>.stringList(key: String): List<String> = (this[key] as? List<*>)
        ?.map { it as? String ?: error("Catalog list '$key' contains a non-string.") }
        ?: error("Missing catalog list: $key")

    private fun sha256(path: Path): String = sha256Bytes(Files.readAllBytes(path))
    private fun sha256Text(value: String): String = sha256Bytes(value.toByteArray(Charsets.UTF_8))
    private fun sha256Bytes(value: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(value)
        .joinToString("") { "%02x".format(Locale.ROOT, it) }
    private fun jsonEscape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

    private data class SourceRecord(
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
    )

    private data class SemanticRecord(
        val iri: String,
        val kind: String,
        val sourceFamily: DomainSourceFamily,
        val sourcePath: String,
        val ontologyIri: String,
        val maturity: String,
        val preferredLabel: String?,
        val alternateLabels: List<String>,
        val definitions: List<String>,
        val parents: List<String>,
        val domains: List<String>,
        val ranges: List<String>,
        val descriptorText: String,
        val dependencyFingerprint: String,
        val unsupportedConstructs: List<String>,
        val recordFingerprint: String,
    ) {
        fun canonicalWithoutFingerprint(): String = toJson(includeRecordFingerprint = false)
        fun toJson(): String = toJson(includeRecordFingerprint = true)

        private fun toJson(includeRecordFingerprint: Boolean): String = buildString {
            append("{\"schema\":\"${DomainCorpusIdentity.RECORD_SCHEMA}\",\"iri\":\"${jsonEscape(iri)}\",\"kind\":\"$kind\"")
            append(",\"sourceFamily\":\"${sourceFamily.name}\",\"sourcePath\":\"${jsonEscape(sourcePath)}\"")
            append(",\"ontologyIri\":\"${jsonEscape(ontologyIri)}\",\"maturity\":\"$maturity\"")
            append(",\"preferredLabel\":${preferredLabel?.let { "\"${jsonEscape(it)}\"" } ?: "null"}")
            append(",\"alternateLabels\":${jsonArray(alternateLabels)},\"definitions\":${jsonArray(definitions)}")
            append(",\"parents\":${jsonArray(parents)},\"domains\":${jsonArray(domains)},\"ranges\":${jsonArray(ranges)}")
            append(",\"descriptorText\":\"${jsonEscape(descriptorText)}\"")
            append(",\"dependencyFingerprint\":\"$dependencyFingerprint\"")
            append(",\"unsupportedConstructs\":${jsonArray(unsupportedConstructs)}")
            if (includeRecordFingerprint) append(",\"recordFingerprint\":\"$recordFingerprint\"")
            append("}")
        }
    }

    private data class UnsupportedRecord(val iri: String, val sourcePath: String, val construct: String) {
        fun toJson(): String =
            "{\"schema\":\"${DomainCorpusIdentity.UNSUPPORTED_SCHEMA}\",\"iri\":\"${jsonEscape(iri)}\"," +
                "\"sourcePath\":\"${jsonEscape(sourcePath)}\",\"construct\":\"${jsonEscape(construct)}\"}"
    }

    private data class FoundationGroup(val id: String, val label: String, val members: List<String>)

    private fun jsonArray(values: List<String>): String =
        values.joinToString(separator = ",", prefix = "[", postfix = "]") { "\"${jsonEscape(it)}\"" }
}
