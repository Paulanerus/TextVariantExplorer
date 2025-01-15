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
        filter: List<String>,
        offset: Int,
        limit: Int,
    ): List<Map<String, String>> {
        val (tableName, entries) = this.getEntries(name, ids, whereClause, filter) ?: return emptyList()
        return dataSources[name.substringBefore(".")]!!.selectAll(tableName, entries, offset = offset, limit = limit)
    }

    override fun count(
        name: String,
        ids: Set<Long>,
        whereClause: List<String>,
        filter: List<String>,
    ): Long {
        val (tableName, entries) = this.getEntries(name, ids, whereClause, filter) ?: return 0
        return dataSources[name.substringBefore(".")]!!.count(tableName, entries)
    }

    private fun getEntries(
        name: String,
        ids: Set<Long>,
        whereClause: List<String>,
        filter: List<String>,
    ): Pair<String, MutableMap<String, List<String>>>? {
        val sourceName = name.substringBefore(".")
        val tableName = name.substringAfter(".")

        val db = dataSources[sourceName] ?: return null

        var entries = whereClause.filter { it.contains(":") }.groupBy { it.substringBefore(":") }
            .mapValues { it.value.map { it.substringAfter(":") } }.toMutableMap()

        val primaryKey = db.primaryKeyOf(tableName) ?: return null

        if (ids.isNotEmpty()) entries += primaryKey to ids.map { it.toString() }.toList()

        if (filter.isNotEmpty()) {
            val groupedFilters = filter.filter { it.contains(":") }.groupBy { it.substringBefore(":") }
                .mapValues { it.value.map { it.substringAfter(":") } }.toMutableMap()

            if (entries.isEmpty()) return tableName to groupedFilters

            entries.replaceAll { key, values ->
                groupedFilters[key]?.let { filterValues -> values.filter { it in filterValues } } ?: values
            }

            entries.entries.removeAll { it.value.isEmpty() }

            if (entries.isEmpty()) return null
        }

        return tableName to entries
    }

    override fun close() = dataSources.values.forEach { it.close() }
}