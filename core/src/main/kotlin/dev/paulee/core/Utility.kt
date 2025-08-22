package dev.paulee.core

import org.slf4j.LoggerFactory.getLogger
import java.security.MessageDigest

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

private val NON_ALPHANUMERIC = Regex("[^A-Za-z0-9_]")

private val FORBIDDEN_PREFIXES = listOf("sqlite_")

fun normalizeSourceName(str: String): String {
    val replaced = NON_ALPHANUMERIC.replace(str, "_")

    val needsUnderscore =
        replaced.firstOrNull()?.isDigit() == true || FORBIDDEN_PREFIXES.any { replaced.startsWith(it) }

    return (if (needsUnderscore) "_$replaced" else replaced).trim()
}

fun splitStr(str: String, delimiter: Char, quoteCharacters: Array<Char> = arrayOf('"')): List<String> {
    val result = ArrayList<String>()
    val sb = StringBuilder(str.length)

    if (quoteCharacters.size == 1) {
        val quote = quoteCharacters[0]
        var inside = false

        for (c in str) {
            when (c) {
                quote -> inside = !inside
                delimiter if !inside -> {
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

fun sha1Hex(input: String): String =
    MessageDigest.getInstance("SHA-1")
        .digest(input.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }