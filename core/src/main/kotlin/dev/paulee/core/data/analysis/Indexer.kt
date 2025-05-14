package dev.paulee.core.data.analysis

import dev.paulee.api.data.IndexField
import dev.paulee.api.data.Language
import dev.paulee.api.data.Source
import dev.paulee.api.data.UniqueField
import dev.paulee.core.normalizeDataSource
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.core.WhitespaceAnalyzer
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
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TermQuery
import org.apache.lucene.store.BaseDirectory
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.store.NIOFSDirectory
import org.slf4j.LoggerFactory.getLogger
import java.io.Closeable
import java.nio.file.Path

class Indexer(path: Path, sources: List<Source>) : Closeable {

    companion object {
        private val QUOTE_REGEX = Regex("\"([^\"]+?)\"")

        private val OPERATOR_REGEX = Regex("(?i)\\b(or|and|not)\\b")

        private val OPERATOR_MAPPING = mapOf(
            "or" to "OR",
            "and" to "AND",
            "not" to "NOT"
        )
    }

    private val logger = getLogger(Indexer::class.java)

    private val directory: BaseDirectory

    private val writer: IndexWriter

    private var reader: DirectoryReader

    private val whitespaceAnalyzer = WhitespaceAnalyzer()

    private val mappedAnalyzer = mutableMapOf<String, Analyzer>()

    private val idFields = mutableSetOf<String>()

    init {
        //See https://lucene.apache.org/core/10_0_0/core/org/apache/lucene/store/NIOFSDirectory.html
        this.directory = if (this.isWindows()) FSDirectory.open(path) else NIOFSDirectory.open(path)

        sources.forEach {
            val normalized = normalizeDataSource(it.name)

            if (it.fields.none { field -> field is IndexField }) return@forEach

            it.fields.forEach { field ->
                val fieldName = field.name

                when (field) {
                    is IndexField -> {
                        this.mappedAnalyzer["$normalized.$fieldName"] = LangAnalyzer.new(field.lang)
                        this.mappedAnalyzer["$normalized.$fieldName.ws"] = whitespaceAnalyzer
                    }

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

        val fieldName = idFields.find { it.startsWith(name) } ?: return

        var hasChanges = false

        entries.forEach { entry ->
            val fieldValue = entry[fieldName.substringAfter("$name.")] ?: return@forEach

            this.writer.updateDocument(Term(fieldName, fieldValue), this.createDoc(name, entry))

            hasChanges = true
        }

        if (hasChanges) {
            runCatching { this.writer.commit() }.getOrElse { e ->
                this.logger.error("Exception: Failed to commit changes.", e)
            }
        }
    }

    fun searchFieldIndex(field: String, query: String): List<Document> {
        val exactTerms = QUOTE_REGEX.findAll(query)
            .map { it.groupValues[1] }
            .toList()

        val stripped = exactTerms.fold(query) { acc, t -> acc.replace("\"$t\"", "") }.trim()

        val normalized = normalizeOperator(stripped)

        DirectoryReader.openIfChanged(this.reader)?.let {
            this.reader.close()

            this.reader = it
        }

        val searcher = IndexSearcher(this.reader)

        val queryBuilder = BooleanQuery.Builder()

        if (normalized.isNotBlank()) {
            val parser = StandardQueryParser(mappedAnalyzer[field] ?: EnglishAnalyzer()).apply {
                defaultOperator = StandardQueryConfigHandler.Operator.AND
                allowLeadingWildcard = true
            }

            queryBuilder.add(parser.parse(normalized, field), BooleanClause.Occur.MUST)
        }

        exactTerms.forEach { rawTerm ->
            queryBuilder.add(
                TermQuery(Term("$field.ws", rawTerm)),
                BooleanClause.Occur.MUST
            )
        }

        val hits = searcher.search(queryBuilder.build(), Int.MAX_VALUE)
        return hits.scoreDocs.map { searcher.storedFields().document(it.doc) }
    }

    override fun close() {
        this.writer.close()
        this.reader.close()
    }

    private fun normalizeOperator(query: String): String {
        return OPERATOR_REGEX.replace(query) { matchResult ->
            val key = matchResult.value.lowercase()
            OPERATOR_MAPPING[key] ?: matchResult.value
        }
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
        }
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("win")
}

