package dev.paulee.core.data.model

import dev.paulee.api.data.DataInfo
import dev.paulee.api.data.IndexField
import dev.paulee.api.data.UniqueField
import dev.paulee.api.data.provider.IStorageProvider
import dev.paulee.core.data.analysis.Indexer
import dev.paulee.core.normalizeDataSource
import dev.paulee.core.splitStr
import org.apache.lucene.document.Document
import org.slf4j.LoggerFactory.getLogger

internal data class IndexSearchResult(
    val ids: LinkedHashSet<Long> = LinkedHashSet(),
    val tokens: List<String> = emptyList(),
    val indexedValues: Set<String> = emptySet(),
) {
    fun isEmpty(): Boolean = ids.isEmpty() && tokens.isEmpty()
}

internal class DataPool(val indexer: Indexer?, val dataInfo: DataInfo, val storageProvider: IStorageProvider?) {

    private val logger = getLogger("DataPool (${dataInfo.name})")

    var fields = mutableMapOf<String, Boolean>()

    var identifier = mutableMapOf<String, String>()

    var defaultIndexField: String? = null

    var defaultClass: String? = null

    val links = mutableMapOf<String, String>()

    val metadata = mutableMapOf<String, Any>()

    private val keyValueRgx = "\\w+:\\w+|\\w+:\"[^\"]*\"".toRegex()

    init {
        dataInfo.sources.forEach { source ->
            val sourceName = source.name

            source.variantMapping?.let { metadata[sourceName] = it }

            source.preFilter?.let { metadata[sourceName] = it }

            val normalized = normalizeDataSource(sourceName)

            source.fields.forEach inner@{ field ->
                val fieldName = field.name

                val key = "$normalized.$fieldName"
                fields[key] = false

                if (field.sourceLink.isNotBlank()) {
                    if (dataInfo.sources.any { link -> link.name == field.sourceLink && link.fields.any { it.name == field.name } }) {
                        links[key] = "${field.sourceLink}.$fieldName"
                    } else logger.warn("Link '${field.sourceLink}' is not present and will be ignored.")
                }

                when (field) {
                    is IndexField -> {
                        fields[key] = true

                        identifier.putIfAbsent(normalized, "${normalized}_ag_id")

                        if (field.default && defaultIndexField.isNullOrEmpty()) {
                            defaultIndexField = "$normalized.$fieldName"
                            defaultClass = normalized
                        }
                    }

                    is UniqueField -> if (field.identify) identifier[normalized] = "$normalized.$fieldName"
                    else -> {}
                }
            }
        }

        if (this.defaultIndexField.isNullOrEmpty()) {
            logger.warn("${dataInfo.name} has no default index field.")

            this.fields.filter { it.value }.entries.firstOrNull()?.key.let {
                if (it == null) {
                    logger.warn("${dataInfo.name} has no indexable fields.")
                } else {
                    logger.warn("'$it' was chosen instead.")
                    this.defaultIndexField = it
                }
            }
        }
    }

    fun search(query: String, semantic: Boolean): IndexSearchResult {
        if (indexer == null) return IndexSearchResult()

        val ids = LinkedHashSet<Long>()

        fun addIdsFrom(docs: List<Document>, fieldClass: String?) {
            val classKey = fieldClass ?: return
            val idFieldName = identifier[classKey] ?: return

            docs.forEach { it.getField(idFieldName)?.numericValue()?.toLong()?.let(ids::add) }
        }

        val similarityThreshold = 0.7f

        val indexedValues = mutableSetOf<String>()

        val token = mutableListOf<String>()
        if (keyValueRgx.containsMatchIn(query)) {

            val queryToken = mutableListOf<String>()

            splitStr(query, delimiter = ' ').forEach { str ->

                var colon = -1
                var hasSpace = false

                for (i in str.indices) {
                    val ch = str[i]
                    if (ch == ':') { colon = i; break }
                    if (ch == ' ') hasSpace = true
                }

                if (colon == -1) {
                    queryToken.add(if (hasSpace) "\"$str\"" else str)
                    return@forEach
                }

                val field = str.substring(0, colon).let {
                    if (it.contains(".")) it
                    else "${defaultClass ?: return IndexSearchResult()}.$it"
                }

                if (fields[field] == true) {
                    val value = str.substring(colon + 1)
                    val fieldClass = field.substringBefore('.')

                    indexedValues.add(value)

                    val docs =
                        if (semantic) indexer.searchMatchingVec(field, value, similarityThreshold)
                        else indexer.searchFieldIndex(field, value)

                    addIdsFrom(docs, fieldClass)
                } else token.add(str)
            }

            if (queryToken.isNotEmpty()) {
                val joined = queryToken.joinToString(" ")

                indexedValues.add(joined)

                defaultIndexField?.let { defaultField ->
                    val docs =
                        if (semantic) indexer.searchMatchingVec(defaultField, joined, similarityThreshold)
                        else indexer.searchFieldIndex(defaultField, joined)

                    addIdsFrom(docs, defaultClass)
                }
            }
        } else {
            defaultIndexField?.let {
                val docs =
                    if (semantic) indexer.searchMatchingVec(it, query, similarityThreshold)
                    else indexer.searchFieldIndex(it, query)

                addIdsFrom(docs, defaultClass)
            }

            indexedValues.add(query)
        }

        return IndexSearchResult(ids, token, indexedValues)
    }
}
