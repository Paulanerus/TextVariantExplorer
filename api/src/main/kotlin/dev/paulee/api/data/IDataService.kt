package dev.paulee.api.data

import java.nio.file.Path

interface IDataService {

    fun createDataPool(dataInfo: RequiresData, path: Path): Boolean

    fun getPage(query: String, amount: Int, pageCount: Int = -1)
}