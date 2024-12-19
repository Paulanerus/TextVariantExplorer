package dev.paulee.api.data

import java.io.Closeable
import java.nio.file.Path

interface IDataService : Closeable {

    fun createDataPool(dataInfo: RequiresData, path: Path): Boolean

    fun loadDataPools(path: Path, dataInfo: Set<RequiresData>): Int

    fun selectDataPool()

    fun getPage(query: String, pageCount: Int): List<Map<String, String>>

    fun getPageCount(query: String): Pair<Long, List<String>>
}