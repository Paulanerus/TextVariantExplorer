package dev.paulee.core.data.analysis

import dev.paulee.api.data.Index
import dev.paulee.api.data.Language
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.TextField
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
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

    init {
        //See https://lucene.apache.org/core/5_2_0/core/org/apache/lucene/store/NIOFSDirectory.html
        val directory = if (this.isWindows()) FSDirectory.open(path) else NIOFSDirectory.open(path)

        sources
            .forEach {
                val name = it.simpleName?.lowercase() ?: return@forEach

                it.primaryConstructor
                    ?.parameters
                    .orEmpty()
                    .filter { it.hasAnnotation<Index>() }
                    .forEach { param ->
                        val paramName = param.name ?: return@forEach

                        val lang = param.findAnnotation<Index>()!!.lang

                        this.mappedAnalyzer["$name.$paramName"] = LangAnalyzer.new(lang)
                    }
            }
        val fieldAnalyzer = PerFieldAnalyzerWrapper(LangAnalyzer.new(Language.ENGLISH), mappedAnalyzer)

        val config = IndexWriterConfig(fieldAnalyzer).apply {
            openMode = IndexWriterConfig.OpenMode.CREATE_OR_APPEND
        }

        writer = IndexWriter(directory, config)
    }

    fun indexEntries(name: String, entries: List<Map<String, String>>) {

        if (this.mappedAnalyzer.isEmpty() || entries.isEmpty()) return

        this.writer.addDocuments(entries.map { this.createDoc(name, it) })
    }

    fun close() = this.writer.close()

    private fun createDoc(name: String, map: Map<String, String>): Document {
        return Document().apply {
            map.forEach { key, value ->

                if (!mappedAnalyzer.contains("$name.$key"))
                    return@forEach

                add(TextField("$name.$key", value, Field.Store.YES))
            }
        }
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("win")
}

