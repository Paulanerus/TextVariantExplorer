package dev.paulee.core

import org.slf4j.LoggerFactory.getLogger

object GlobalExceptionHandler {

    private val logger = getLogger(GlobalExceptionHandler::class.java)

    init {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            logger.error(
                "Uncaught exception in thread ${thread.name}.",
                throwable
            )
        }
    }
}

fun splitStr(str: String, delimiter: Char, quoteCharacters: Array<Char> = arrayOf('"', '\'')): List<String> {
    val tokens = mutableListOf<String>()

    var tokenStart = 0
    var insideQuotes = false
    str.indices.forEach {
        val c = str[it]

        if (quoteCharacters.contains(c)) insideQuotes = !insideQuotes

        if (c == delimiter && !insideQuotes) {
            if (it > tokenStart) tokens.add(str.substring(tokenStart, it))
            else tokens.add("")

            tokenStart = it + 1
        }
    }

    if (tokenStart < str.length) tokens.add(str.substring(tokenStart))

    return tokens
}

fun normalizeDataSource(dataSource: String): String = dataSource.substringBeforeLast(".").replace(" ", "_")
