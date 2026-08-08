package com.entio.semantic

import com.entio.semantic.DomainSearchAssetSupport.IRI_FILE
import com.entio.semantic.DomainSearchAssetSupport.LEXICAL_FILE
import com.entio.semantic.DomainSearchAssetSupport.SEARCH_MANIFEST
import com.entio.semantic.DomainSearchAssetSupport.VECTOR_FILE
import com.entio.semantic.DomainSearchAssetSupport.int
import com.entio.semantic.DomainSearchAssetSupport.string
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.sqrt
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.BoostQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TermQuery
import org.apache.lucene.search.similarities.BM25Similarity
import org.apache.lucene.store.ByteBuffersDirectory

public data class DomainSearchHit(public val iri: String, public val score: Float)

/** Read-only local BM25 and optional exact-cosine index. */
public class DomainSearchIndex private constructor(
    private val directory: ByteBuffersDirectory,
    private val reader: DirectoryReader,
    private val searcher: IndexSearcher,
    private val iris: List<String>,
    private val vectors: FloatArray?,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    public val vectorAvailable: Boolean get() = vectors != null

    public fun searchLexical(query: String, limit: Int = 100): List<DomainSearchHit> {
        checkOpen()
        require(limit in 1..100)
        if (query.isBlank()) return emptyList()
        val tokens = StandardAnalyzer().use { analyzer ->
            analyzer.tokenStream("text", query).use { stream ->
                val term = stream.addAttribute(CharTermAttribute::class.java)
                val values = mutableListOf<String>()
                stream.reset()
                while (stream.incrementToken()) values += term.toString()
                stream.end()
                values.distinct()
            }
        }
        if (tokens.isEmpty()) return emptyList()
        val queryValue = BooleanQuery.Builder().apply {
            tokens.forEach { token ->
                add(BoostQuery(TermQuery(Term("preferredLabel", token)), 4.0f), BooleanClause.Occur.SHOULD)
                add(BoostQuery(TermQuery(Term("labelAcronym", token)), 4.0f), BooleanClause.Occur.SHOULD)
                add(BoostQuery(TermQuery(Term("alternateLabels", token)), 3.0f), BooleanClause.Occur.SHOULD)
                add(TermQuery(Term("definitions", token)), BooleanClause.Occur.SHOULD)
                add(BoostQuery(TermQuery(Term("text", token)), 0.5f), BooleanClause.Occur.SHOULD)
            }
        }.build()
        return searcher.search(queryValue, limit).scoreDocs.map { score ->
            DomainSearchHit(searcher.storedFields().document(score.doc).get("iri"), score.score)
        }.sortedWith(compareByDescending<DomainSearchHit> { it.score }.thenBy { it.iri })
    }

    public fun searchVector(query: FloatArray, limit: Int = 100): List<DomainSearchHit> {
        checkOpen()
        require(limit in 1..100)
        require(query.size == LocalSentenceEmbeddingService.DIMENSION) { "Wrong query vector dimension." }
        val stored = requireNotNull(vectors) { "Vector index is unavailable in lexical-only mode." }
        validateNormalized(query, "query")
        return iris.indices.map { recordIndex ->
            var score = 0.0f
            val offset = recordIndex * LocalSentenceEmbeddingService.DIMENSION
            for (dimension in query.indices) score += query[dimension] * stored[offset + dimension]
            DomainSearchHit(iris[recordIndex], score)
        }.sortedWith(compareByDescending<DomainSearchHit> { it.score }.thenBy { it.iri }).take(limit)
    }

    override fun close(): Unit {
        if (closed.compareAndSet(false, true)) {
            reader.close()
            directory.close()
        }
    }

    private fun checkOpen(): Unit = check(!closed.get()) { "The domain search index is closed." }

    public companion object {
        public fun openFull(root: Path): DomainSearchIndex = open(root, requireVectors = true)
        public fun openLexical(root: Path): DomainSearchIndex = open(root, requireVectors = false)

        private fun open(root: Path, requireVectors: Boolean): DomainSearchIndex {
            val manifest = DomainSearchAssetSupport.mapping(root.resolve(SEARCH_MANIFEST))
            require(manifest.string("schema") == DomainSearchAssetSupport.INDEX_SCHEMA)
            require(manifest.string("lexicalContract") == DomainSearchAssetSupport.LEXICAL_CONTRACT)
            require(manifest.int("entityCount") == DomainCorpusIdentity.EXPECTED_ENTITY_COUNT)
            val documents = DomainSearchAssetSupport.readDocuments(root.resolve(LEXICAL_FILE))
            require(documents.size == manifest.int("entityCount"))
            require(DomainSearchAssetSupport.sha256(root.resolve(LEXICAL_FILE)) == manifest.string("lexicalSha256"))
            val iris = documents.map(DomainSearchDocument::iri)
            require(iris == iris.sorted() && iris.distinct().size == iris.size)
            val vectors = if (requireVectors) loadVectors(root, manifest, iris) else null

            val analyzer = StandardAnalyzer()
            val directory = ByteBuffersDirectory()
            try {
                IndexWriter(directory, IndexWriterConfig(analyzer).setSimilarity(BM25Similarity())).use { writer ->
                    documents.forEach { source ->
                        val document = Document()
                        document.add(StringField("iri", source.iri, Field.Store.YES))
                        document.add(TextField("preferredLabel", source.preferredLabel, Field.Store.NO))
                        document.add(TextField("labelAcronym", acronym(source.preferredLabel), Field.Store.NO))
                        document.add(TextField("alternateLabels", source.alternateLabels.joinToString(" "), Field.Store.NO))
                        document.add(TextField("definitions", source.definitions.joinToString(" "), Field.Store.NO))
                        document.add(TextField("text", source.descriptorText, Field.Store.NO))
                        writer.addDocument(document)
                    }
                }
                analyzer.close()
                val reader = DirectoryReader.open(directory)
                val searcher = IndexSearcher(reader).also { it.similarity = BM25Similarity() }
                return DomainSearchIndex(directory, reader, searcher, iris, vectors)
            } catch (exception: Exception) {
                analyzer.close()
                directory.close()
                throw exception
            }
        }

        private fun loadVectors(root: Path, manifest: Map<*, *>, iris: List<String>): FloatArray {
            require(manifest.string("modelId") == LocalSentenceEmbeddingService.MODEL_ID) { "Wrong embedding model." }
            require(manifest.string("modelRevision") == LocalSentenceEmbeddingService.MODEL_REVISION) {
                "Wrong embedding model revision."
            }
            require(manifest.int("dimension") == LocalSentenceEmbeddingService.DIMENSION) { "Wrong vector dimension." }
            val storedIris = Files.readAllLines(root.resolve(IRI_FILE)).filter(String::isNotBlank)
            require(storedIris == iris) { "Lexical and vector identities do not match." }
            require(DomainSearchAssetSupport.sha256(root.resolve(IRI_FILE)) == manifest.string("iriSha256"))
            require(DomainSearchAssetSupport.sha256(root.resolve(VECTOR_FILE)) == manifest.string("vectorSha256"))
            val bytes = Files.readAllBytes(root.resolve(VECTOR_FILE))
            val expectedBytes = iris.size * LocalSentenceEmbeddingService.DIMENSION * Float.SIZE_BYTES
            require(bytes.size == expectedBytes) { "Wrong vector byte count." }
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val vectors = FloatArray(iris.size * LocalSentenceEmbeddingService.DIMENSION) { buffer.float }
            iris.indices.forEach { index ->
                validateNormalized(
                    vectors.copyOfRange(
                        index * LocalSentenceEmbeddingService.DIMENSION,
                        (index + 1) * LocalSentenceEmbeddingService.DIMENSION,
                    ),
                    iris[index],
                )
            }
            return vectors
        }

        private fun validateNormalized(values: FloatArray, identity: String): Unit {
            var squaredNorm = 0.0
            values.forEach { value ->
                require(value.isFinite()) { "Non-finite vector: $identity" }
                squaredNorm += value.toDouble() * value.toDouble()
            }
            require(squaredNorm > 0.0 && abs(sqrt(squaredNorm) - 1.0) <= 1e-5) {
                "Vector is not normalized: $identity"
            }
        }

        private fun acronym(label: String): String = label.split(Regex("[^A-Za-z0-9]+"))
            .filter(String::isNotBlank)
            .takeIf { it.size > 1 }
            ?.joinToString("") { it.first().lowercaseChar().toString() }
            .orEmpty()
    }
}
