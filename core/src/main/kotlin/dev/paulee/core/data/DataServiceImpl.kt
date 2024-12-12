package dev.paulee.core.data

import dev.paulee.api.data.DataSource
import dev.paulee.api.data.IDataService
import dev.paulee.api.data.RequiresData
import dev.paulee.api.data.Unique
import dev.paulee.api.data.provider.IStorageProvider
import dev.paulee.api.data.search.IndexSearchService
import dev.paulee.core.data.analysis.Indexer
import dev.paulee.core.data.io.BufferedCSVReader
import dev.paulee.core.data.search.IndexSearchServiceImpl
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.math.ceil
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor

private class DataPool

class DataServiceImpl(private val storageProvider: IStorageProvider) : IDataService {

    private val searchService: IndexSearchService = IndexSearchServiceImpl()

    private val pageSize = 50

    private var currentPage = 0

    private val pageCache = object : LinkedHashMap<Int, List<Map<String, String>>>(3, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, List<Map<String, String>>>?): Boolean {
            return size > 3
        }
    }

    private val dataPools = mutableMapOf<Path, DataPool>()

    override fun createDataPool(dataInfo: RequiresData, path: Path): Boolean {
        val initStatus = this.storageProvider.init(dataInfo, path)

        if (initStatus < 1) return initStatus == 0

        val indexer =
            runCatching {
                Indexer(
                    path.resolve("index/${dataInfo.name}"),
                    dataInfo.sources
                )
            }.getOrElse { exception ->
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

            val hasIdentifier =
                clazz.primaryConstructor?.parameters.orEmpty().any { it.findAnnotation<Unique>()?.identify == true }

            val idGenerator = generateSequence(1L) { it + 1 }.iterator()

            BufferedCSVReader(sourcePath).readLines { lines ->
                val entries = lines.map { line ->
                    if (hasIdentifier) line
                    else line + ("${file}_ag_id" to idGenerator.next().toString())
                }

                indexer.indexEntries(file, entries)
                this.storageProvider.insert("${dataInfo.name}.$file", entries)
            }
        }
        indexer.close()

        return true
    }

    override fun loadDataPools(path: Path): Int {
        if (!path.exists()) path.createDirectories()

        path.listDirectoryEntries()
            .filter { it.isDirectory() && !dataPools.containsKey(it) }
            .forEach {
                dataPools[it] = DataPool()
            }

        return dataPools.size
    }

    override fun getPage(query: String, pageCount: Int): List<Map<String, String>> {

        pageCache[pageCount]?.let { return it }

        val indexResult = this.searchService.search("", query)

        val entries = storageProvider.get(
            "",
            indexResult.ids,
            indexResult.tokens,
            offset = this.currentPage * this.pageSize,
            limit = pageSize
        )

        pageCache[this.currentPage] = entries

        this.currentPage++

        return entries
    }

    override fun getPageCount(query: String): Long {
        val indexResult = this.searchService.search("", query)

        val count = this.storageProvider.count("", indexResult.ids, indexResult.tokens)

        return ceil(count / pageSize.toDouble()).toLong()
    }

    override fun close() {
        this.searchService.close()
        this.storageProvider.close()
    }
}