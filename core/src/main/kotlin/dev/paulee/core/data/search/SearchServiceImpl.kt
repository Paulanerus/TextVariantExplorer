package dev.paulee.core.data.search

import dev.paulee.api.data.DataSource
import dev.paulee.api.data.Index
import dev.paulee.api.data.RequiresData
import dev.paulee.api.data.Unique
import dev.paulee.api.data.search.SearchService
import dev.paulee.core.data.analysis.Indexer
import kotlin.io.path.Path
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor

private class SearchInfo(val indexer: Indexer) {

    var indexedFields = mutableSetOf<String>()

    var identifier = mutableMapOf<String, String>()

    var defaultIndexField: String = ""
}

class SearchServiceImpl : SearchService {

    private val entries = mutableMapOf<String, SearchInfo>()

    private val keyValueRgx = "\\w+:\\w+|\\w+:\"[^\"]*\"".toRegex()

    override fun init(dataInfo: RequiresData) {
        entries[dataInfo.name] = SearchInfo(Indexer(Path("data/index/${dataInfo.name}"), dataInfo.sources)).apply {

            dataInfo.sources.forEach { clazz ->
                val file = clazz.findAnnotation<DataSource>()?.file ?: return@forEach

                clazz.primaryConstructor?.parameters.orEmpty().forEach { param ->
                    val name = param.name ?: return@forEach

                    val key = "$file.$name"

                    param.findAnnotation<Index>()?.let { index ->
                        indexedFields.add(key)

                        identifier.putIfAbsent(file, "${file}_ag_id")

                        if (index.default && defaultIndexField.isEmpty()) defaultIndexField = "$file.$name"
                    }

                    param.findAnnotation<Unique>()?.identify?.let {
                        if (it && param.type.classifier == Long::class) identifier[file] = "$file.$name"
                    }
                }
            }
        }
    }

    override fun search(source: String, query: String) {

        val entry = entries[source] ?: return
    }

    override fun close() = this.entries.values.forEach { it.indexer.close() }
}