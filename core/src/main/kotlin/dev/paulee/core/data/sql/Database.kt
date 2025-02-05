package dev.paulee.core.data.sql

import dev.paulee.api.data.DataSource
import dev.paulee.api.data.Unique
import dev.paulee.core.Logger
import dev.paulee.core.normalizeDataSource
import org.jetbrains.annotations.Nullable
import java.io.Closeable
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import kotlin.io.path.createDirectories
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.primaryConstructor

private enum class ColumnType {
    TEXT, INTEGER, REAL, NUMERIC, UNKNOWN
}

private fun typeToColumnType(type: KClassifier): ColumnType = when (type) {
    String::class -> ColumnType.TEXT
    Char::class -> ColumnType.TEXT
    Short::class -> ColumnType.INTEGER
    Int::class -> ColumnType.INTEGER
    Long::class -> ColumnType.INTEGER
    Float::class -> ColumnType.REAL
    Double::class -> ColumnType.REAL
    Boolean::class -> ColumnType.NUMERIC
    else -> ColumnType.UNKNOWN
}

private data class Column(val name: String, val type: ColumnType, val primary: Boolean, val nullable: Boolean) {
    override fun toString(): String = "$name $type ${if (primary) "PRIMARY KEY" else if (nullable) "" else "NOT NULL"}"
}

private class Table(val name: String, columns: List<Column>) {

    val primaryKey: Column = columns.find { it.primary } ?: Column("${name}_ag_id", ColumnType.INTEGER, true, false)

    val columns = listOf(primaryKey) + columns.filter { !it.primary }

    fun createIfNotExists(connection: Connection) {
        connection.createStatement().use {
            it.execute("CREATE TABLE IF NOT EXISTS $name (${columns.joinToString(", ")})")
        }
    }

    fun insert(connection: Connection, entries: List<Map<String, String>>) {

        if (entries.isEmpty()) return

        val placeholders =
            List(entries.size) { List(columns.size) { "?" }.joinToString(", ", prefix = "(", postfix = ")") }

        val query = buildString {
            append("INSERT INTO ")
            append(name)
            append(" (")
            append(columns.joinToString(", ") { it.name })
            append(") VALUES ")
            append(placeholders.joinToString(", "))
        }

        connection.prepareStatement(query).use {
            val size = columns.size
            entries.forEachIndexed { index, map ->
                columns.forEachIndexed inner@ { idx, column ->
                    val value = map[column.name] ?: return@inner

                    it.setString((size * index) + idx + 1, value)
                }
            }
            it.executeUpdate()
        }
    }

    fun selectAll(
        connection: Connection,
        whereClause: Map<String, List<String>> = emptyMap<String, List<String>>(),
        offset: Int = 0,
        limit: Int = Int.MAX_VALUE,
    ): List<Map<String, String>> {
        val query = buildString {
            append("SELECT * FROM ")
            append(name)

            if (whereClause.isNotEmpty()) {
                val clause = whereClause.entries.filter { getColumnType(it.key) != null }
                    .joinToString(" AND ") { (column, values) ->
                        val columnType = getColumnType(column) ?: return@joinToString ""

                        val inClause = values.joinToString(
                            ", ", prefix = "IN (", postfix = ")"
                        ) { if (columnType == ColumnType.TEXT) "'$it'" else it }

                        "$column $inClause"
                    }

                if (clause.isNotEmpty()) {
                    append(" WHERE ")
                    append(clause)
                }
            }

            append(" LIMIT ")
            append(limit)

            append(" OFFSET ")
            append(offset)
        }

        return connection.createStatement().use { statement ->
            statement.executeQuery(query).use {
                val results = mutableListOf<Map<String, String>>()

                while (it.next()) {
                    val row = columns.associate { column -> column.name to (it.getString(column.name) ?: "") }

                    results.add(row)
                }

                results
            }
        }
    }

    fun count(connection: Connection, whereClause: Map<String, List<String>> = emptyMap<String, List<String>>()): Long {
        val query = buildString {
            append("SELECT COUNT(*) FROM ")
            append(name)

            if (whereClause.isNotEmpty()) {
                val clause = whereClause.entries.filter { getColumnType(it.key) != null }
                    .joinToString(" AND ") { (column, values) ->
                        val columnType = getColumnType(column) ?: return@joinToString ""

                        val inClause = values.joinToString(
                            ", ", prefix = "IN (", postfix = ")"
                        ) { if (columnType == ColumnType.TEXT) "'$it'" else it }

                        "$column $inClause"
                    }

                if (clause.isNotEmpty()) {
                    append(" WHERE ")
                    append(clause)
                }
            }
        }

        return connection.createStatement().use { statement ->
            statement.executeQuery(query).use { if (it.next()) it.getLong(1) else -1L }
        }
    }

    fun getColumnType(name: String): ColumnType? = columns.find { it.name == name }?.type

    override fun toString(): String = "$name primary=${primaryKey}, columns={${columns.joinToString(", ")}}"
}

internal class Database(path: Path) : Closeable {

    private val dbPath = "jdbc:sqlite:$path"

    private var connection: Connection? = null

    private val tables = mutableSetOf<Table>()

    companion object {

        private val logger = Logger.getLogger("Database")

        private val hasNoSQLModule: Boolean

        init {
            val sqlModule = ModuleLayer.boot().findModule("java.sql")

            this.hasNoSQLModule = sqlModule.isEmpty

            if(hasNoSQLModule) logger.error("The required java.sql module is missing.")
            else logger.info("Found java.sql module.")
        }
    }

    init {
        path.parent.createDirectories()
    }

    fun connect() {
        if (hasNoSQLModule || this.connection != null) return

        this.connection = DriverManager.getConnection(dbPath)
    }

    fun createTables(klasses: Array<KClass<*>>) {
        klasses.forEach { createTable(it) }
    }

    fun insert(name: String, entries: List<Map<String, String>>) {
        val table = tables.find { it.name == name } ?: return

        table.insert(this.connection ?: return, entries)
    }

    fun createTable(klass: KClass<*>) {
        if(hasNoSQLModule) return

        val tableName = klass.findAnnotation<DataSource>()?.file ?: return

        val columns = klass.primaryConstructor?.parameters.orEmpty().mapNotNull { param ->
            val name = param.name ?: return@mapNotNull null
            val type = param.type.classifier ?: return@mapNotNull null

            val isNullable = param.hasAnnotation<Nullable>()

            val isPrimary = param.hasAnnotation<Unique>() && !isNullable

            Column(name, typeToColumnType(type), isPrimary, isNullable)
        }

        val table = Table(normalizeDataSource(tableName), columns)

        runCatching {
            transaction {
                table.createIfNotExists(this)

                tables.add(table)
            }
        }.getOrElse { e -> logger.exception(e) }
    }

    fun primaryKeyOf(name: String): String? = tables.find { it.name == name }?.primaryKey?.name

    fun selectAll(
        name: String,
        whereClause: Map<String, List<String>> = emptyMap(),
        offset: Int = 0,
        limit: Int = Int.MAX_VALUE,
    ): List<Map<String, String>> {
        if(hasNoSQLModule) return emptyList()

        val table = tables.find { it.name == name } ?: return emptyList()

        return runCatching {
            transaction {
                table.selectAll(this, whereClause, offset, limit)
            }
        }.getOrElse { e ->
            logger.exception(e)
            emptyList()
        }
    }

    fun count(name: String, whereClause: Map<String, List<String>> = emptyMap()): Long {
        if(hasNoSQLModule) return -1L

        val table = tables.find { it.name == name } ?: return -1L

        return runCatching {
            transaction {
                table.count(this, whereClause)
            }
        }.getOrElse { e ->
            logger.exception(e)
            0
        }
    }

    fun <T> transaction(block: Connection.() -> T): T {
        val conn = this.connection ?: throw IllegalStateException("Failed to start transaction: Connection is null.")

        conn.autoCommit = false

        return try {
            runCatching { conn.block() }.onSuccess {
                conn.commit()
            }.onFailure {
                conn.rollback()
            }.getOrThrow()
        } finally {
            conn.autoCommit = true
        }
    }

    override fun close() {
        this.connection?.close()
    }
}