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
import org.apache.lucene.util.Version
import org.slf4j.LoggerFactory.getLogger
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.PrintStream
import java.nio.charset.StandardCharsets
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
        private val logger = getLogger(Indexer::class.java)

        private val OPERATOR_CASCADE_REGEX =
            Regex("(?i)\\b(AND(?:\\s+NOT)?|OR(?:\\s+NOT)?|NOT)\\b(?:\\s+(?:AND|OR|NOT)\\b)*")

        private val LEADING_REGEX = Regex("(?i)^(?:AND|OR|NOT)\\b\\s*")

        private val TRAILING_REGEX = Regex("(?i)\\s*\\b(?:AND|OR|NOT)$")

        private const val EMBEDDING_BATCH_SIZE = 128

        fun checkIndex(path: Path): Pair<Boolean, String> {
            return runCatching {
                FSDirectory.open(path).use { dir ->
                    val charset = StandardCharsets.UTF_8

                    val outStream = ByteArrayOutputStream()
                    PrintStream(outStream, true, charset).use { stream ->
                        CheckIndex(dir).use {
                            it.setInfoStream(stream)
                            val status = it.checkIndex()

                            status.clean to outStream.toString(charset)
                        }
                    }
                }
            }.getOrElse {
                logger.error("Exception: Failed to check index.", it)

                false to (it.message ?: "")
            }
        }
    }

    private val directory: BaseDirectory = FSDirectory.open(path)

    private val writer: IndexWriter

    private var reader: DirectoryReader

    private val whitespaceAnalyzer = WhitespaceAnalyzer()

    private val mappedAnalyzer = mutableMapOf<String, Analyzer>()

    private val idFields = mutableSetOf<String>()

    private val embeddingFields = mutableMapOf<String, Embedding.Model>()

    init {
        checkForVersionCompatibility()

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
            ramBufferSizeMB = 1024.0
            setUseCompoundFile(false)
        }

        this.writer = IndexWriter(this.directory, config)

        this.reader = DirectoryReader.open(this.writer)

        logger.info("Initialized Indexer")

        val analyzer =
            this.mappedAnalyzer.entries.joinToString(", ") { (key, value) -> "$key (${value::class.simpleName})" }
        logger.info("Indexer fields: $analyzer")

        val embeddings = embeddingFields.entries.joinToString(", ") { (key, value) -> "$key (${value.name})" }
        if (embeddingFields.isNotEmpty()) logger.info("Indexer embedding fields: $embeddings")
    }

    fun indexEntries(name: String, entries: List<Map<String, String>>) {
        if (this.mappedAnalyzer.isEmpty() || entries.isEmpty()) return

        val idField = idFields.find { it.startsWith(name) } ?: return
        val idSuffix = idField.substringAfter("$name.")

        val fieldEmbeddings =
            Array(entries.size) { HashMap<String, FloatArray>(embeddingFields.size) }

        if (embeddingFields.isNotEmpty()) {
            for ((field, model) in embeddingFields) {
                if (!field.startsWith("$name.")) continue

                val key = field.substringAfter("$name.")

                var start = 0
                val n = entries.size
                val chunk = ArrayList<String>(EMBEDDING_BATCH_SIZE)
                while (start < n) {
                    val end = minOf(start + EMBEDDING_BATCH_SIZE, n)

                    chunk.clear()

                    for (i in start until end)
                        chunk.add(entries[i][key] ?: "")

                    val embeddings = EmbeddingProvider.createEmbeddings(model, chunk)

                    for (i in 0 until (end - start)) {
                        val docIdx = start + i

                        fieldEmbeddings[docIdx][field] = embeddings[i]
                    }

                    start = end
                }
            }
        }

        for (i in entries.indices) {
            val entry = entries[i]

            val fieldValue = entry[idSuffix] ?: continue

            writer.updateDocument(
                Term(idField, fieldValue),
                createDoc(name, entry, fieldEmbeddings[i])
            )
        }
    }

    fun finish() {
        runCatching { this.writer.commit() }
            .onFailure { logger.error("Exception: Failed to commit changes.", it) }
    }

    fun searchFieldIndex(field: String, query: String): List<Document> {
        val normalized = normalizeOperator(query)

        if (normalized.isBlank()) return emptyList()

        refreshReader()

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

        if (query.isBlank()) return emptyList()

        val searcher = IndexSearcher(this.reader)

        val embedding =
            EmbeddingProvider.createEmbeddings(model, listOf(query), true).firstOrNull() ?: return emptyList()

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

    private fun refreshReader() =
        DirectoryReader.openIfChanged(reader)?.let {
            reader.close()
            reader = it
        }

    private fun createDoc(
        name: String,
        map: Map<String, String>,
        precomputed: Map<String, FloatArray> = emptyMap(),
    ): Document = Document().apply {
        map.forEach { (key, value) ->
            val id = "$name.$key"

            if (idFields.contains(id) || idFields.contains(key)) {
                add(LongField(if (key.endsWith("_ag_id")) key else id, value.toLong(), Field.Store.YES))
            }

            if (mappedAnalyzer.contains(id)) {
                add(TextField(id, value, Field.Store.YES))
                add(TextField("$id.ws", value, Field.Store.YES))
            }

            precomputed[id]?.let { vec ->
                add(KnnFloatVectorField("$id.vec", vec, VectorSimilarityFunction.COSINE))
            }
        }
    }

    private fun checkForVersionCompatibility() {
        if (!DirectoryReader.indexExists(directory)) return

        val currentMajor = Version.LATEST.major

        val segInfo = runCatching { SegmentInfos.readLatestCommit(directory) }.getOrElse {
            logger.error("Exception: Failed to read SegmentInfos.", it)
            return
        }

        val indexMajorVersion = segInfo.indexCreatedVersionMajor

        if (currentMajor == indexMajorVersion) return

        if (currentMajor < indexMajorVersion) {
            logger.warn("Indexer version ($indexMajorVersion) is newer than current version ($currentMajor).")
            return
        }

        if (currentMajor - 1 > indexMajorVersion) {
            logger.error("Index is at least two major versions behind current version.")
            return
        }

        logger.warn("Index version ($indexMajorVersion) is older than current version ($currentMajor) and will be upgraded.")

        runCatching { IndexUpgrader(directory).upgrade() }
            .onSuccess { logger.info("Indexer upgraded successfully.") }
            .onFailure { logger.error("Failed to upgrade Indexer from $indexMajorVersion to $currentMajor.", it) }
    }
}
