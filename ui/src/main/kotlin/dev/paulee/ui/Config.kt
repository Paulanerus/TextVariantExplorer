package dev.paulee.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter
import kotlin.io.path.notExists
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties

object Config {

    var theme by mutableStateOf("Light")

    var lang by mutableStateOf("en")

    var noWidthRestriction by mutableStateOf(false)

    var exactHighlighting by mutableStateOf(true)

    var selectedPool by mutableStateOf("")

    var windowState by mutableStateOf("Floating")

    var windowWidth by mutableStateOf(1600)
    var windowHeight by mutableStateOf(900)

    var windowX by mutableStateOf(-1)
    var windowY by mutableStateOf(-1)

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