package dev.paulee.core.data.io

import dev.paulee.core.normalizeSourceName
import dev.paulee.core.splitStr
import org.slf4j.LoggerFactory.getLogger
import java.nio.file.Path
import java.util.ArrayList
import java.util.HashMap
import kotlin.io.path.bufferedReader

class BufferedCSVReader(private val path: Path, private val delimiter: Char = ',', val batchSize: Int = 300) {

    private val logger = getLogger(BufferedCSVReader::class.java)

    private var reader = path.bufferedReader()

    private var headSize: Int = -1

    fun readLines(callback: (List<HashMap<String, String>>) -> Unit) {
        val batch = ArrayList<HashMap<String, String>>(batchSize)

        val builder = StringBuilder(1024)

        reader.use {
            val head = this.readLine(builder)

            if (head == null) {
                this.logger.error("Could not read Head ($path).")
                return
            }

            val header = splitStr(head, delimiter).map { normalizeSourceName(it) }

            this.headSize = countDelimiter(head)

            var line: String? = this.readLine(builder)
            while (line != null) {
                val split = splitStr(line, delimiter)

                if (split.size == this.headSize) {
                    val headToValue = HashMap<String, String>(this.headSize)

                    split.forEachIndexed { index, entry -> headToValue[header[index]] = entry }

                    batch.add(headToValue)

                    if (batch.size == batchSize) {
                        callback(batch)
                        batch.clear()
                    }
                }

                line = this.readLine(builder)
            }

            if (batch.isNotEmpty()) callback(batch)
        }
    }

    private fun readLine(builder: StringBuilder): String? {
        val line = runCatching { this.reader.readLine() }
            .getOrElse { e ->
                this.logger.error("Exception: Failed to read line.", e)
                null
            } ?: return null

        if (this.headSize == -1 || (countDelimiter(line)) == this.headSize) return line

        builder.clear()
        builder.append(line)

        while ((countDelimiter(builder)) < this.headSize) {
            builder.append(runCatching { this.reader.readLine() }.getOrElse { e ->
                this.logger.error("Exception: Failed to read line (while).", e); null
            } ?: "")
        }

        return builder.toString()
    }

    private fun countDelimiter(sequence: CharSequence): Int {
        var amount = 1

        var insideQuotes = false
        for (char in sequence) {
            if (char == '"') insideQuotes = !insideQuotes

            if (char == delimiter && !insideQuotes) amount++
        }

        return amount
    }
}