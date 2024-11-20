package dev.paulee.api.data.provider

import dev.paulee.api.data.RequiresData
import java.io.Closeable

interface IStorageProvider : Closeable {

    fun init(dataInfo: RequiresData, path: String = ""): Boolean

    fun insert(entry: Map<String, String>)

}