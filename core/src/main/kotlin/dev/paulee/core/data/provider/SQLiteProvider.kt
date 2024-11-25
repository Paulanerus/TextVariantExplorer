package dev.paulee.core.data.provider

import dev.paulee.api.data.DataSource
import dev.paulee.api.data.NullValue
import dev.paulee.api.data.RequiresData
import dev.paulee.api.data.Unique
import dev.paulee.api.data.provider.IStorageProvider
import org.jetbrains.exposed.dao.id.ULongIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberProperties

class SQLiteProvider : IStorageProvider {

    private val tableCache = mutableMapOf<String, ULongIdTable>()

    override fun init(dataInfo: RequiresData, path: Path): Int {

        val db = Database.connect("jdbc:sqlite:$path/${dataInfo.name}.db", "org.sqlite.JDBC")
        TransactionManager.defaultDatabase = db

        dataInfo.sources.forEach {
            val tableName = it.findAnnotation<DataSource>()?.file ?: return@forEach

            tableCache[tableName] = generateTableObject(tableName, it)
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

    private fun entriesToInsertQuery(table: ULongIdTable, entries: List<Map<String, String>>): String? {
        val tableNames = table.columns.map { it.name }

        val header = entries.first().keys.filter { it in tableNames }

        if (header.size != tableNames.size - 1) return null

        val headerStr = header.joinToString(",")

        var values = mutableListOf<String>()
        entries.forEach {
            values.add("(${header.map { name -> it[name] }.joinToString(",") { "'${it?.replace("'", "''")}'" }})")
        }

        return "INSERT INTO ${table.tableName} ($headerStr) VALUES ${values.joinToString(",")}"
    }

    private fun generateTableObject(tableName: String, clazz: KClass<*>): ULongIdTable =
        object : ULongIdTable(tableName) {
            init {
                clazz.memberProperties.forEach { prop ->
                    val type = prop.returnType
                    val column = when (type.classifier) {
                        String::class -> {
                            if (prop.hasAnnotation<NullValue>()) text(prop.name).nullable()
                            else text(prop.name)
                        }

                        Int::class -> integer(prop.name)
                        else -> throw IllegalArgumentException("Unsupported type: ${type.classifier}")
                    }

                    if (prop.hasAnnotation<Unique>()) column.uniqueIndex()
                }
            }
        }
}