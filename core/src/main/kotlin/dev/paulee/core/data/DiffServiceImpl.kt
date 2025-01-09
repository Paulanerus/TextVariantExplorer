package dev.paulee.core.data

import com.github.difflib.text.DiffRowGenerator
import dev.paulee.api.data.Change
import dev.paulee.api.data.DiffService

class DiffServiceImpl : DiffService {

    private val generator =
        DiffRowGenerator.create().mergeOriginalRevised(true).showInlineDiffs(true).oldTag { f -> "~~" }
            .newTag { f -> "**" }.build()

    override fun getDiff(strings: List<String>): Set<Change> {

        if (strings.size <= 1) return emptySet()

        val first = listOf(strings[0])

        return (1 until strings.size).map {
            val output = generator.generateDiffRows(first, listOf(strings[it]))

            val oldLine = output.first().oldLine

            Change(oldLine, extractToken(oldLine))
        }.toSet()
    }

    private fun extractToken(str: String): List<Pair<String, IntRange>> {
        val patternOld = Regex("~~([^~]*)~~")
        val patternNew = Regex("\\*\\*([^*]*)\\*\\*")

        val entriesOld = patternOld.findAll(str).map { it.groupValues[0] to it.range }.toList()
        val entriesNew = patternNew.findAll(str).map { it.groupValues[0] to it.range }.toList()

        return entriesOld + entriesNew
    }
}