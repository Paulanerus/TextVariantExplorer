package dev.paulee.core.data.io

import dev.paulee.core.normalizeSourceName
import dev.paulee.core.splitStr
import org.slf4j.LoggerFactory.getLogger
import java.nio.file.Path
import kotlin.io.path.bufferedReader

internal class BufferedCSVReader(private val path: Path, private val delimiter: Char = ',') {

    private val logger = getLogger(BufferedCSVReader::class.java)

    private var errorCount: Long = 0

    private var reader = path.bufferedReader()

    private var headSize: Int = -1

    fun readLines(callback: (List<Map<String, String>>) -> Unit) {
        val batch = mutableListOf<Map<String, String>>()

        reader.use {
            val head = this.readLine()

            if (head == null) {
                this.logger.error("Could not read Head ($path).")
                return
            }

            val header = splitStr(head, delimiter).map { normalizeSourceName(it) }

            this.headSize = header.size

            var line: String? = this.readLine()
            while (line != null) {
                val split = splitStr(line, delimiter)

                if (split.size == this.headSize) {
                    val headToValue = mutableMapOf<String, String>()

                    split.forEachIndexed { index, entry -> headToValue[header[index]] = entry }

                    batch.add(headToValue)
                } else {
                    errorCount++
                    this.logger.warn("Line mismatch (Head: $headSize, Line: ${split.size}, error count: $errorCount): $line")
                }

                if (batch.size == 100) callback(batch).also { batch.clear() }

                line = this.readLine()
            }

            if (batch.isNotEmpty()) callback(batch)
        }
    }

    private fun readLine(): String? {
        val line = runCatching { this.reader.readLine() }
            .getOrElse { e ->
                this.logger.error("Exception: Failed to read line.", e)
                null
            } ?: return null

        if (this.headSize == -1 || (this.getDelimiterCount(line) + 1) == this.headSize) return line

        var fullLine = line

        while ((this.getDelimiterCount(fullLine) + 1) < this.headSize) {
            fullLine += runCatching { this.reader.readLine() }.getOrElse { e ->
                this.logger.error("Exception: Failed to read line (while).", e)
                null
            }?.trim() ?: ""
        }

        return fullLine
    }

    private fun getDelimiterCount(str: String): Int {
        var amount = 0

        var insideQuotes = false
        str.forEach {
            if (it == '"') insideQuotes = !insideQuotes

            if (it == delimiter && !insideQuotes) amount++
        }

        return amount
    }
}