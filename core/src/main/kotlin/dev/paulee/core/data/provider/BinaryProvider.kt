package dev.paulee.core.data.provider

import dev.paulee.api.data.DataSource
import dev.paulee.api.data.RequiresData
import dev.paulee.api.data.Unique
import dev.paulee.api.data.provider.IStorageProvider
import java.io.RandomAccessFile
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.primaryConstructor

/*
    Binary Layout
    - Embedded files
        amount: Int |
            @forEach -> length: Int | name: String | id: Int | startIndex: Int | endIndex: Int

    - Data dictionary (dataStart)
        [Replaceable Values]
        size: Long
            @forEach -> size: Long | sourceID: Int  | lengthName: Int | name: String |
                @forEach -> lengthValue: Int | value: String

        [Unique Values]

        [Index]
 */

internal class BinaryProvider : IStorageProvider {

    private var binaryBlob: RandomAccessFile? = null

    private val fileIndices = mutableMapOf<String, Int>()

    private var dataStart: Long = 0

    override fun init(dataInfo: RequiresData, path: Path): Int {

        if (getPath(path, dataInfo.name).exists()) return 0

        this.binaryBlob = kotlin.runCatching {
            RandomAccessFile(getPath(path, dataInfo.name).toString(), "rw")
        }.getOrNull()

        if (this.binaryBlob == null) return -1

        if (!this.embedFiles(dataInfo.sources)) return -1

        this.binaryBlob?.writeLong(0)

        for (source in getValidEntries(dataInfo.sources)) source.primaryConstructor?.parameters.orEmpty()
            .forEach { this.embedParameter(this.getSourceName(source)!!, it) }

        this.binaryBlob?.run {
            val current = filePointer

            seek(dataStart)

            writeLong(current - dataStart)

            seek(current)
        }
        return 1
    }

    override fun insert(name: String, entry: List<Map<String, String>>) {
        TODO("Not yet implemented")
    }

    override fun get(
        name: String,
        ids: Set<Long>,
        whereClause: List<String>,
        filter: List<String>,
        offset: Int,
        limit: Int,
    ): List<Map<String, String>> {
        TODO("Not yet implemented")
    }

    override fun count(
        name: String,
        ids: Set<Long>,
        whereClause: List<String>,
        filter: List<String>,
    ): Long {
        TODO("Not yet implemented")
    }

    override fun close() {
        this.binaryBlob?.close()
    }

    private fun embedFiles(files: Array<KClass<*>>): Boolean {
        if (files.isEmpty()) return true

        return this.binaryBlob?.run {
            seek(0)

            writeInt(files.size)

            dataStart += Int.SIZE_BYTES
            getValidEntries(files).map { getSourceName(it)!! }.forEachIndexed { idx, str ->

                    writeInt(str.length)
                    writeBytes(str)
                    writeInt(idx).also { fileIndices[str] = idx }
                    writeInt(0)
                    writeInt(0)

                    dataStart += (Int.SIZE_BYTES * 4) + str.length
                }
            true
        } == true
    }

    private fun embedParameter(name: String, param: KParameter): Boolean {

        if (param.hasAnnotation<Unique>()) return true

        return this.binaryBlob?.run {
            val start = filePointer
            writeLong(0)

            writeInt(fileIndices[name]!!)
            writeInt(param.name!!.length)
            writeBytes(param.name!!)

            val current = filePointer

            seek(start)

            writeLong(current - start)

            seek(current)

            true
        } == true
    }

    private fun getSourceName(clazz: KClass<*>): String? {
        return clazz.findAnnotation<DataSource>()?.file
    }

    private fun getValidEntries(sources: Array<KClass<*>>): List<KClass<*>> =
        sources.filter { !this.getSourceName(it).isNullOrEmpty() }

    private fun getPath(path: Path, name: String): Path =
        path.resolve(name.plus(name.endsWith(".bin").let { if (it) "" else ".bin" }))
}