package dev.paulee.core.data.io

import java.nio.file.Path
import kotlin.io.path.bufferedReader

class BufferedCSVReader(path: Path, private val delimiter: Char = ',') {

    var lineCount: Long = 0
    var errorCount: Long = 0

    private var reader = path.bufferedReader()

    private var headSize: Int = -1

    fun readLines(callback: (List<List<String>>) -> Unit) {
        val batch = mutableListOf<List<String>>()

        reader.use {
            val header = this.readLine() ?: return

            headSize = this.getDelimiterCount(header) + 1

            var line: String? = this.readLine()
            while (line != null) {
                val split = splitStr(line);

                if (split.size == this.headSize) batch.add(split).also { lineCount++ }
                else errorCount++

                if (batch.size == 50) callback(batch).also { batch.clear() }

                line = this.readLine()
            }

            if (batch.isNotEmpty()) callback(batch)

            println("Amount: $lineCount")
            println("Errors: $errorCount")
        }
    }

    private fun readLine(): String? {
        val line = runCatching { this.reader.readLine() }.getOrNull() ?: return null

        if (this.headSize == -1 || (this.getDelimiterCount(line) + 1) == this.headSize) return line

        var fullLine = line

        while ((this.getDelimiterCount(fullLine) + 1) < this.headSize) fullLine += runCatching { this.reader.readLine() }.getOrNull()
            ?.trim() ?: ""

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

    private fun splitStr(str: String): List<String> {
        var tokens = mutableListOf<String>()

        var tokenStart = 0
        var insideQuotes = false
        (0 until str.length).forEach {
            val c = str[it]

            if (c == '"') insideQuotes = !insideQuotes

            if (c == delimiter && !insideQuotes) {
                if (it > tokenStart) tokens.add(str.substring(tokenStart, it))
                else tokens.add("")

                tokenStart = it + 1
            }
        }

        if (tokenStart < str.length) tokens.add(str.substring(tokenStart))

        return tokens
    }
}