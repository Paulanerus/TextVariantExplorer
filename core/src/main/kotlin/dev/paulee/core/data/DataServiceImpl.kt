package dev.paulee.core.data

import dev.paulee.api.data.DataSource
import dev.paulee.api.data.IDataService
import dev.paulee.api.data.RequiresData
import dev.paulee.api.data.Unique
import dev.paulee.api.data.provider.IStorageProvider
import dev.paulee.core.data.analysis.Indexer
import dev.paulee.core.data.io.BufferedCSVReader
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor

class DataServiceImpl(private val storageProvider: IStorageProvider) : IDataService {

    override fun createDataPool(dataInfo: RequiresData, path: Path): Boolean {
        val initStatus = this.storageProvider.init(dataInfo, path)

        if (initStatus < 1) return initStatus == 0

        this.storageProvider.use {
            val indexer =
                runCatching {
                    Indexer(
                        path.resolve("index/${dataInfo.name}"),
                        dataInfo.sources
                    )
                }.getOrElse { exception ->
                    println("Failed to create index for ${dataInfo.name}")
                    return false
                }

            dataInfo.sources.forEach { clazz ->
                val file = clazz.findAnnotation<DataSource>()?.file

                if (file.isNullOrEmpty()) {
                    println("No data source provided for ${clazz.simpleName}")
                    return@forEach
                }

                val sourcePath = path.resolve(file.let { if (it.endsWith(".csv")) it else "$it.csv" })

                if (!sourcePath.exists()) {
                    println("Source file '$sourcePath' not found")
                    return@forEach
                }

                val hasIdentifier =
                    clazz.primaryConstructor?.parameters.orEmpty().any { it.findAnnotation<Unique>()?.identify == true }

                val idGenerator = generateSequence(1L) { it + 1 }.iterator()

                BufferedCSVReader(sourcePath).readLines { lines ->
                    val entries = lines.map { line ->
                        if (hasIdentifier) line
                        else line + ("${file}_ag_id" to idGenerator.next().toString())
                    }

                    indexer.indexEntries(file, entries)
                    this.storageProvider.insert(file, entries)
                }
            }
            indexer.close()
        }
        return true
    }
}