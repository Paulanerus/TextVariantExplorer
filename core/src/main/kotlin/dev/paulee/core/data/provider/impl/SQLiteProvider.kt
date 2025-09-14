package dev.paulee.core.data.provider.impl

import dev.paulee.api.data.DataInfo
import dev.paulee.api.data.provider.IStorageProvider
import dev.paulee.api.data.provider.ProviderStatus
import dev.paulee.api.data.provider.QueryOrder
import dev.paulee.core.data.sql.Database
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.exists

internal class SQLiteProvider : IStorageProvider {

    private val logger = LoggerFactory.getLogger(SQLiteProvider::class.java)

    private lateinit var database: Database

    private var initialized = false

    private var lock = false

    override fun init(dataInfo: DataInfo, path: Path, lock: Boolean): ProviderStatus {
        val dbPath = path.resolve("${dataInfo.name}.db")

        if (initialized) return ProviderStatus.FAILED

        val exists = dbPath.exists()

        this.database = Database(dbPath)

        runCatching { this.database.connect() }
            .getOrElse { e ->
                this.logger.error("Exception: Failed to connect to DB.", e)
                return ProviderStatus.FAILED
            }

        this.database.createTables(dataInfo.sources)

        this.initialized = true

        this.lock = lock

        this.logger.info("Initializing SQLStorageProvider (${dataInfo.name}, locked=$lock).")

        return if (exists) ProviderStatus.EXISTS else ProviderStatus.SUCCESS
    }

    override fun insert(name: String, entries: List<Map<String, String>>) {
        if (!this.lock) this.database.insert(name, entries)
        else this.logger.warn("Blocked insert on locked provider ($name).")
    }

    override fun get(
        name: String,
        ids: List<Long>,
        whereClause: List<String>,
        filter: List<String>,
        order: QueryOrder?,
        offset: Int,
        limit: Int,
    ): List<Map<String, String>> {
        val entries = this.getEntries(name, ids, whereClause, filter) ?: return emptyList()
        return this.database.selectAll(name, entries, order, offset = offset, limit = limit)
    }

    override fun count(
        name: String,
        ids: List<Long>,
        whereClause: List<String>,
        filter: List<String>,
    ): Long {
        val entries = this.getEntries(name, ids, whereClause, filter) ?: return 0
        return this.database.count(name, entries)
    }

    override fun suggestions(
        name: String,
        field: String,
        value: String,
        amount: Int
    ): List<String> {
        return this.database.suggestions(name, field, value, amount)
    }

    private fun getEntries(
        name: String,
        ids: List<Long>,
        whereClause: List<String>,
        filter: List<String>,
    ): MutableMap<String, List<String>>? {
        val entries = whereClause.filter { it.contains(":") }.groupBy { it.substringBefore(":") }
            .mapValues { entry -> entry.value.map { it.substringAfter(":") } }.toMutableMap()

        val primaryKey = this.database.primaryKeyOf(name)

        if (primaryKey == null) {
            this.logger.warn("Primary key for $name not found.")
            return null
        }

        if (ids.isNotEmpty()) entries += primaryKey to ids.map { it.toString() }.toList()

        if (filter.isNotEmpty()) {
            val groupedFilters = filter.filter { it.contains(":") }.groupBy { it.substringBefore(":") }
                .mapValues { entry -> entry.value.map { it.substringAfter(":") } }.toMutableMap()

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