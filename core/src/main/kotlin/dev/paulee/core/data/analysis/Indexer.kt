package dev.paulee.core.data.analysis

import dev.paulee.api.data.DataInfo
import dev.paulee.api.data.IndexField
import dev.paulee.api.data.Language
import dev.paulee.api.data.UniqueField
import dev.paulee.api.internal.Embedding
import dev.paulee.core.data.provider.EmbeddingProvider
import dev.paulee.core.normalizeDataSource
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper
import org.apache.lucene.document.*
import org.apache.lucene.index.*
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.FloatVectorSimilarityQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.Query
import org.apache.lucene.store.BaseDirectory
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.store.NIOFSDirectory
import org.slf4j.LoggerFactory.getLogger
import java.io.Closeable
import java.nio.file.Path

internal class CustomParser(private val defaultField: String, defaultAnalyzer: Analyzer) :
    QueryParser(defaultField, defaultAnalyzer) {

    override fun newFieldQuery(
        analyzer: Analyzer,
        field: String?,
        queryText: String?,
        quoted: Boolean,
    ): Query? {
        val target = if (quoted) "${field ?: defaultField}.ws" else (field ?: defaultField)
        return super.newFieldQuery(analyzer, target, queryText, quoted)
    }

    override fun getWildcardQuery(field: String?, termStr: String?): Query? {
        return super.getWildcardQuery("${field ?: defaultField}.ws", termStr)
    }
}

internal class Indexer(path: Path, dataInfo: DataInfo) : Closeable {

    companion object {
        private val OPERATOR_CASCADE_REGEX =
            Regex("(?i)\\b(AND(?:\\s+NOT)?|OR(?:\\s+NOT)?|NOT)\\b(?:\\s+(?:AND|OR|NOT)\\b)*")

        private val LEADING_REGEX = Regex("(?i)^(?:AND|OR|NOT)\\b\\s*")

        private val TRAILING_REGEX = Regex("(?i)\\s*\\b(?:AND|OR|NOT)$")
    }

    private val logger = getLogger(Indexer::class.java)

    private val directory: BaseDirectory

    private val writer: IndexWriter

    private var reader: DirectoryReader

    private val whitespaceAnalyzer = WhitespaceAnalyzer()

    private val mappedAnalyzer = mutableMapOf<String, Analyzer>()

    private val idFields = mutableSetOf<String>()

    private val embeddingFields = mutableMapOf<String, Embedding.Model>()

    init {
        //See https://lucene.apache.org/core/10_0_0/core/org/apache/lucene/store/NIOFSDirectory.html
        this.directory = if (this.isWindows()) FSDirectory.open(path) else NIOFSDirectory.open(path)

        dataInfo.sources.forEach {
            val normalized = normalizeDataSource(it.name)

            if (it.fields.none { field -> field is IndexField }) return@forEach

            it.fields.forEach { field ->
                val fieldName = "${normalized}.${field.name}"

                when (field) {
                    is IndexField -> {
                        this.mappedAnalyzer[fieldName] = LangAnalyzer.new(field.lang)
                        this.mappedAnalyzer["$fieldName.ws"] = whitespaceAnalyzer

                        field.embeddingModel?.let { model ->
                            EmbeddingProvider.registerTokenizer(model)
                            embeddingFields[fieldName] = model
                        }
                    }

                    is UniqueField -> if (field.identify) this.idFields.add(fieldName)
                    else -> {}
                }
            }

            this.idFields.add("${normalized}_ag_id")
        }

        val fieldAnalyzer = PerFieldAnalyzerWrapper(LangAnalyzer.new(Language.ENGLISH), this.mappedAnalyzer)

        val config = IndexWriterConfig(fieldAnalyzer).apply {
            openMode = IndexWriterConfig.OpenMode.CREATE_OR_APPEND
        }

        this.writer = IndexWriter(this.directory, config)

        this.reader = DirectoryReader.open(this.writer)

        this.logger.info("Initialized Indexer (${if (this.isWindows()) "FS" else "NIOFS"})")

        val analyzer =
            this.mappedAnalyzer.entries.joinToString(", ") { (key, value) -> "$key (${value::class.simpleName})" }
        this.logger.info("Indexer fields: $analyzer")

        val embeddings = embeddingFields.entries.joinToString(", ") { (key, value) -> "$key (${value.name})" }
        if (embeddingFields.isNotEmpty()) this.logger.info("Indexer embedding fields: $embeddings")
    }

    fun indexEntries(name: String, entries: List<Map<String, String>>) {
        if (this.mappedAnalyzer.isEmpty() || entries.isEmpty()) return

        val fieldName = idFields.find { it.startsWith(name) } ?: return

        entries.forEach { entry ->
            val fieldValue = entry[fieldName.substringAfter("$name.")] ?: return@forEach

            this.writer.updateDocument(Term(fieldName, fieldValue), this.createDoc(name, entry))
        }

        runCatching { this.writer.commit() }.onFailure {
            this.logger.error("Exception: Failed to commit changes.", it)
        }
    }

    fun searchFieldIndex(field: String, query: String): List<Document> {
        val normalized = normalizeOperator(query)

        if (normalized.isBlank()) return emptyList()

        DirectoryReader.openIfChanged(this.reader)?.let {
            this.reader.close()

            this.reader = it
        }

        val searcher = IndexSearcher(this.reader)

        val perField =
            PerFieldAnalyzerWrapper(
                mappedAnalyzer[field] ?: LangAnalyzer.new(Language.ENGLISH),
                mappedAnalyzer
            )

        val parser = CustomParser(field, perField).apply {
            defaultOperator = QueryParser.Operator.AND
            allowLeadingWildcard = true
        }

        val query = parser.parse(normalized)

        val hits = searcher.search(query, Int.MAX_VALUE)
        return hits.scoreDocs.map { searcher.storedFields().document(it.doc) }
    }

    fun searchMatchingVec(field: String, query: String, similarity: Float): List<Document> {
        val model = embeddingFields[field] ?: return emptyList()

        DirectoryReader.openIfChanged(this.reader)?.let {
            this.reader.close()

            this.reader = it
        }

        val searcher = IndexSearcher(this.reader)

        val embedding =
            EmbeddingProvider.createEmbeddings(model, true, listOf(query)).firstOrNull() ?: return emptyList()

        val query = FloatVectorSimilarityQuery("$field.vec", embedding, similarity)

        val hits = searcher.search(query, Int.MAX_VALUE)
        return hits.scoreDocs.map { searcher.storedFields().document(it.doc) }
    }

    override fun close() {
        this.writer.close()
        this.reader.close()
        this.directory.close()
    }

    private fun normalizeOperator(query: String): String {
        return OPERATOR_CASCADE_REGEX.replace(query) { it.groupValues[1].uppercase() }
            .replace(LEADING_REGEX, "")
            .replace(TRAILING_REGEX, "")
    }

    private fun createDoc(name: String, map: Map<String, String>): Document = Document().apply {
        map.forEach { (key, value) ->
            val id = "$name.$key"

            if (idFields.contains(id) || idFields.contains(key)) {
                add(LongField(if (key.endsWith("_ag_id")) key else id, value.toLong(), Field.Store.YES))
            }

            if (mappedAnalyzer.contains(id)) {
                add(TextField(id, value, Field.Store.YES))
                add(TextField("$id.ws", value, Field.Store.YES))
            }

            embeddingFields[id]?.let { model ->
                val embedding = EmbeddingProvider.createEmbeddings(model, false, listOf(value)).first()

                add(KnnFloatVectorField("$id.vec", embedding, VectorSimilarityFunction.COSINE))
            }
        }
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("win")
}

