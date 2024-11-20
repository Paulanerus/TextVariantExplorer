package dev.paulee.core.data.provider

import dev.paulee.api.data.RequiresData
import dev.paulee.api.data.provider.IStorageProvider
import java.io.RandomAccessFile
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists

class BinaryProvider : IStorageProvider {

    private var binaryBlob: RandomAccessFile? = null

    override fun init(dataInfo: RequiresData, path: String): Boolean {

        if (getPath(path, dataInfo.name).exists())
            return false

        binaryBlob = RandomAccessFile(getPath(path, dataInfo.name).toString(), "rw")

        return true
    }

    override fun insert(entry: Map<String, String>) {
        TODO("Not yet implemented")
    }

    override fun close() {
        this.binaryBlob?.close()
    }

    private fun getPath(path: String, name: String): Path =
        Path(path).resolve(name.plus(name.endsWith(".bin").let { if (it) "" else ".bin" }))
}