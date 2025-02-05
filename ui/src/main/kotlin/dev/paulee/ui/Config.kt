package dev.paulee.ui

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter
import kotlin.io.path.notExists
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties

object Config {

    var noWidthRestriction = false

    var selectedPool = ""

    private var configFile = "config"

    private var configPath = Path(configFile)

    private val hiddenColumns = mutableMapOf<String, Set<Int>>()

    fun save() {
        this.configPath.bufferedWriter().use { writer ->
            this::class.memberProperties.filter { it.visibility == KVisibility.PUBLIC && it is KMutableProperty<*> }
                .forEach {
                    val value = it.getter.call(this)

                    writer.write("${it.name} = $value\n")
                    writer.newLine()
                }

            this.hiddenColumns.forEach {
                writer.write("${it.key} = ${it.value}\n")
                writer.newLine()
            }
        }
    }

    fun load(path: Path) {
        this.configPath = path.resolve(configFile)

        if (this.configPath.notExists()) return

        this.configPath.bufferedReader().useLines { lines ->
            lines.filter { it.contains("=") }.forEach { line ->
                val (field, value) = line.split("=", limit = 2).map { it.trim() }

                val member = this::class.memberProperties.find { it.name == field }

                if (member != null && member is KMutableProperty<*>) {
                    val converted: Any = when (member.returnType.classifier) {
                        Boolean::class -> value.toBooleanStrictOrNull() ?: false
                        Short::class -> value.toShortOrNull() ?: 0
                        Int::class -> value.toIntOrNull() ?: 0
                        Long::class -> value.toLongOrNull() ?: 0L
                        Float::class -> value.toFloatOrNull() ?: 0f
                        Double::class -> value.toDoubleOrNull() ?: 0
                        else -> value
                    }

                    runCatching { member.setter.call(this, converted) }
                } else {
                    value.takeIf { it.startsWith("[") && it.endsWith("]") }
                        ?.trim('[', ']')
                        ?.replace(" ", "")
                        ?.split(",")
                        ?.mapNotNull { it.toIntOrNull() }
                        ?.toSet()
                        ?.let { this.hiddenColumns[field] = it }
                }
            }
        }
    }

    fun setHidden(id: String, ids: Set<Int>) {
        this.hiddenColumns[id] = ids
    }

    fun getHidden(id: String): Set<Int> = this.hiddenColumns[id] ?: emptySet()
}