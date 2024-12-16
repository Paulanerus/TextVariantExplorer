package dev.paulee.core.data.search

import dev.paulee.api.data.DataSource
import dev.paulee.api.data.Index
import dev.paulee.api.data.RequiresData
import dev.paulee.api.data.Unique
import dev.paulee.core.data.analysis.Indexer
import dev.paulee.core.normalizeDataSource
import dev.paulee.core.splitStr
import java.nio.file.Path
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor

data class IndexSearchResult(val ids: Set<Long>, val tokens: List<String>)

class QueryHandler {

    private val keyValueRgx = "\\w+:\\w+|\\w+:\"[^\"]*\"".toRegex()

    var fields = mutableMapOf<String, Boolean>()

    var identifier = mutableMapOf<String, String>()

    var defaultIndexField: String? = null

    var defaultClass: String? = null

    lateinit var indexer: Indexer

    fun init(dataInfo: RequiresData, path: Path) {
        this.indexer = Indexer(path.resolve("index"), dataInfo.sources)

        dataInfo.sources.forEach { clazz ->
            val file = clazz.findAnnotation<DataSource>()?.file ?: return@forEach

            val normalized = normalizeDataSource(file)

            clazz.primaryConstructor?.parameters.orEmpty().forEach { param ->
                val name = param.name ?: return@forEach

                val key = "$normalized.$name"
                fields[key] = false

                param.findAnnotation<Index>()?.let { index ->
                    fields[key] = true

                    identifier.putIfAbsent(normalized, "${normalized}_ag_id")

                    if (index.default && defaultIndexField.isNullOrEmpty()) {
                        defaultIndexField = "$normalized.$name"
                        defaultClass = normalized
                    }
                }

                param.findAnnotation<Unique>()?.identify?.let {
                    if (it && param.type.classifier == Long::class) identifier[normalized] = "$normalized.$name"
                }
            }
        }

        if (defaultIndexField.isNullOrEmpty()) println("${dataInfo.name} has no default index field")
    }

    fun search(query: String): IndexSearchResult {
        val ids = mutableSetOf<Long>()

        val token = mutableListOf<String>()
        if (keyValueRgx.containsMatchIn(query)) {

            val queryToken = mutableListOf<String>()

            splitStr(query, delimiter = ' ').forEach { str ->

                val colon = str.indexOf(':')

                if (colon == -1) {
                    queryToken.add(str)
                    return@forEach
                }

                val field = str.substring(0, colon).let {
                    if (it.contains(".")) it
                    else "${defaultClass ?: return IndexSearchResult(emptySet(), emptyList())}.$it"
                }

                if (fields[field] == true) {
                    val value = str.substring(colon + 1).trim('"')
                    val fieldClass = field.substringBefore('.')

                    indexer.searchFieldIndex(field, value)
                        .mapTo(ids) { doc -> doc.getField(identifier[fieldClass]).numericValue().toLong() }

                } else token.add(str)
            }

            defaultIndexField?.let { defaultField ->
                queryToken.takeIf { it.isNotEmpty() }?.let {
                    indexer.searchFieldIndex(defaultField, it.joinToString(" ")).mapTo(ids) { doc ->
                        doc.getField(identifier[defaultClass]).numericValue().toLong()
                    }
                }
            }
        } else {
            defaultIndexField?.let { indexer.searchFieldIndex(it, query) }
                ?.mapTo(ids) { doc -> doc.getField(identifier[defaultClass]).numericValue().toLong() }
        }

        return IndexSearchResult(ids, token)
    }

    fun close() = indexer.close()
}