package dev.paulee.core.data.sql

import dev.paulee.api.data.FieldType
import dev.paulee.api.data.Source
import dev.paulee.api.data.UniqueField
import dev.paulee.api.data.provider.QueryOrder
import dev.paulee.core.normalizeDataSource
import dev.paulee.core.normalizeSourceName
import dev.paulee.core.sha1Hex
import dev.paulee.core.splitStr
import org.duckdb.DuckDBConnection
import org.duckdb.DuckDBDriver
import org.slf4j.LoggerFactory.getLogger
import java.io.Closeable
import java.nio.file.Path
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Types
import java.util.*
import kotlin.io.path.bufferedReader
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.notExists

private enum class ColumnType {
    TEXT, INTEGER, REAL
}

private fun typeToColumnType(type: FieldType): ColumnType = when (type) {
    FieldType.TEXT -> ColumnType.TEXT
    FieldType.INT -> ColumnType.INTEGER
    FieldType.FLOAT -> ColumnType.REAL
    FieldType.BOOLEAN -> ColumnType.TEXT
}

private data class Column(
    var name: String,
    val type: ColumnType,
    val primary: Boolean,
    val nullable: Boolean,
) {
    override fun toString(): String = "$name $type ${if (primary) "PRIMARY KEY" else if (nullable) "" else "NOT NULL"}"
}

private data class WhereClause(val statement: String, val unresolved: Map<String, List<String>>)

private data class LinkRef(val source: String, val field: String)

private class Table(val name: String, val columns: List<Column>) {

    val primaryKey: Column = columns.find { it.primary } ?: Column(
        "${name}_ag_id", ColumnType.INTEGER, primary = true, nullable = false
    )

    val orderedColumns = listOf(primaryKey) + columns.filter { !it.primary }

    val tempTables = mutableMapOf<String, String>()

    val references = mutableMapOf<LinkRef, Table>()

    fun import(connection: DuckDBConnection, path: Path, hasId: Boolean) {
        val readCsvStmt = buildString {
            append("read_csv('$path', header = true, columns = {")
            append(columns.joinToString(", ") { column ->
                "'${column.name}': '${column.type}'"
            })
            append("})")
        }

        val query = buildString {
            append("CREATE TABLE IF NOT EXISTS ")
            append(name)

            if (hasId)
                append(" AS SELECT * FROM $readCsvStmt;")
            else
                append(" AS SELECT CAST(row_number() OVER () - 1 AS INTEGER) AS ${name}_ag_id, t.* FROM (SELECT * FROM $readCsvStmt) AS t;")
        }

        connection.createStatement().use {
            it.execute(query)

            orderedColumns.mapNotNull { column ->
                val name = column.name
                val normalized = normalizeSourceName(name)

                if (name != normalized) {
                    column.name = normalized
                    name to normalized
                } else null
            }.forEach { (old, new) ->
                it.execute("ALTER TABLE $name RENAME COLUMN \"$old\" TO $new;")
            }
        }
    }

    fun selectAll(
        connection: DuckDBConnection,
        whereClause: Map<String, List<String>> = emptyMap(),
        order: QueryOrder?,
        offset: Int = 0,
        limit: Int = Int.MAX_VALUE,
    ): List<Map<String, String>> {
        val (statement, unresolved) = buildWhereClause(connection, whereClause)

        val query = buildString {
            append("SELECT * FROM ")
            append(name)

            append(statement)

            order?.takeIf { it.first.isNotBlank() }?.let {
                append(" ORDER BY ")
                append(order.first)

                if (order.second) append(" DESC")
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
                    val row = orderedColumns.associate { column -> column.name to (it.getString(column.name) ?: "") }

                    results.add(row)
                }

                results
            }
        }
    }

    fun count(connection: DuckDBConnection, whereClause: Map<String, List<String>> = emptyMap()): Long {
        val (statement, unresolved) = buildWhereClause(connection, whereClause)

        val query = buildString {
            append("SELECT COUNT(*) FROM ")
            append(name)

            append(statement)
        }

        return connection.createStatement().use { statement ->
            statement.executeQuery(query).use { if (it.next()) it.getLong(1) else -1L }
        }
    }

    fun suggestions(connection: DuckDBConnection, field: String, value: String, amount: Int): List<String> {
        val query = buildString {
            append("SELECT DISTINCT ")
            append(field)
            append(" FROM ")
            append(name)

            append(" WHERE ")
            append(field)
            append(" LIKE '")
            append(value.replaceWildCard())
            append("%' ESCAPE '\\'")

            append(" LIMIT ")
            append(amount)
        }

        return connection.createStatement().use { statement ->
            statement.executeQuery(query).use {
                val results = mutableListOf<String>()

                while (it.next()) results.add(it.getString(1))

                results
            }
        }
    }

    fun getColumnType(name: String): ColumnType? = orderedColumns.find { it.name == name }?.type

    override fun toString(): String = "$name primary=${primaryKey}, columns={${orderedColumns.joinToString(", ")}}"

    private fun buildWhereClause(connection: DuckDBConnection, whereClause: Map<String, List<String>>): WhereClause {
        if (whereClause.isEmpty()) return WhereClause("", emptyMap())

        val (resolvedEntries, unresolvedEntries) = whereClause.entries.partition { getColumnType(it.key) != null }

        val unresolved = unresolvedEntries.associate { it.toPair() }

        val parts = resolvedEntries.joinToString(" AND ") { (column, values) ->
            val columnType = getColumnType(column) ?: return@joinToString ""

            val columnName = "$name.$column"

            val (wildcards, nonWildcards) = values.distinct().partition { it.hasWildcard() }

            val cause = buildString {
                if (nonWildcards.isNotEmpty()) {
                    if (nonWildcards.size == 1) {
                        val value = nonWildcards.first()

                        append("$columnName = ${if (columnType == ColumnType.TEXT) "'${value.escapeLiteral()}'" else value}")
                    } else {

                        if (nonWildcards.size > 500) {
                            val hash = sha1Hex(
                                (listOf(name, columnType.name, columnName) + nonWildcards).joinToString("")
                            )

                            val tempQuery = tempTables.getOrPut(hash) {
                                createAndUpdateTempTable(connection, hash, nonWildcards, columnType)
                            }

                            append("$columnName IN ($tempQuery)")
                        } else {
                            val inClause = nonWildcards.joinToString(
                                ", ", prefix = "IN (", postfix = ")"
                            ) { if (columnType == ColumnType.TEXT) "'${it.escapeLiteral()}'" else it }

                            append("$columnName $inClause")
                        }
                    }

                    if (wildcards.isNotEmpty()) append(" OR ")
                }

                if (columnType == ColumnType.TEXT) {
                    wildcards.takeIf { it.isNotEmpty() }
                        ?.joinToString(" OR ") { "$columnName LIKE '${it.replaceWildCard()}' ESCAPE '\\'" }
                        ?.let { append(it) }
                }
            }

            if (cause.isNotBlank()) "($cause)" else ""
        }

        return WhereClause(if (parts.isEmpty()) "" else " WHERE $parts", unresolved)
    }

    private fun String.escapeLiteral() = this.replace("'", "''")

    private fun String.hasWildcard(): Boolean = this.contains('*') || this.contains('?')

    private fun String.replaceWildCard(): String = this
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
        .replace("*", "%")
        .replace("?", "_")
        .escapeLiteral()

    private fun createAndUpdateTempTable(
        connection: DuckDBConnection,
        hash: String,
        values: List<String>,
        type: ColumnType,
    ): String {
        val tempName = "tmp_$hash"

        connection.createStatement().use {
            it.execute("CREATE TEMP TABLE $tempName (v $type)")
        }

        values.chunked(500).forEach { chunk ->
            val placeholders = List(chunk.size) { "(?)" }.joinToString(", ")
            val sql = "INSERT INTO $tempName(v) VALUES $placeholders"

            connection.prepareStatement(sql).use { ps ->
                var paramIndex = 1

                when (type) {
                    ColumnType.TEXT -> {
                        for (v in chunk)
                            ps.setString(paramIndex++, v)
                    }

                    ColumnType.INTEGER -> {
                        for (v in chunk) {
                            val longVal = v.toLongOrNull()
                            if (longVal == null) ps.setNull(paramIndex++, Types.INTEGER)
                            else ps.setLong(paramIndex++, longVal)
                        }
                    }

                    ColumnType.REAL -> {
                        for (v in chunk) {
                            val dblVal = v.toDoubleOrNull()
                            if (dblVal == null) ps.setNull(paramIndex++, Types.REAL)
                            else ps.setDouble(paramIndex++, dblVal)
                        }
                    }
                }

                ps.executeUpdate()
            }
        }

        return "SELECT v FROM $tempName"
    }
}

internal class Database(private val path: Path) : Closeable {

    private val dbPath = "jdbc:duckdb:$path"

    private var connection: DuckDBConnection? = null

    private val tables = mutableSetOf<Table>()

    val dbFileExists = path.exists()

    private val linkRefs = mutableMapOf<String, MutableList<LinkRef>>()

    companion object {

        private val logger = getLogger(Database::class.java)

        private val hasNoSQLModule: Boolean

        init {
            val sqlModule = ModuleLayer.boot().findModule("java.sql")

            this.hasNoSQLModule = sqlModule.isEmpty

            if (hasNoSQLModule) logger.error("The required java.sql module is missing.")
            else logger.info("Found java.sql module.")
        }
    }

    init {
        path.parent.createDirectories()
    }

    fun connect() {
        if (hasNoSQLModule || this.connection != null) return

        val props = Properties().apply {
            setProperty(DuckDBDriver.JDBC_STREAM_RESULTS, "true")
        }

        this.connection = DriverManager.getConnection(dbPath, props) as DuckDBConnection
    }

    fun streamData(name: String): Sequence<LinkedHashMap<String, String>> {
        if (hasNoSQLModule || connection == null) return emptySequence()

        tables.find { it.name == name } ?: return emptySequence()

        return sequence {
            connection!!.prepareStatement(
                "SELECT * FROM $name",
                ResultSet.TYPE_FORWARD_ONLY,
                ResultSet.CONCUR_READ_ONLY
            )
                .use {
                    it.executeQuery().use { rs ->
                        while (rs.next()) yield(mapRow(rs))
                    }
                }
        }
    }

    fun importAll(sources: List<Source>) {
        sources.forEach { import(it) }

        linkRefs.forEach { (targetName, refs) ->
            val target = tables.firstOrNull { it.name == targetName } ?: return@forEach

            refs.forEach { ref ->
                val source = tables.firstOrNull { it.name == ref.source } ?: return@forEach

                val type = source.getColumnType(ref.field)

                if (type == null || type != target.getColumnType(ref.field)) {
                    logger.error("Source field '${ref.field}' not found")
                    return@forEach
                }

                target.references[ref] = source
            }
        }
    }

    private fun import(source: Source) {
        if (hasNoSQLModule || connection == null) return

        val sourcePath = path.parent?.resolve("data")?.resolve("${source.name}.csv")

        if (sourcePath == null || sourcePath.notExists()) {
            logger.error("Source path '$sourcePath' not found")
            return
        }

        val headerMap = HashMap<String, String>(source.fields.size)

        if (!dbFileExists) {
            runCatching { sourcePath.bufferedReader().use { it.readLine() } }
                .getOrNull()
                ?.let { header ->
                    splitStr(header, delimiter = ',')
                        .forEach {
                            headerMap[normalizeSourceName(it)] = it
                        }
                }
        }

        val normalizedDataSource = normalizeDataSource(source.name)

        val columns = source.fields.map { field ->
            val isNullable = false //param.hasAnnotation<Nullable>()

            val isPrimary = (field is UniqueField) // && !isNullable

            if (field.sourceLink.isNotBlank()) {
                linkRefs.getOrPut(field.sourceLink) { mutableListOf() }
                    .add(LinkRef(normalizedDataSource, field.name))
            }

            Column(headerMap[field.name] ?: field.name, typeToColumnType(field.fieldType), isPrimary, isNullable)
        }

        val table = Table(normalizedDataSource, columns)
        tables.add(table)

        val hasId = source.fields.any { it is UniqueField && it.identify }

        transaction {
            table.import(this, sourcePath, hasId)
        }
    }

    fun primaryKeyOf(name: String): String? = tables.find { it.name == name }?.primaryKey?.name

    fun selectAll(
        name: String,
        whereClause: Map<String, List<String>> = emptyMap(),
        order: QueryOrder?,
        offset: Int = 0,
        limit: Int = Int.MAX_VALUE,
    ): List<Map<String, String>> {
        if (hasNoSQLModule) return emptyList()

        val table = tables.find { it.name == name } ?: return emptyList()

        return runCatching {
            transaction {
                table.selectAll(this, whereClause, order, offset, limit)
            }
        }.getOrElse { e ->
            logger.error("Exception: Failed to retrieve entries from table '$name' due to an unexpected error.", e)
            emptyList()
        }
    }

    fun count(name: String, whereClause: Map<String, List<String>> = emptyMap()): Long {
        if (hasNoSQLModule) return -1L

        val table = tables.find { it.name == name } ?: return -1L

        return runCatching {
            transaction {
                table.count(this, whereClause)
            }
        }.getOrElse { e ->
            logger.error("Exception: Failed to count entries in table '$name' due to an unexpected error.", e)
            0
        }
    }

    fun suggestions(name: String, field: String, value: String, amount: Int): List<String> {
        if (hasNoSQLModule) return emptyList()

        val table = tables.find { it.name == name } ?: return emptyList()

        return runCatching {
            transaction {
                table.suggestions(this, field, value, amount)
            }
        }.getOrElse { e ->
            logger.error("Exception: Failed to retrieve suggestions from table '$name' due to an unexpected error.", e)
            emptyList()
        }
    }

    fun <T> transaction(block: DuckDBConnection.() -> T): T {
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

    private fun mapRow(rs: ResultSet): LinkedHashMap<String, String> {
        val metadata = rs.metaData

        val map = LinkedHashMap<String, String>(metadata.columnCount)

        for (i in 1..metadata.columnCount)
            map[metadata.getColumnLabel(i)] = (rs.getObject(i) ?: "").toString()

        return map
    }
}
