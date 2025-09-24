package dev.paulee.core.data.provider.impl

import dev.paulee.api.data.DataInfo
import dev.paulee.api.data.provider.IStorageProvider
import dev.paulee.api.data.provider.ProviderStatus
import dev.paulee.api.data.provider.QueryOrder
import dev.paulee.core.data.sql.Database
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.LinkedHashMap
import kotlin.io.path.exists

internal class DefaultProvider : IStorageProvider {

    private val logger = LoggerFactory.getLogger(DefaultProvider::class.java)

    private lateinit var database: Database

    private var initialized = false

    override fun init(dataInfo: DataInfo, path: Path): ProviderStatus {
        val dbPath = path.resolve("${dataInfo.name}.duckdb")

        if (initialized) return ProviderStatus.Failed

        val exists = dbPath.exists()

        this.database = Database(dbPath)

        runCatching { this.database.connect() }
            .getOrElse { e ->
                this.logger.error("Exception: Failed to connect to DB.", e)
                return ProviderStatus.Failed
            }

        dataInfo.sources.forEach(database::import)

        this.initialized = true

        this.logger.info("Initializing SQLStorageProvider (${dataInfo.name}).")

        return if (exists) ProviderStatus.Exists else ProviderStatus.Success
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
        amount: Int,
    ): List<String> {
        return this.database.suggestions(name, field, value, amount)
    }

    override fun streamData(name: String): Sequence<LinkedHashMap<String, String>> {
        if (name.isBlank()) return emptySequence()

        return database.streamData(name)
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