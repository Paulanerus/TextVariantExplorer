package dev.paulee.core.data.provider

import dev.paulee.api.data.DataSource
import dev.paulee.api.data.NullValue
import dev.paulee.api.data.RequiresData
import dev.paulee.api.data.Unique
import dev.paulee.api.data.provider.IStorageProvider
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.primaryConstructor

class SQLiteProvider : IStorageProvider {

    private val tableCache = mutableMapOf<String, Table>()

    override fun init(dataInfo: RequiresData, path: Path): Int {
        val db = Database.connect("jdbc:sqlite:$path/${dataInfo.name}.db", "org.sqlite.JDBC")
        TransactionManager.defaultDatabase = db

        dataInfo.sources.forEach { clazz ->
            val tableName = clazz.findAnnotation<DataSource>()?.file ?: return@forEach

            val primaryKey =
                clazz.primaryConstructor?.parameters.orEmpty()
                    .find { prop -> prop.type.classifier == Long::class && prop.findAnnotation<Unique>()?.identify == true }?.name
                    ?: "${tableName}_ag_id"

            tableCache[tableName] = this.generateTableObject(tableName, primaryKey, clazz)
                .also { table ->
                    transaction {
                        SchemaUtils.create(table)
                    }
                }
        }

        return tableCache.values.any { transaction { it.selectAll().count() } == 0L }.let { if (it) 1 else 0 }
    }

    override fun insert(name: String, entries: List<Map<String, String>>) {
        val table = tableCache[name] ?: return


        entriesToInsertQuery(table, entries)?.let {
            transaction {
                exec(it)
            }
        }
    }

    override fun close() {}

    private fun entriesToInsertQuery(table: Table, entries: List<Map<String, String>>): String? {
        val tableNames = table.columns.map { it.name }

        val header = entries.first().keys.filter { it in tableNames }

        if (header.size != tableNames.size) return null

        val headerStr = header.joinToString(",")

        var values = mutableListOf<String>()
        entries.forEach {
            values.add("(${header.map { name -> it[name] }.joinToString(",") { "'${it?.replace("'", "''")}'" }})")
        }

        return "INSERT INTO ${table.tableName} ($headerStr) VALUES ${values.joinToString(",")}"
    }

    private fun generateTableObject(tableName: String, keyColumn: String, clazz: KClass<*>): Table =
        object : Table(tableName) {

            val id = long(keyColumn)

            init {
                clazz.primaryConstructor?.parameters.orEmpty().filter { it.findAnnotation<Unique>()?.identify != true }
                    .forEach { prop ->
                        val name = prop.name ?: return@forEach

                        val type = prop.type
                        val column = when (type.classifier) {
                            String::class -> text(name)
                            Char::class -> char(name)
                            Short::class -> short(name)
                            Int::class -> integer(name)
                            Long::class -> long(name)
                            Float::class -> float(name)
                            Double::class -> double(name)
                            Boolean::class -> bool(name)
                            else -> throw IllegalArgumentException("Unsupported type: ${type.classifier}")
                        }

                        if (prop.hasAnnotation<NullValue>())
                            column.nullable()
                    }
            }

            override val primaryKey = PrimaryKey(id)
        }
}