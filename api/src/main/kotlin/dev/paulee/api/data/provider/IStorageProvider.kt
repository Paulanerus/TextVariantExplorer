package dev.paulee.api.data.provider

import dev.paulee.api.data.RequiresData
import java.io.Closeable

interface IStorageProvider : Closeable {

    fun init(dataInfo: RequiresData, path: String = ""): Int

    fun insert(entry: Map<String, String>)

}