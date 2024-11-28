package dev.paulee.core.data.analysis

import dev.paulee.api.data.DataSource
import dev.paulee.api.data.Index
import dev.paulee.api.data.Language
import dev.paulee.api.data.Unique
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.LongField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.store.NIOFSDirectory
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.primaryConstructor

class Indexer(path: Path, sources: Array<KClass<*>>) {

    private val writer: IndexWriter

    private val mappedAnalyzer = mutableMapOf<String, Analyzer>()

    private val idFields = mutableSetOf<String>()

    init {
        //See https://lucene.apache.org/core/5_2_0/core/org/apache/lucene/store/NIOFSDirectory.html
        val directory = if (this.isWindows()) FSDirectory.open(path) else NIOFSDirectory.open(path)

        sources.forEach {
            val name = it.findAnnotation<DataSource>()?.file ?: return@forEach

            it.primaryConstructor
                ?.parameters
                .orEmpty()
                .filter { it.hasAnnotation<Index>() || it.hasAnnotation<Unique>() }
                .forEach { param ->
                    val paramName = param.name ?: return@forEach

                    param.findAnnotation<Index>()?.also { index ->
                        this.mappedAnalyzer["$name.$paramName"] = LangAnalyzer.new(index.lang)
                    }

                    param.findAnnotation<Unique>()?.takeIf { param.type.classifier == Long::class && it.identify }
                        ?.also { unique -> this.idFields.add("$name.$paramName") }
                }

            idFields.add("${name}_ag_id")
        }

        val fieldAnalyzer = PerFieldAnalyzerWrapper(LangAnalyzer.new(Language.ENGLISH), mappedAnalyzer)

        val config = IndexWriterConfig(fieldAnalyzer).apply {
            openMode = IndexWriterConfig.OpenMode.CREATE_OR_APPEND
        }

        writer = IndexWriter(directory, config)
    }

    fun indexEntries(name: String, entries: List<Map<String, String>>) {
        if (this.mappedAnalyzer.isEmpty() || entries.isEmpty()) return

        entries.forEach {
            val fieldName = idFields.find { it.startsWith(name) } ?: return@forEach
            val fieldValue = it[fieldName.substringAfter("$name.")] ?: return@forEach

            this.writer.updateDocument(Term(fieldName, fieldValue), this.createDoc(name, it))
        }
    }

    fun close() = this.writer.close()

    private fun createDoc(name: String, map: Map<String, String>): Document {
        return Document().apply {
            map.forEach { key, value ->

                val id = "$name.$key"

                if (idFields.contains(id) || idFields.contains(key)) add(LongField(id, value.toLong(), Field.Store.YES))

                if (mappedAnalyzer.contains(id)) add(TextField(id, value, Field.Store.YES))
            }
        }
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("win")
}

