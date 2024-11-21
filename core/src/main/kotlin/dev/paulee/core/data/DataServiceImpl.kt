package dev.paulee.core.data

import dev.paulee.api.data.DataSource
import dev.paulee.api.data.IDataService
import dev.paulee.api.data.RequiresData
import dev.paulee.api.data.provider.IStorageProvider
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.deleteIfExists
import kotlin.reflect.full.findAnnotation

class DataServiceImpl(private val storageProvider: IStorageProvider) : IDataService {

    override fun createDataPool(dataInfo: RequiresData, path: Path): Boolean {

        val initStatus = this.storageProvider.init(dataInfo, path.toString())

        if (initStatus < 1) return initStatus == 0

        this.storageProvider.use {

            dataInfo.sources.map { it.java }.forEach { clazz ->

                val file = clazz.kotlin.findAnnotation<DataSource>()?.file

                if (file.isNullOrEmpty()) {
                    println("No data source provided for ${clazz.simpleName}")
                    return@forEach
                }

                val sourcePath = path.resolve(file.plus(file.endsWith(".csv").let { if (it) "" else ".csv" }))

                if (!Files.exists(sourcePath)) {
                    println("Source file '$sourcePath' not found for ${clazz.simpleName}")
                    return@forEach
                }

                sourcePath.bufferedReader().use { reader -> reader.lines().forEach { it } }
            }
        }

        path.resolve("${dataInfo.name}.bin").deleteIfExists()

        return true
    }
}