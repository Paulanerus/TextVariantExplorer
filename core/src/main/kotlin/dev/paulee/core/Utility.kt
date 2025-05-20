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

fun splitStr(str: String, delimiter: Char, quoteCharacters: Array<Char> = arrayOf('"')): List<String> {
    val result = ArrayList<String>()
    val sb = StringBuilder(str.length)

    if (quoteCharacters.size == 1) {
        val quote = quoteCharacters[0]
        var inside = false

        for (c in str) {
            when {
                c == quote -> inside = !inside
                c == delimiter && !inside -> {
                    result.add(sb.toString())
                    sb.setLength(0)
                }

                else -> sb.append(c)
            }
        }
    } else {
        var inside = false

        for (c in str) {
            if (quoteCharacters.contains(c)) {
                inside = !inside
            } else if (c == delimiter && !inside) {
                result.add(sb.toString())
                sb.setLength(0)
            } else {
                sb.append(c)
            }
        }
    }

    if (sb.isNotBlank()) result.add(sb.toString())

    return result
}

fun normalizeDataSource(dataSource: String): String = dataSource.substringBeforeLast(".").replace(" ", "_")
