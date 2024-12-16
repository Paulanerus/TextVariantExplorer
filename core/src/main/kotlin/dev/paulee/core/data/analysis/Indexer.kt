package dev.paulee.core.data.analysis

import dev.paulee.api.data.DataSource
import dev.paulee.api.data.Index
import dev.paulee.api.data.Language
import dev.paulee.api.data.Unique
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
import java.io.Closeable
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.primaryConstructor

class Indexer(path: Path, sources: Array<KClass<*>>) : Closeable {

    private val directory: BaseDirectory

    private val writer: IndexWriter

    private var reader: DirectoryReader? = null

    private val mappedAnalyzer = mutableMapOf<String, Analyzer>()

    private val idFields = mutableSetOf<String>()

    private val operator = mapOf("(?i)\\bor\\b" to "OR", "(?i)\\band\\b" to "AND", "(?i)\\bnot\\b" to "NOT")

    init {
        //See https://lucene.apache.org/core/10_0_0/core/org/apache/lucene/store/NIOFSDirectory.html
        this.directory = if (this.isWindows()) FSDirectory.open(path) else NIOFSDirectory.open(path)

        sources.forEach {
            val name = it.findAnnotation<DataSource>()?.file ?: return@forEach

            val normalized = normalizeDataSource(name)

            it.primaryConstructor?.parameters.orEmpty()
                .filter { it.hasAnnotation<Index>() || it.hasAnnotation<Unique>() }.forEach { param ->
                    val paramName = param.name ?: return@forEach

                    param.findAnnotation<Index>()?.also { index ->
                        this.mappedAnalyzer["$normalized.$paramName"] = LangAnalyzer.new(index.lang)
                    }

                    param.findAnnotation<Unique>()?.takeIf { param.type.classifier == Long::class && it.identify }
                        ?.also { unique -> this.idFields.add("$normalized.$paramName") }
                }

            this.idFields.add("${normalized}_ag_id")
        }

        val fieldAnalyzer = PerFieldAnalyzerWrapper(LangAnalyzer.new(Language.ENGLISH), this.mappedAnalyzer)

        val config = IndexWriterConfig(fieldAnalyzer).apply {
            openMode = IndexWriterConfig.OpenMode.CREATE_OR_APPEND
        }

        this.writer = IndexWriter(this.directory, config)
    }

    fun indexEntries(name: String, entries: List<Map<String, String>>) {
        if (this.mappedAnalyzer.isEmpty() || entries.isEmpty()) return

        entries.forEach {
            val fieldName = idFields.find { it.startsWith(name) } ?: return@forEach
            val fieldValue = it[fieldName.substringAfter("$name.")] ?: return@forEach

            this.writer.updateDocument(Term(fieldName, fieldValue), this.createDoc(name, it))
        }
    }

    fun searchFieldIndex(field: String, query: String): List<Document> {

        //Open by IndexWriter would be better, but it is still experimental as of 16.12.2024.
        // See https://lucene.apache.org/core/10_0_0/core/org/apache/lucene/index/DirectoryReader.html#open(org.apache.lucene.index.IndexWriter)
        if (this.reader == null)
            this.reader = runCatching { DirectoryReader.open(this.directory) }.getOrNull() ?: return emptyList()

        val queryParser = StandardQueryParser(this.mappedAnalyzer[field] ?: EnglishAnalyzer())

        queryParser.defaultOperator = StandardQueryConfigHandler.Operator.AND
        queryParser.allowLeadingWildcard = true

        DirectoryReader.openIfChanged(this.reader)?.let {
            this.reader?.close()

            this.reader = it
        }

        val searcher = IndexSearcher(this.reader)

        val topDocs = searcher.search(queryParser.parse(this.normalizeOperator(query), field), Int.MAX_VALUE)

        val storedFields = searcher.storedFields()

        return topDocs.scoreDocs.map { storedFields.document(it.doc) }
    }

    override fun close() {
        this.writer.close()
        this.reader?.close()
    }

    private fun normalizeOperator(query: String) =
        operator.entries.fold(query) { acc, entry -> acc.replace(entry.key.toRegex(), entry.value) }

    private fun createDoc(name: String, map: Map<String, String>): Document = Document().apply {
        map.forEach { key, value ->
            val id = "$name.$key"

            if (idFields.contains(id) || idFields.contains(key)) add(LongField(id, value.toLong(), Field.Store.YES))

            if (mappedAnalyzer.contains(id)) add(TextField(id, value, Field.Store.YES))
        }
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("win")
}

