package dev.paulee.core.data

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.paulee.api.data.DataInfo
import org.slf4j.LoggerFactory.getLogger
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.notExists

internal object FileService {

    enum class Platform {
        Windows, MacOS, Linux, Other;

        companion object {
            val current: Platform by lazy {
                val os = System.getProperty("os.name")?.lowercase() ?: return@lazy Other

                when {
                    "win" in os -> Windows
                    "mac" in os || "darwin" in os -> MacOS
                    "nux" in os || "nix" in os -> Linux
                    else -> Other
                }
            }

            val isWindows: Boolean get() = current == Windows

            val isMacOS: Boolean get() = current == MacOS

            val isLinux: Boolean get() = current == Linux

            val isOther: Boolean get() = current == Other

            val isCuda12xInstalled: Boolean by lazy {
                if (isMacOS) return@lazy false

                runCatching {
                    val process = ProcessBuilder().apply {
                        command("nvcc", "--version")
                        redirectErrorStream(true)
                    }.start()

                    val output = process.inputStream.bufferedReader().use { it.readText() }
                    process.waitFor()

                    val versionRegex = Regex("""V(\d+)\.(\d+)\.(\d+)""")
                    val matchResult = versionRegex.find(output)

                    if (matchResult != null) {
                        val majorVersion = matchResult.groupValues[1].toIntOrNull() ?: 0

                        majorVersion == 12
                    } else {
                        false
                    }
                }.getOrDefault(false)
            }

            val isCuDNNInstalled by lazy {
                if (isMacOS) return@lazy false

                // This is a temporary solution until JDK 25 with FFM API can be used.
                return@lazy runCatching {
                    if (isWindows) {
                        System.getenv("PATH").contains("NVIDIA\\CUDNN\\v9")
                    } else {
                        val lib = Path("/usr/lib")

                        lib.listDirectoryEntries("libcudnn.so.9*").isNotEmpty()
                    }
                }.getOrDefault(false)
            }
        }
    }

    val appDir: Path get() = ensureDir(".textexplorer", true)

    val pluginsDir: Path get() = ensureDir("plugins")

    val dataDir: Path get() = ensureDir("data")

    val modelsDir: Path get() = ensureDir("models")

    private val logger = getLogger(FileService::class.java)

    private val mapper = jacksonObjectMapper().apply { enable(SerializationFeature.INDENT_OUTPUT) }

    init {
        val osSpecific = when (Platform.current) {
            Platform.MacOS -> ""
            else -> "| CUDA: ${Platform.isCuda12xInstalled}, cuDNN: ${Platform.isCuDNNInstalled}"
        }

        logger.info("Operating system: ${Platform.current} $osSpecific")
    }

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
        val dir = if (main) Path(System.getProperty("user.home") ?: "", name) else appDir.resolve(name)

        if (dir.notExists()) {
            runCatching { dir.createDirectories() }
                .onFailure {
                    logger.error("Failed to create directory '$name'.", it)
                }
        }

        return dir
    }
}