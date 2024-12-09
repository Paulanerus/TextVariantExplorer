package dev.paulee.api.data.search

import dev.paulee.api.data.RequiresData
import java.io.Closeable

data class IndexSearchResult(val ids: Set<Long>, val tokens: List<String>)

interface IndexSearchService : Closeable {

    fun init(dataInfo: RequiresData)

    fun search(source: String, query: String): IndexSearchResult
}