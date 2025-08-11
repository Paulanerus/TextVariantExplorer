package dev.paulee.api.data

import dev.paulee.api.data.provider.IStorageProvider
import dev.paulee.api.data.provider.QueryOrder
import java.io.Closeable
import java.nio.file.Path

interface IDataService : Closeable {

    suspend fun createDataPool(dataInfo: DataInfo, path: Path): Boolean

    fun loadDataPools(path: Path): Int

    fun selectDataPool(selection: String)

    fun getSelectedPool(): String

    fun hasSelectedPool(): Boolean

    fun getAvailablePools(): Set<String>

    fun getAvailableDataInfo(): Set<DataInfo>

    fun getSuggestions(field: String, value: String): List<String>

    fun getPage(query: String, order: QueryOrder?, pageCount: Int): Pair<List<Map<String, String>>, Map<String, List<Map<String, String>>>>

    fun getPageCount(query: String): Triple<Long, Long, Set<String>>

    fun createStorageProvider(infoName: String, path: Path): IStorageProvider?

    fun dataInfoToString(dataInfo: DataInfo): String?

    fun dataInfoFromString(dataInfo: String): DataInfo?
}