package dev.paulee.core.data

import com.github.difflib.text.DiffRowGenerator
import dev.paulee.api.data.Change
import dev.paulee.api.data.DiffService

class DiffServiceImpl : DiffService {

    private val generator =
        DiffRowGenerator.create().mergeOriginalRevised(true).showInlineDiffs(true).oldTag { _ -> "~~" }
            .newTag { _ -> "**" }.build()

    override fun getDiff(strings: List<String>): List<Change> {

        if (strings.size <= 1) return emptyList()

        val first = listOf(strings[0])

        return (1 until strings.size).map {
            val output = generator.generateDiffRows(first, listOf(strings[it]))

            val oldLine = output.first().oldLine

            Change(oldLine, extractToken(oldLine))
        }
    }

    override fun getDiff(original: String, str: String): Change? = this.getDiff(listOf(original, str)).firstOrNull()

    override fun oldValue(change: Change): String = with(change) {
        tokens.fold(str) { acc, token ->
            if (token.first.startsWith("**")) acc.replace(token.first, "")
            else acc.replace(token.first, token.first.trim('~'))
        }
    }

    override fun newValue(change: Change): String = with(change) {
        tokens.fold(str) { acc, token ->
            if (token.first.startsWith("~~")) acc.replace(token.first, "")
            else acc.replace(token.first, token.first.trim('*'))
        }
    }

    private fun extractToken(str: String): List<Pair<String, IntRange>> {
        val patternOld = Regex("~~([^~]*)~~")
        val patternNew = Regex("\\*\\*([^*]*)\\*\\*")

        val entriesOld = patternOld.findAll(str).map { it.groupValues[0] to it.range }.toList()
        val entriesNew = patternNew.findAll(str).map { it.groupValues[0] to it.range }.toList()

        return entriesOld + entriesNew
    }
}