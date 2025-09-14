package dev.paulee.core.data

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.paulee.api.data.DataInfo
import org.slf4j.LoggerFactory.getLogger
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.notExists

internal object FileService {

    val appDir: Path get() = ensureDir(".textexplorer", true)

    val pluginsDir: Path get() = ensureDir("plugins")

    val dataDir: Path get() = ensureDir("data")

    val modelsDir: Path get() = ensureDir("models")

    private val logger = getLogger(FileService::class.java)

    private val mapper = jacksonObjectMapper().apply { enable(SerializationFeature.INDENT_OUTPUT) }

    fun toJson(dataInfo: DataInfo): String? = runCatching { this.mapper.writeValueAsString(dataInfo) }
        .getOrElse {
            this.logger.error("Exception: Could not serialize file info.", it)
            null
        }

    fun fromJson(json: String): DataInfo? = runCatching { this.mapper.readValue<DataInfo>(json) }
        .getOrElse {
            this.logger.error("Exception: Could not deserialize file info.", it)
            null
        }

    private fun ensureDir(name: String, main: Boolean = false): Path {
        val dir = if (main) Path(System.getProperty("user.home"), name) else appDir.resolve(name)

        if (dir.notExists()) {
            runCatching { dir.createDirectories() }
                .onFailure {
                    logger.error("Failed to create directory '$name'.", it)
                }
        }

        return dir
    }
}