package dev.paulee.core.data.provider

import dev.paulee.api.data.RequiresData
import dev.paulee.api.data.provider.IStorageProvider
import dev.paulee.core.data.sql.Database
import java.nio.file.Path
import kotlin.io.path.exists

internal class SQLiteProvider : IStorageProvider {

    private val dataSources = mutableMapOf<String, Database>()

    override fun init(dataInfo: RequiresData, path: Path): Int {
        val dbPath = path.resolve("${dataInfo.name}.db")

        if (dbPath.exists()) return 0

        val database = Database(dbPath)

        runCatching { database.connect() }.getOrElse { return -1 }

        database.createTables(dataInfo.sources)

        dataSources[dataInfo.name] = database

        return 1
    }

    override fun insert(name: String, entries: List<Map<String, String>>) {
        val sourceName = name.substringBefore(".")
        val tableName = name.substringAfter(".")

        val table = dataSources[sourceName] ?: return

        table.insert(tableName, entries)
    }

    override fun close() = dataSources.values.forEach { it.close() }
}