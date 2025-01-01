package dev.paulee.api.data

import java.io.Closeable
import java.nio.file.Path

interface IDataService : Closeable {

    fun createDataPool(dataInfo: RequiresData, path: Path): Boolean

    fun loadDataPools(path: Path, dataInfo: Set<RequiresData>): Int

    fun selectDataPool(selection: String)

    fun getSelectedPool(): String

    fun getAvailablePools(): Set<String>

    fun getPage(query: String, pageCount: Int): Pair<List<Map<String, String>>, Map<String, List<Map<String, String>>>>

    fun getPageCount(query: String): Pair<Long, Set<String>>
}