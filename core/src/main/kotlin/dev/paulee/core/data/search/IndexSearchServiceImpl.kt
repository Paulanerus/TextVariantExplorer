package dev.paulee.core.data.search

import dev.paulee.api.data.DataSource
import dev.paulee.api.data.Index
import dev.paulee.api.data.RequiresData
import dev.paulee.api.data.Unique
import dev.paulee.api.data.search.IndexSearchResult
import dev.paulee.api.data.search.IndexSearchService
import dev.paulee.core.data.analysis.Indexer
import dev.paulee.core.splitStr
import kotlin.io.path.Path
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor

private class SearchInfo(val indexer: Indexer) {

    var fields = mutableMapOf<String, Boolean>()

    var identifier = mutableMapOf<String, String>()

    var defaultIndexField: String? = null

    var defaultClass: String? = null
}

class IndexSearchServiceImpl : IndexSearchService {

    private val entries = mutableMapOf<String, SearchInfo>()

    private val keyValueRgx = "\\w+:\\w+|\\w+:\"[^\"]*\"".toRegex()

    override fun init(dataInfo: RequiresData) {
        entries[dataInfo.name] = SearchInfo(Indexer(Path("data/index/${dataInfo.name}"), dataInfo.sources)).apply {
            dataInfo.sources.forEach { clazz ->
                val file = clazz.findAnnotation<DataSource>()?.file ?: return@forEach

                clazz.primaryConstructor?.parameters.orEmpty().forEach { param ->
                    val name = param.name ?: return@forEach

                    val key = "$file.$name"
                    fields[key] = false

                    param.findAnnotation<Index>()?.let { index ->
                        fields[key] = true

                        identifier.putIfAbsent(file, "${file}_ag_id")

                        if (index.default && defaultIndexField.isNullOrEmpty()) {
                            defaultIndexField = "$file.$name"
                            defaultClass = file
                        }
                    }

                    param.findAnnotation<Unique>()?.identify?.let {
                        if (it && param.type.classifier == Long::class) identifier[file] = "$file.$name"
                    }
                }
            }
            if (defaultIndexField.isNullOrEmpty()) println("${dataInfo.name} has no default index field")
        }
    }

    override fun search(source: String, query: String): IndexSearchResult {
        val entry = entries[source] ?: return IndexSearchResult(emptySet(), emptyList())

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
                    else "${entry.defaultClass ?: return IndexSearchResult(emptySet(), emptyList())}.$it"
                }

                if (entry.fields[field] == true) {
                    val value = str.substring(colon + 1).trim('"')
                    val fieldClass = field.substringBefore('.')

                    entry.indexer.searchFieldIndex(field, value)
                        .mapTo(ids) { doc -> doc.getField(entry.identifier[fieldClass]).numericValue().toLong() }

                } else token.add(str)
            }

            entry.defaultIndexField?.let { defaultField ->
                queryToken.takeIf { it.isNotEmpty() }?.let {
                    entry.indexer.searchFieldIndex(defaultField, it.joinToString(" ")).mapTo(ids) { doc ->
                        doc.getField(entry.identifier[entry.defaultClass]).numericValue().toLong()
                    }
                }
            }
        } else {
            entry.defaultIndexField?.let { entry.indexer.searchFieldIndex(it, query) }
                ?.mapTo(ids) { doc -> doc.getField(entry.identifier[entry.defaultClass]).numericValue().toLong() }
        }

        return IndexSearchResult(ids, token)
    }

    override fun close() = this.entries.values.forEach { it.indexer.close() }
}