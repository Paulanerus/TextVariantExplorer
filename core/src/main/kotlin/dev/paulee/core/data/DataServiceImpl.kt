package dev.paulee.core.data

import dev.paulee.api.data.*
import dev.paulee.api.data.provider.IStorageProvider
import dev.paulee.api.data.provider.ProviderStatus
import dev.paulee.api.data.provider.QueryOrder
import dev.paulee.api.internal.Embedding
import dev.paulee.core.data.analysis.Indexer
import dev.paulee.core.data.model.DataPool
import dev.paulee.core.data.provider.EmbeddingProvider
import dev.paulee.core.data.provider.StorageProvider
import dev.paulee.core.splitStr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory.getLogger
import java.io.IOException
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.path.*
import kotlin.math.ceil

typealias PageResult = Pair<List<Map<String, String>>, Map<String, List<Map<String, String>>>>

object DataServiceImpl : IDataService {

    private val logger = getLogger(DataServiceImpl::class.java)

    private const val PAGE_SIZE = 50

    private const val BATCH_SIZE = 1000

    private val variantPattern = Regex("@([^:]+):(\\S+)")

    private val preFilterPattern = Regex("@[^:\\s]+:[^:\\s]+:[^:\\s]+")

    private var currentPool: String? = null

    private var currentField: String? = null

    private val pageCache = object : LinkedHashMap<Triple<Int, String, QueryOrder?>, PageResult>(6, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Triple<Int, String, QueryOrder?>, PageResult>?): Boolean {
            return size > 6
        }
    }

    private val storageProvider = mutableMapOf<String, IStorageProvider>()

    private val dataPools = mutableMapOf<String, DataPool>()

    init {
        loadDataPools(FileService.dataDir)
    }

    override suspend fun createDataPool(dataInfo: DataInfo, onProgress: (progress: Int) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val poolPath = FileService.dataDir.resolve(dataInfo.name)

                val currentProvider = StorageProvider.of(dataInfo.storageType)

                when (currentProvider.init(dataInfo, poolPath)) {
                    ProviderStatus.Success -> Unit
                    ProviderStatus.Exists -> return@withContext true
                    else -> return@withContext false
                }

                dataInfoToString(dataInfo)?.let { json ->
                    poolPath.resolve("info.json").writeText(json)
                    logger.info("Created '${dataInfo.name}' info file.")
                }

                val dataPool = runCatching {
                    DataPool(
                        indexer = Indexer(poolPath.resolve("index"), dataInfo),
                        dataInfo = dataInfo,
                        storageProvider = currentProvider
                    )
                }.getOrElse { e ->
                    logger.error("Exception: Failed to create data pool.", e)
                    return@withContext false
                }

                val sourcesWithIndex = dataInfo.sources.filter { it.fields.any { field -> field is IndexField } }

                val totalBatches =
                    ((sourcesWithIndex.sumOf { currentProvider.count(it.name) } + BATCH_SIZE - 1) / BATCH_SIZE).toInt()

                var processedBatches = 0
                var lastPercentage = 0
                onProgress(0)

                sourcesWithIndex
                    .forEach { source ->
                        val name = source.name

                        currentProvider.streamData(name)
                            .chunked(BATCH_SIZE)
                            .forEach { entries ->
                                dataPool.indexer.indexEntries(name, entries)

                                processedBatches++

                                val percentage =
                                    (if (totalBatches > 0) (processedBatches * 100) / totalBatches else 0).toInt()

                                if (percentage > lastPercentage) {
                                    onProgress(percentage)
                                    lastPercentage = percentage
                                }
                            }
                    }

                dataPool.indexer.finish()

                dataPools[dataInfo.name] = dataPool

                logger.info("Created data pool ${dataInfo.name}.")

                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                logger.error("Failed to create data pool ${dataInfo.name}.", e)
                false
            } catch (e: Exception) {
                logger.error("Unexpected error for ${dataInfo.name}.", e)
                false
            }
        }

    @OptIn(ExperimentalPathApi::class)
    fun loadDataPools(path: Path) {
        if (path.notExists()) {
            logger.warn("Data pool directory does not exist.")
            return
        }

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

            if (storageProvider.init(dataInfo, child) == ProviderStatus.Exists) {
                dataPools[dataInfo.name] =
                    DataPool(Indexer(child.resolve("index"), dataInfo), dataInfo, storageProvider)

                logger.info("Loaded ${dataInfo.name} data pool.")
            } else {
                logger.info("Deleting invalid or empty data pool directory '${dataInfo.name}'.")

                runCatching { child.deleteRecursively() }
                    .onFailure { e -> logger.error("Failed to delete directory ${child.fileName}.", e) }
            }
        }

        val amount = dataPools.size
        if (amount == 1) {
            val pool = dataPools.entries.first()

            this.currentPool = pool.key
            this.currentField = pool.value.defaultClass //FIXME: Select any indexable field if available.

            if (this.currentField == null) logger.warn("${this.currentPool} has no index field.")
            else logger.info("Set selected data pool to $currentPool.$currentField.")
        }

        if (amount > 0) logger.info("Loaded $amount data ${if (amount == 1) "pool" else "pools"}.")
        else logger.info("No data pools in '$path' available.")
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
        dataPools.filter { pool -> pool.value.fields.any { it.value } }.flatMap { entry ->
            entry.value.fields.filter { it.value }.map { "${entry.key}.${it.key.substringBefore(".")}" }
        }.toSet()

    override fun getAvailableDataInfo(): Set<DataInfo> = this.dataPools.values.map { it.dataInfo }.toSet()

    override fun getSuggestions(field: String, value: String): List<String> {
        val current = this.currentField ?: return emptyList()

        val dataPool = this.dataPools[this.currentPool] ?: return emptyList()

        val fieldExists = dataPool.dataInfo.sources
            .firstOrNull { it.name == current }
            ?.fields
            ?.any { it.name == field } == true

        if (!fieldExists) return emptyList()


        return dataPool.storageProvider.suggestions(current, field, value, 6)
    }

    override suspend fun downloadModel(model: Embedding.Model, path: Path, onProgress: (progress: Int) -> Unit) =
        EmbeddingProvider.downloadModel(model, path, onProgress)

    override fun getPage(query: String, isSemantic: Boolean, order: QueryOrder?, pageCount: Int): PageResult {

        if (this.currentPool == null || this.currentField == null) return Pair(emptyList(), emptyMap())

        logger.info("Query (${order ?: "None"} | Semantic: $isSemantic): $query")

        val key = Triple(pageCount, query, order)

        pageCache[key]?.let { return it }

        val dataPool = this.dataPools[this.currentPool] ?: return Pair(emptyList(), emptyMap())

        val (filterQuery, filter) = this.getPreFilter(query)

        val indexResult = dataPool.search(this.handleReplacements(dataPool.metadata, filterQuery), isSemantic)

        if (filter.isEmpty() && indexResult.isEmpty()) return Pair(emptyList(), emptyMap())

        val entries = dataPool.storageProvider.get(
            this.currentField!!,
            indexResult.ids,
            indexResult.tokens,
            filter,
            order,
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

    override fun getPageCount(query: String, isSemantic: Boolean): Triple<Long, Long, Set<String>> {
        if (this.currentPool == null || this.currentField == null) return Triple(-1, -1, emptySet())

        val dataPool = this.dataPools[this.currentPool] ?: return Triple(-1, -1, emptySet())

        val (filterQuery, filter) = this.getPreFilter(query)

        val indexResult = dataPool.search(handleReplacements(dataPool.metadata, filterQuery), isSemantic)

        if (filter.isEmpty() && indexResult.isEmpty()) return Triple(0, 0, emptySet())

        val count = dataPool.storageProvider.count(
            this.currentField!!, indexResult.ids, indexResult.tokens, filter
        )

        val indexedValues = indexResult.indexedValues.flatMap { splitStr(it, ' ') }.map { it.trim('(', ')') }
            .mapNotNull { flattenToken(it) }.toSet()

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

        provider.init(dataInfo, path)

        return provider
    }

    override fun dataInfoToString(dataInfo: DataInfo): String? = FileService.toJson(dataInfo)

    override fun dataInfoFromString(dataInfo: String): DataInfo? = FileService.fromJson(dataInfo)

    override fun appDir(): Path = FileService.appDir

    override fun dataDir(): Path = FileService.dataDir

    override fun modelDir(): Path = FileService.modelsDir

    override fun close() {
        this.storageProvider.forEach { it.value.close() }

        this.dataPools.values.forEach {
            it.storageProvider.close()
            it.indexer.close()
        }

        EmbeddingProvider.close()
    }

    private fun handleReplacements(replacements: Map<String, Any>, query: String): String {
        val dataPool = this.dataPools[this.currentPool] ?: return query

        return variantPattern.replace(query) {
            val key = it.groupValues[1]
            val value = it.groupValues[2]

            when (val transform = replacements[key]) {
                is VariantMapping -> {
                    dataPool.storageProvider.get(key, whereClause = listOf("${transform.base}:$value"))
                        .flatMap { map -> transform.variants.mapNotNull { key -> map[key] } }.toSet()
                        .takeIf { set -> set.isNotEmpty() }?.joinToString(" or ", prefix = "(", postfix = ")") ?: ""
                }

                else -> ""
            }
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
                            ).mapNotNull { it[field]?.let { id -> "$field:$id" } }.distinct()

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
    