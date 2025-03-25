package dev.paulee.core.data

import dev.paulee.api.data.*
import dev.paulee.api.data.provider.IStorageProvider
import dev.paulee.core.data.analysis.Indexer
import dev.paulee.core.data.io.BufferedCSVReader
import dev.paulee.core.data.provider.StorageProvider
import dev.paulee.core.normalizeDataSource
import dev.paulee.core.splitStr
import org.slf4j.LoggerFactory.getLogger
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.math.ceil

private val logger = getLogger(DataServiceImpl::class.java)

typealias PageResult = Pair<List<Map<String, String>>, Map<String, List<Map<String, String>>>>

private data class IndexSearchResult(
    val ids: Set<Long> = emptySet(),
    val tokens: List<String> = emptyList(),
    val indexedValues: Set<String> = emptySet(),
) {
    fun isEmpty(): Boolean = ids.isEmpty() && tokens.isEmpty()
}

private class DataPool(val indexer: Indexer, val dataInfo: DataInfo, val storageProvider: IStorageProvider) {

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

            //clazz.findAnnotation<Variant>()?.let { metadata[file] = it }

            //clazz.findAnnotation<PreFilter>()?.let { metadata[file] = it }

            val normalized = normalizeDataSource(sourceName)

            source.fields.forEach inner@{ field ->
                val fieldName = field.name

                val key = "$normalized.$fieldName"
                fields[key] = false

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
                    is LinkField -> {
                        if(dataInfo.sources.any { link -> link.name == field.source && link.fields.any { it.name == field.name } }) links[key] = "${field.source}.$fieldName"
                        else logger.warn("Link '${field.source}' is not present and will be ignored.")
                    }
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

    companion object {
        private const val PAGE_SIZE = 50

        private val variantPattern = Regex("@([^:]+):(\\S+)")

        private val preFilterPattern = Regex("@[^:\\s]+:[^:\\s]+:[^:\\s]+")
    }

    private var currentPool: String? = null

    private var currentField: String? = null

    private val pageCache = object : LinkedHashMap<Pair<Int, String>, PageResult>(3, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Int, String>, PageResult>?): Boolean {
            return size > 3
        }
    }

    private val storageProvider = mutableMapOf<String, IStorageProvider>()

    private val dataPools = mutableMapOf<String, DataPool>()

    override fun createDataPool(dataInfo: DataInfo, path: Path): Boolean {
        val poolPath = path.resolve(dataInfo.name)

        val storageProvider = StorageProvider.of(dataInfo.storageType)

        val initStatus = storageProvider.init(dataInfo, poolPath)

        if (initStatus < 1) return initStatus == 0

        val dataPool = runCatching {
            DataPool(
                indexer = Indexer(poolPath.resolve("index"), dataInfo.sources),
                dataInfo = dataInfo,
                storageProvider = storageProvider
            )
        }.getOrElse { e ->
            logger.error("Exception: Failed to create data pool.", e)
            return false
        }

        dataInfo.sources.forEach { source ->
            val file = source.name

            if (file.isEmpty()) {
                logger.warn("No data source provided for ${file}.")
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

    override fun loadDataPools(path: Path): Int {
        if (!path.exists()) path.createDirectories()

        path.forEachDirectoryEntry { child ->
            if (!child.isDirectory()) return@forEachDirectoryEntry

            val jsonFile = child.listDirectoryEntries().firstOrNull { it.extension == "json" }

            if (jsonFile == null) {
                logger.warn("Data pool '${child.name}' has no json specification.")
                return@forEachDirectoryEntry
            }

            val dataInfo = FileService.fromJson(runCatching { jsonFile.readText() }.getOrDefault(""))
                ?: return@forEachDirectoryEntry

            val storageProvider = StorageProvider.of(dataInfo.storageType)

            if (storageProvider.init(dataInfo, child) == 0) {
                dataPools[dataInfo.name] =
                    DataPool(Indexer(child.resolve("index"), dataInfo.sources), dataInfo, storageProvider)

                logger.info("Loaded ${dataInfo.name} data pool.")
            } else {
                if (!this.createDataPool(dataInfo, path)) logger.warn("Failed to create data pool.")
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

        if (amount > 0) logger.info("Loaded $amount data ${if (amount == 1) "pool" else "pools"}.")
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
            offset = pageCount * PAGE_SIZE,
            limit = PAGE_SIZE
        )

        val links = mutableMapOf<String, List<Map<String, String>>>()
        dataPool.links.forEach { (key, value) ->
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

    override fun getPageCount(query: String): Triple<Long, Long, Set<String>> {

        if (this.currentPool == null || this.currentField == null) return Triple(-1, -1, emptySet())

        val dataPool = this.dataPools[this.currentPool] ?: return Triple(-1, -1, emptySet())

        val (filterQuery, filter) = this.getPreFilter(query)

        val indexResult = dataPool.search(this.handleReplacements(dataPool.metadata, filterQuery))

        if (filter.isEmpty() && indexResult.isEmpty()) return Triple(0, 0, emptySet())

        val count = dataPool.storageProvider.count(
            this.currentField!!, indexResult.ids, indexResult.tokens, filter
        )

        val indexedValues =
            indexResult.indexedValues
                .flatMap { splitStr(it, ' ') }
                .map { it.trim('(', ')') }
                .mapNotNull { flattenToken(it) }
                .toSet()

        return Triple(count, ceil(count / PAGE_SIZE.toDouble()).toLong(), indexedValues)
    }

    override fun createStorageProvider(infoName: String, path: Path): IStorageProvider? {
        val dataInfo = this.dataPools[infoName]?.dataInfo ?: return null

        if (this.storageProvider[infoName] == null) this.storageProvider[infoName] =
            StorageProvider.of(dataInfo.storageType)

        val provider = this.storageProvider[infoName]

        if (provider == null) {
            logger.error("Failed to create StorageProvider of type: ${dataInfo.storageType.name}")
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

        return variantPattern.replace(query) {
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
        val dataPool = this.dataPools[this.currentPool] ?: return Pair(query, emptyList())

        val filters = preFilterPattern.findAll(query).map { it.value }.toSet()

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

    private fun flattenToken(token: String): String? {
        return if (token.equals("and", false) || token.equals("not", false) || token.equals(
                "or",
                false
            ) || token.isBlank() || token.length < 2
        ) null
        else token.trim()
    }
}
    