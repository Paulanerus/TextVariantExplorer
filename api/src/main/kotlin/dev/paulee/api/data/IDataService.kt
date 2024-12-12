package dev.paulee.api.data

import java.io.Closeable
import java.nio.file.Path

interface IDataService : Closeable {

    fun createDataPool(dataInfo: RequiresData, path: Path): Boolean

    fun loadDataPools(path: Path): Int

    fun getPage(query: String, pageCount: Int = -1): List<Map<String, String>>

    fun getPageCount(query: String): Long
}