package dev.paulee.core.data

import com.github.difflib.text.DiffRowGenerator
import dev.paulee.api.data.Change
import dev.paulee.api.data.DiffService

class DiffServiceImpl : DiffService {

    private val generator =
        DiffRowGenerator.create().mergeOriginalRevised(true).showInlineDiffs(true).oldTag { _ -> "~~" }
            .newTag { _ -> "**" }.build()

    override fun getDiff(original: String, str: String): Change? {
        if (original == str) return null

        val output = generator.generateDiffRows(listOf(original), listOf(str))

        val oldLine = output.firstOrNull()?.oldLine ?: return null

        return Change(oldLine, extractToken(oldLine))
    }

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