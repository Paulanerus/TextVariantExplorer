package dev.paulee.core.data

import dev.paulee.api.data.DataSource
import dev.paulee.api.data.IDataService
import dev.paulee.api.data.RequiresData
import dev.paulee.api.data.provider.IStorageProvider
import dev.paulee.core.data.io.BufferedCSVReader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.reflect.full.findAnnotation
import kotlin.system.measureTimeMillis

class DataServiceImpl(private val storageProvider: IStorageProvider) : IDataService {

    override fun createDataPool(dataInfo: RequiresData, path: Path): Boolean {

        val initStatus = this.storageProvider.init(dataInfo, path)

        if (initStatus < 1) return initStatus == 0

        this.storageProvider.use {

            dataInfo.sources.forEach { clazz ->

                val file = clazz.findAnnotation<DataSource>()?.file

                if (file.isNullOrEmpty()) {
                    println("No data source provided for ${clazz.simpleName}")
                    return@forEach
                }

                val time = measureTimeMillis {
                    val sourcePath = path.resolve(file.plus(file.endsWith(".csv").let { if (it) "" else ".csv" }))

                    if (!Files.exists(sourcePath)) {
                        println("Source file '$sourcePath' not found")
                        return@forEach
                    }

                    BufferedCSVReader(sourcePath).readLines { this.storageProvider.insert(it) }
                }

                println("Loaded ${clazz.simpleName} in ${time}ms")
            }
        }
        return true
    }
}