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

class DataServiceImpl(val storageProvider: IStorageProvider) : IDataService {

    override fun createDataPool(dataInfo: RequiresData, path: Path): Boolean {

        if (!this.storageProvider.init(dataInfo, path.toString())) return true

        this.storageProvider.use {

            dataInfo.sources.map { it.java }.forEach { clazz ->

                val file = clazz.kotlin.findAnnotation<DataSource>()?.file

                if (file.isNullOrEmpty()) {
                    println("No data source provided for ${clazz.simpleName}")
                    return false
                }

                val sourcePath = path.resolve(file.plus(file.endsWith(".csv").let { if (it) "" else ".csv" }))

                if (!Files.exists(sourcePath)) {
                    println("Source file '$sourcePath' not found for ${clazz.simpleName}")
                    return false
                }

                sourcePath.bufferedReader().use { reader -> reader.lines().forEach { it.plus(",") } }
            }
            //clazz.kotlin.primaryConstructor?.parameters?.firstOrNull()?.annotations.orEmpty()
            //    .forEach { p0 -> println(p0) }
        }

        path.resolve("${dataInfo.name}.bin").deleteIfExists()

        return false
    }
}