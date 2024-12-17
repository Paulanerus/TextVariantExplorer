package dev.paulee.core.data

import dev.paulee.api.data.*
import dev.paulee.api.data.provider.IStorageProvider
import dev.paulee.core.data.analysis.Indexer
import dev.paulee.core.data.io.BufferedCSVReader
import dev.paulee.core.normalizeDataSource
import dev.paulee.core.splitStr
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.math.ceil
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor

private data class IndexSearchResult(val ids: Set<Long>, val tokens: List<String>) {
    fun isEmpty(): Boolean = ids.isEmpty() && tokens.isEmpty()
}

private class DataPool(val indexer: Indexer, dataInfo: RequiresData) {
    var fields = mutableMapOf<String, Boolean>()

    var identifier = mutableMapOf<String, String>()

    var defaultIndexField: String? = null

    var defaultClass: String? = null

    var hasIdentifier = false

    val links = mutableMapOf<String, String>()

    private val keyValueRgx = "\\w+:\\w+|\\w+:\"[^\"]*\"".toRegex()

    init {
        dataInfo.sources.forEach { clazz ->
            val file = clazz.findAnnotation<DataSource>()?.file ?: return@forEach

            val normalized = normalizeDataSource(file)

            clazz.primaryConstructor?.parameters.orEmpty().forEach { param ->
                val name = param.name ?: return@forEach

                val key = "$normalized.$name"
                fields[key] = false

                param.findAnnotation<Link>()?.let { link ->
                    link.clazz.findAnnotation<DataSource>()?.file?.let { linkFile ->

                        links[key] = if (link.field.isNotEmpty()) "$linkFile.${link.field}" else "$linkFile.$name"
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
                    if (it && param.type.classifier == Long::class) {
                        hasIdentifier = true
                        identifier[normalized] = "$normalized.$name"
                    }
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
}

class DataServiceImpl(private val storageProvider: IStorageProvider) : IDataService {

    private val pageSize = 50

    private val pageCache = object : LinkedHashMap<Pair<Int, String>, List<Map<String, String>>>(3, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Int, String>, List<Map<String, String>>>?): Boolean {
            return size > 3
        }
    }

    private val dataPools = mutableMapOf<String, DataPool>()

    override fun createDataPool(dataInfo: RequiresData, path: Path): Boolean {
        val poolPath = path.resolve(dataInfo.name)

        val initStatus = this.storageProvider.init(dataInfo, poolPath)

        if (initStatus < 1) return initStatus == 0

        val dataPool = runCatching {
            DataPool(
                indexer = Indexer(poolPath.resolve("index"), dataInfo.sources), dataInfo = dataInfo
            )
        }.getOrElse {
            println("Failed to create index for ${dataInfo.name}")
            return false
        }

        dataInfo.sources.forEach { clazz ->
            val file = clazz.findAnnotation<DataSource>()?.file

            if (file.isNullOrEmpty()) {
                println("No data source provided for ${clazz.simpleName}")
                return@forEach
            }

            val sourcePath = path.resolve(file.let { if (it.endsWith(".csv")) it else "$it.csv" })

            if (!sourcePath.exists()) {
                println("Source file '$sourcePath' not found")
                return@forEach
            }

            val idGenerator = generateSequence(1L) { it + 1 }.iterator()

            BufferedCSVReader(sourcePath).readLines { lines ->
                val entries = lines.map { line ->
                    if (dataPool.hasIdentifier) line
                    else line + ("${file}_ag_id" to idGenerator.next().toString())
                }

                dataPool.indexer.indexEntries(file, entries)
                this.storageProvider.insert("${dataInfo.name}.$file", entries)
            }
        }

        dataPools[dataInfo.name] = dataPool
        this.storageProvider.init(dataInfo, poolPath)

        return true
    }

    override fun loadDataPools(path: Path, dataInfo: Set<RequiresData>): Int {
        if (!path.exists()) path.createDirectories()

        path.listDirectoryEntries().filter { it.isDirectory() && !dataPools.containsKey(it.name) }.forEach {
            val dataInfo = dataInfo.find { info -> info.name == it.name } ?: return@forEach

            dataPools[it.name] = DataPool(indexer = Indexer(it.resolve("index"), dataInfo.sources), dataInfo = dataInfo)

            this.storageProvider.init(dataInfo, it)
        }

        return dataPools.size
    }

    override fun selectDataPool() {
        TODO("Not yet implemented")
    }

    override fun getPage(query: String, pageCount: Int): List<Map<String, String>> {

        val key = Pair(pageCount, query)

        pageCache[key]?.let { return it }

        val dataPool = this.dataPools["demo"] ?: return emptyList()

        val indexResult = dataPool.search(query)

        if (indexResult.isEmpty()) return emptyList()

        val entries = this.storageProvider.get(
            "demo.verses", indexResult.ids, indexResult.tokens, offset = pageCount * this.pageSize, limit = pageSize
        )

        val links = mutableListOf<Map<String, String>>()
        dataPool.links.forEach { key, value ->
            val keyField = key.substringAfter('.')

            val (valSource, valField) = value.split('.', limit = 2)

            val fields = entries.asSequence()
                .mapNotNull { it[keyField] }
                .toSet()

            if (fields.isEmpty()) return@forEach

            this.storageProvider.get("demo.$valSource", whereClause = fields.map { "$valField:$it" }.toList())
                .toCollection(links)
        }

        pageCache[key] = entries

        return entries
    }

    override fun getPageCount(query: String): Long {
        val dataPool = this.dataPools["demo"] ?: return -1

        val indexResult = dataPool.search(query)

        if (indexResult.isEmpty()) return 0

        val count = this.storageProvider.count("demo.verses", indexResult.ids, indexResult.tokens)

        return ceil(count / pageSize.toDouble()).toLong()
    }

    override fun close() {
        this.storageProvider.close()

        this.dataPools.values.forEach { it.indexer.close() }
    }
}