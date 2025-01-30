package dev.paulee.core.data

import dev.paulee.api.data.*
import dev.paulee.api.data.provider.IStorageProvider
import dev.paulee.core.Logger
import dev.paulee.core.data.analysis.Indexer
import dev.paulee.core.data.io.BufferedCSVReader
import dev.paulee.core.data.provider.StorageProvider
import dev.paulee.core.normalizeDataSource
import dev.paulee.core.splitStr
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.math.ceil
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor

private val logger = Logger.getLogger("DataService")

typealias PageResult = Pair<List<Map<String, String>>, Map<String, List<Map<String, String>>>>

private data class IndexSearchResult(
    val ids: Set<Long> = emptySet<Long>(),
    val tokens: List<String> = emptyList<String>(),
    val indexedValues: Set<String> = emptySet<String>(),
) {
    fun isEmpty(): Boolean = ids.isEmpty() && tokens.isEmpty()
}

private class DataPool(val indexer: Indexer, dataInfo: RequiresData, val storageProvider: IStorageProvider) {
    var fields = mutableMapOf<String, Boolean>()

    var identifier = mutableMapOf<String, String>()

    var defaultIndexField: String? = null

    var defaultClass: String? = null

    val links = mutableMapOf<String, String>()

    val metadata = mutableMapOf<String, Any>()

    private val keyValueRgx = "\\w+:\\w+|\\w+:\"[^\"]*\"".toRegex()

    init {
        dataInfo.sources.forEach { clazz ->
            val file = clazz.findAnnotation<DataSource>()?.file ?: return@forEach

            clazz.findAnnotation<Variant>()?.let { metadata[file] = it }

            clazz.findAnnotation<PreFilter>()?.let { metadata[file] = it }

            val normalized = normalizeDataSource(file)

            clazz.primaryConstructor?.parameters.orEmpty().forEach { param ->
                val name = param.name ?: return@forEach

                val key = "$normalized.$name"
                fields[key] = false

                param.findAnnotation<Link>()?.let { link ->
                    link.clazz.findAnnotation<DataSource>()?.file?.let { linkFile ->

                        if (dataInfo.sources.contains(link.clazz)) links[key] = "$linkFile.$name"
                        else logger.warn("Link '$linkFile' was not specified in the plugin main and will be ignored.")
                    }
                }

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

    fun search(query: String): IndexSearchResult {
        val ids = mutableSetOf<Long>()

        val indexedValues = mutableSetOf<String>()

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
                    else "${defaultClass ?: return IndexSearchResult()}.$it"
                }

                if (fields[field] == true) {
                    val value = str.substring(colon + 1).trim('"')
                    val fieldClass = field.substringBefore('.')

                    indexedValues.add(value)

                    indexer.searchFieldIndex(field, value)
                        .mapTo(ids) { doc -> doc.getField(identifier[fieldClass]).numericValue().toLong() }

                } else token.add(str)
            }

            if (queryToken.isNotEmpty()) {
                val joined = queryToken.joinToString(" ")

                indexedValues.add(joined)

                defaultIndexField?.let { defaultField ->
                    indexer.searchFieldIndex(defaultField, joined).mapTo(ids) { doc ->
                        doc.getField(identifier[defaultClass]).numericValue().toLong()
                    }
                }
            }
        } else {
            defaultIndexField?.let { indexer.searchFieldIndex(it, query) }
                ?.mapTo(ids) { doc -> doc.getField(identifier[defaultClass]).numericValue().toLong() }

            indexedValues.add(query)
        }

        return IndexSearchResult(ids, token, indexedValues)
    }

    fun hasIdentifier(name: String, entries: Map<String, String>): Boolean {
        return entries[identifier[name]?.substringAfter(".")] != null
    }
}

class DataServiceImpl : IDataService {

    private var currentPool: String? = null

    private var currentField: String? = null

    private val pageSize = 50

    private val pageCache = object : LinkedHashMap<Pair<Int, String>, PageResult>(3, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Int, String>, PageResult>?): Boolean {
            return size > 3
        }
    }

    private val storageProvider = mutableMapOf<String, IStorageProvider>()

    private val dataPools = mutableMapOf<String, DataPool>()

    override fun createDataPool(dataInfo: RequiresData, path: Path): Boolean {
        val poolPath = path.resolve(dataInfo.name)

        val storageProvider = StorageProvider.of(dataInfo.storage)

        val initStatus = storageProvider.init(dataInfo, poolPath)

        if (initStatus < 1) return initStatus == 0

        val dataPool = runCatching {
            DataPool(
                indexer = Indexer(poolPath.resolve("index"), dataInfo.sources),
                dataInfo = dataInfo,
                storageProvider = storageProvider
            )
        }.getOrElse { e ->
            logger.exception(e)
            return false
        }

        dataInfo.sources.forEach { clazz ->
            val file = clazz.findAnnotation<DataSource>()?.file

            if (file.isNullOrEmpty()) {
                logger.warn("No data source provided for ${clazz.simpleName}.")
                return@forEach
            }

            val sourcePath = path.resolve(file.let { if (it.endsWith(".csv")) it else "$it.csv" })

            if (!sourcePath.exists()) {
                logger.warn("Source file '$sourcePath' not found.")
                return@forEach
            }

            val idGenerator = generateSequence(1L) { it + 1 }.iterator()

            BufferedCSVReader(sourcePath).readLines { lines ->
                val entries = lines.map { line ->
                    if (dataPool.hasIdentifier(file, line)) line
                    else line + ("${file}_ag_id" to idGenerator.next().toString())
                }

                dataPool.indexer.indexEntries(file, entries)
                storageProvider.insert(file, entries)
            }
        }

        dataPools[dataInfo.name] = dataPool

        logger.info("Created data pool ${dataInfo.name}.")

        return true
    }

    override fun loadDataPools(path: Path, dataInfo: Set<RequiresData>): Int {
        if (!path.exists()) path.createDirectories()

        dataInfo.forEach {
            val poolPath = path.resolve(it.name)

            if (poolPath.exists() && poolPath.isDirectory()) {
                val storageProvider = StorageProvider.of(it.storage)

                storageProvider.init(it, poolPath)

                dataPools[it.name] = DataPool(
                    indexer = Indexer(poolPath.resolve("index"), it.sources),
                    dataInfo = it,
                    storageProvider = storageProvider
                )

                logger.info("Loaded ${it.name} data pool.")
            } else {
                if (!this.createDataPool(it, path)) logger.warn("Failed to create data pool.")
            }
        }

        val amount = dataPools.size

        if (amount == 1) {
            val pool = dataPools.entries.first()

            this.currentPool = pool.key
            this.currentField = pool.value.defaultClass

            if (this.currentField == null) logger.warn("${this.currentPool} has no index field.")
            else logger.info("Set selected data pool to $currentPool.$currentField")
        }

        if (amount > 0) logger.info("Loaded ${dataInfo.size} data ${if (amount == 1) "pool" else "pools"}.")
        else logger.info("No data pools in '$path' available.")

        return dataPools.size
    }

    override fun selectDataPool(selection: String) {
        if (!selection.contains(".")) return

        val (pool, field) = selection.split(".", limit = 2)

        this.currentPool = pool
        this.currentField = field

        logger.info("Selected $selection.")
    }

    override fun getSelectedPool(): String = "${this.currentPool}.${this.currentField}"

    override fun hasSelectedPool(): Boolean = this.currentPool != null && this.currentField != null

    override fun getAvailablePools(): Set<String> =
        dataPools.filter { it.value.fields.any { it.value } }.flatMap { entry ->
            entry.value.fields.filter { it.value }.map { "${entry.key}.${it.key.substringBefore(".")}" }
        }.toSet()

    override fun getPage(query: String, pageCount: Int): PageResult {

        if (this.currentPool == null || this.currentField == null) return Pair(emptyList(), emptyMap())

        logger.info("Query: $query")

        val key = Pair(pageCount, query)

        pageCache[key]?.let { return it }

        val dataPool = this.dataPools[this.currentPool] ?: return Pair(emptyList(), emptyMap())

        val (filterQuery, filter) = this.getPreFilter(query)

        val indexResult = dataPool.search(this.handleReplacements(dataPool.metadata, filterQuery))

        if (filter.isEmpty() && indexResult.isEmpty()) return Pair(emptyList(), emptyMap())

        val entries = dataPool.storageProvider.get(
            this.currentField!!,
            indexResult.ids,
            indexResult.tokens,
            filter,
            offset = pageCount * this.pageSize,
            limit = pageSize
        )

        val links = mutableMapOf<String, List<Map<String, String>>>()
        dataPool.links.forEach { key, value ->
            val keyField = key.substringAfter('.')

            val (valSource, valField) = value.split('.', limit = 2)

            val fields = entries.asSequence().mapNotNull { it[keyField] }.toSet()

            if (fields.isEmpty()) return@forEach

            links[keyField] = dataPool.storageProvider.get(
                valSource, whereClause = fields.map { "$valField:$it" }.toList()
            )
        }

        val result = PageResult(entries, links)

        pageCache[key] = result

        return result
    }

    override fun getPageCount(query: String): Pair<Long, Set<String>> {

        if (this.currentPool == null || this.currentField == null) return Pair(-1, emptySet())

        val dataPool = this.dataPools[this.currentPool] ?: return Pair(-1, emptySet())

        val (filterQuery, filter) = this.getPreFilter(query)

        val indexResult = dataPool.search(this.handleReplacements(dataPool.metadata, filterQuery))

        if (filter.isEmpty() && indexResult.isEmpty()) return return Pair(0, emptySet())

        val count = dataPool.storageProvider.count(
            this.currentField!!, indexResult.ids, indexResult.tokens, filter
        )

        return Pair(ceil(count / pageSize.toDouble()).toLong(), indexResult.indexedValues)
    }

    override fun createStorageProvider(dataInfo: RequiresData, path: Path): IStorageProvider? {
        val name = dataInfo.name

        if (this.storageProvider[name] == null) this.storageProvider[name] = StorageProvider.of(dataInfo.storage)

        val provider = this.storageProvider[name]

        if (provider == null) {
            logger.error("Failed to create StorageProvider of type: ${dataInfo.storage.name}")
            return null
        }

        provider.init(dataInfo, path, true)

        return provider
    }

    override fun close() {
        this.storageProvider.forEach { it.value.close() }

        this.dataPools.values.forEach {
            it.storageProvider.close()
            it.indexer.close()
        }
    }

    private fun handleReplacements(replacements: Map<String, Any>, query: String): String {
        val dataPool = this.dataPools[this.currentPool] ?: return query

        val pattern = Regex("@([^:]+):(\\S+)")

        return pattern.replace(query) {
            val key = it.groupValues[1]
            val value = it.groupValues[2]

            val transform = replacements[key] ?: return@replace it.value

            if (transform is Variant) {
                dataPool.storageProvider.get(key, whereClause = listOf("${transform.base}:$value"))
                    .flatMap { map -> transform.variants.mapNotNull { key -> map[key] } }.toSet()
                    .joinToString(" or ", prefix = "(", postfix = ")")
            } else ""
        }
    }

    private fun getPreFilter(query: String): Pair<String, List<String>> {
        val regex = Regex("@[^:\\s]+:[^:\\s]+:[^:\\s]+")

        val dataPool = this.dataPools[this.currentPool] ?: return Pair(query, emptyList())

        val filters = regex.findAll(query).map { it.value }.toSet()

        val queryWithoutFilter = filters.fold(query) { acc, filter -> acc.replace(filter, "") }.trim()

        return queryWithoutFilter to filters.filter { it.startsWith("@") && it.count { c -> c == ':' } == 2 }
            .flatMap { rawFilter ->
                val (filter, linkValue, value) = rawFilter.substring(1).split(":", limit = 3)

                dataPool.metadata.entries.filter { it.key == filter && it.value is PreFilter }
                    .flatMap { (key, transform) ->
                        val preFilter = transform as PreFilter

                        val linkEntries = dataPool.links.filterKeys { it.startsWith(key) }.mapValues { link ->
                            val (source, field) = link.value.split(".", limit = 2)
                            source to field
                        }

                        linkEntries.values.flatMap { (source, field) ->
                            val ids = dataPool.storageProvider.get(
                                source, whereClause = listOf("${preFilter.linkKey}:$linkValue")
                            ).mapNotNull { it[field]?.let { id -> "$field:$id" } }

                            val transformKey = preFilter.key

                            dataPool.storageProvider.get(
                                key, whereClause = ids + "${preFilter.value}:$value"
                            ).mapNotNull { it[transformKey]?.let { id -> "$transformKey:$id" } }
                        }
                    }
            }.distinct()
    }
}