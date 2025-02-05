package dev.paulee.core

import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.*
import kotlin.system.exitProcess

private const val RESET = "\u001B[0m"

private const val GREEN = "\u001B[32m"

private const val YELLOW = "\u001B[33m"

private const val RED = "\u001B[31m"

private const val PURPLE = "\u001B[35m"

private enum class LogType {
    INFO,
    WARN,
    ERROR,
    EXCEPTION
}

private fun getTimestamp(): String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

private fun writeToFile(path: Path, message: String) {
    if (path.notExists()) path.createFile()

    path.appendText(message + System.lineSeparator())
}

class Logger private constructor(
    private val serviceName: String,
    private val logFile: Path,
) {

    companion object {
        private val loggers = mutableMapOf<String, Logger>()

        private val systemPath: Path = Paths.get(System.getProperty("user.home"), ".textexplorer")

        private val defaultLogFile: Path = systemPath.resolve("log.txt")

        private val crashLogFile = systemPath.resolve("crashlog_${System.currentTimeMillis()}.txt")

        init {
            if (systemPath.notExists()) systemPath.createDirectories()

            defaultLogFile.deleteIfExists()

            this.registerGlobalExceptionHandler()
        }

        fun getLogger(serviceName: String): Logger {
            return loggers.getOrPut(serviceName) {
                Logger(serviceName, defaultLogFile)
            }
        }

        private fun registerGlobalExceptionHandler() {
            Thread.setDefaultUncaughtExceptionHandler { _, exception ->
                val timestamp = getTimestamp()

                val message = buildString {
                    appendLine("---------- CRASH LOG ----------")
                    appendLine("Timestamp: $timestamp")
                    appendLine("Exception: ${exception::class.simpleName}")
                    appendLine("Message: ${exception.message}")
                    appendLine("Stack Trace:")
                    appendLine(exception.stackTraceToString())
                }

                println("${System.lineSeparator()}$RED$message$RESET${System.lineSeparator()}")

                writeToFile(crashLogFile, message)

                exitProcess(1)
            }
        }
    }

    fun info(str: String) = this.log(str, LogType.INFO)

    fun warn(str: String) = this.log(str, LogType.WARN)

    fun error(str: String) = this.log(str, LogType.ERROR)

    fun exception(exception: Throwable) =
        this.log(exception.message + " | " + exception.stackTraceToString(), LogType.EXCEPTION)

    private fun log(str: String?, type: LogType) {
        val timestamp = getTimestamp()

        val color = when (type) {
            LogType.INFO -> GREEN
            LogType.WARN -> YELLOW
            LogType.ERROR -> RED
            LogType.EXCEPTION -> PURPLE
        }

        val message = "[$timestamp] [$serviceName] [${type.name}] $str"

        println("$color$message$RESET")

        writeToFile(this.logFile, message)
    }
}