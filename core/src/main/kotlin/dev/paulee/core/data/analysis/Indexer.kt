package dev.paulee.core.data.analysis

import dev.paulee.api.data.IndexField
import dev.paulee.api.data.Language
import dev.paulee.api.data.Source
import dev.paulee.api.data.UniqueField
import dev.paulee.core.normalizeDataSource
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.en.EnglishAnalyzer
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.LongField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser
import org.apache.lucene.queryparser.flexible.standard.config.StandardQueryConfigHandler
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.BaseDirectory
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.store.NIOFSDirectory
import org.slf4j.LoggerFactory.getLogger
import java.io.Closeable
import java.nio.file.Path

class Indexer(path: Path, sources: List<Source>) : Closeable {

    private val logger = getLogger(Indexer::class.java)

    private val directory: BaseDirectory

    private val writer: IndexWriter

    private var reader: DirectoryReader

    private val mappedAnalyzer = mutableMapOf<String, Analyzer>()

    private val idFields = mutableSetOf<String>()

    private val operator = mapOf("(?i)\\bor\\b" to "OR", "(?i)\\band\\b" to "AND", "(?i)\\bnot\\b" to "NOT")

    init {
        //See https://lucene.apache.org/core/10_0_0/core/org/apache/lucene/store/NIOFSDirectory.html
        this.directory = if (this.isWindows()) FSDirectory.open(path) else NIOFSDirectory.open(path)

        sources.forEach {
            val normalized = normalizeDataSource(it.name)

            if (it.fields.none { field -> field is IndexField }) return@forEach

            it.fields.forEach { field ->
                val fieldName = field.name

                when (field) {
                    is IndexField -> this.mappedAnalyzer["$normalized.$fieldName"] = LangAnalyzer.new(field.lang)
                    is UniqueField -> if (field.identify) this.idFields.add("$normalized.$fieldName")
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
    }

    fun indexEntries(name: String, entries: List<Map<String, String>>) {
        if (this.mappedAnalyzer.isEmpty() || entries.isEmpty()) return

        var hasChanges = false

        entries.forEach {
            val fieldName = idFields.find { it.startsWith(name) } ?: return@forEach
            val fieldValue = it[fieldName.substringAfter("$name.")] ?: return@forEach

            this.writer.updateDocument(Term(fieldName, fieldValue), this.createDoc(name, it))

            hasChanges = true
        }

        if (hasChanges) runCatching { this.writer.commit() }.getOrElse { e ->
            this.logger.error(
                "Exception: Failed to commit changes.",
                e
            )
        }
    }

    fun searchFieldIndex(field: String, query: String): List<Document> {
        val normalized = this.normalizeOperator(query)

        if (normalized.isEmpty()) return emptyList()

        val queryParser = StandardQueryParser(this.mappedAnalyzer[field] ?: EnglishAnalyzer())

        queryParser.defaultOperator = StandardQueryConfigHandler.Operator.AND
        queryParser.allowLeadingWildcard = true

        DirectoryReader.openIfChanged(this.reader)?.let {
            this.reader.close()

            this.reader = it
        }

        val searcher = IndexSearcher(this.reader)

        val topDocs = searcher.search(queryParser.parse(normalized, field), Int.MAX_VALUE)

        val storedFields = searcher.storedFields()

        return topDocs.scoreDocs.map { storedFields.document(it.doc) }
    }

    override fun close() {
        this.writer.close()
        this.reader.close()
    }

    private fun normalizeOperator(query: String) =
        operator.entries.fold(query) { acc, entry -> acc.replace(entry.key.toRegex(), entry.value) }

    private fun createDoc(name: String, map: Map<String, String>): Document = Document().apply {
        map.forEach { (key, value) ->
            val id = "$name.$key"

            if (idFields.contains(id) || idFields.contains(key)) {
                add(LongField(if (key.endsWith("_ag_id")) key else id, value.toLong(), Field.Store.YES))
            }

            if (mappedAnalyzer.contains(id)) add(TextField(id, value, Field.Store.YES))
        }
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("win")
}

