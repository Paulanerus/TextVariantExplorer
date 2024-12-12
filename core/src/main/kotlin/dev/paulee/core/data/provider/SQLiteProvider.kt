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

        val exists = dbPath.exists()

        val database = Database(dbPath)

        runCatching { database.connect() }.getOrElse { return -1 }

        database.createTables(dataInfo.sources)

        dataSources[dataInfo.name] = database

        return if (exists) 0 else 1
    }

    override fun insert(name: String, entries: List<Map<String, String>>) {
        val sourceName = name.substringBefore(".")
        val tableName = name.substringAfter(".")

        val db = dataSources[sourceName] ?: return

        db.insert(tableName, entries)
    }

    override fun get(
        name: String,
        ids: Set<Long>,
        whereClause: List<String>,
        offset: Int,
        limit: Int
    ): List<Map<String, String>> {
        val sourceName = name.substringBefore(".")
        val tableName = name.substringAfter(".")

        val db = dataSources[sourceName] ?: return emptyList()

        var entries = whereClause.filter { it.contains(":") }.groupBy { it.substringBefore(":") }
            .mapValues { it.value.map { it.substringAfter(":") } }.toMutableMap()

        val primaryKey = db.primaryKeyOf(tableName) ?: return emptyList()

        if (ids.isNotEmpty()) entries += primaryKey to ids.map { it.toString() }.toList()

        return db.selectAll(tableName, entries, offset = offset, limit = limit)
    }

    override fun count(
        name: String,
        ids: Set<Long>,
        whereClause: List<String>
    ): Long {
        val sourceName = name.substringBefore(".")
        val tableName = name.substringAfter(".")

        val db = dataSources[sourceName] ?: return 0

        var entries = whereClause.filter { it.contains(":") }.groupBy { it.substringBefore(":") }
            .mapValues { it.value.map { it.substringAfter(":") } }.toMutableMap()

        val primaryKey = db.primaryKeyOf(tableName) ?: return 0

        if (ids.isNotEmpty()) entries += primaryKey to ids.map { it.toString() }.toList()

        return db.count(tableName, entries)
    }

    override fun close() = dataSources.values.forEach { it.close() }
}