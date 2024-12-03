package dev.paulee.api.data.search

import dev.paulee.api.data.RequiresData
import java.io.Closeable

interface SearchService : Closeable {

    fun init(dataInfo: RequiresData)

    fun search(source: String, query: String)
}