package dev.paulee.core.data.provider

import dev.paulee.api.data.RequiresData
import dev.paulee.api.data.provider.IStorageProvider
import dev.paulee.core.data.sql.Database
import java.nio.file.Path
import kotlin.io.path.exists

internal class SQLiteProvider : IStorageProvider {

    private lateinit var database: Database

    private var initialized = false

    private var lock = false

    override fun init(dataInfo: RequiresData, path: Path, lock: Boolean): Int {
        val dbPath = path.resolve("${dataInfo.name}.db")

        if (initialized) return -1

        val exists = dbPath.exists()

        this.database = Database(dbPath)

        runCatching { this.database.connect() }.getOrElse { return -1 }

        this.database.createTables(dataInfo.sources)

        this.initialized = true

        this.lock = lock

        return if (exists) 0 else 1
    }

    override fun insert(name: String, entries: List<Map<String, String>>) {
        if (!this.lock) this.database.insert(name, entries)
    }

    override fun get(
        name: String,
        ids: Set<Long>,
        whereClause: List<String>,
        filter: List<String>,
        offset: Int,
        limit: Int,
    ): List<Map<String, String>> {
        val entries = this.getEntries(name, ids, whereClause, filter) ?: return emptyList()
        return this.database.selectAll(name, entries, offset = offset, limit = limit)
    }

    override fun count(
        name: String,
        ids: Set<Long>,
        whereClause: List<String>,
        filter: List<String>,
    ): Long {
        val entries = this.getEntries(name, ids, whereClause, filter) ?: return 0
        return this.database.count(name, entries)
    }

    private fun getEntries(
        name: String,
        ids: Set<Long>,
        whereClause: List<String>,
        filter: List<String>,
    ): MutableMap<String, List<String>>? {
        var entries = whereClause.filter { it.contains(":") }.groupBy { it.substringBefore(":") }
            .mapValues { it.value.map { it.substringAfter(":") } }.toMutableMap()

        val primaryKey = this.database.primaryKeyOf(name) ?: return null

        if (ids.isNotEmpty()) entries += primaryKey to ids.map { it.toString() }.toList()

        if (filter.isNotEmpty()) {
            val groupedFilters = filter.filter { it.contains(":") }.groupBy { it.substringBefore(":") }
                .mapValues { it.value.map { it.substringAfter(":") } }.toMutableMap()

            if (entries.isEmpty()) return groupedFilters

            entries.replaceAll { key, values ->
                groupedFilters[key]?.let { filterValues -> values.filter { it in filterValues } } ?: values
            }

            entries.entries.removeAll { it.value.isEmpty() }

            if (entries.isEmpty()) return null
        }

        return entries
    }

    override fun close() {
        this.initialized = false
        this.database.close()
    }
}