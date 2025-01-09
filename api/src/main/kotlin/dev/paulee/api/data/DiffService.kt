package dev.paulee.api.data

data class Change(val str: String, val tokens: List<Pair<String, IntRange>>)

interface DiffService {

    fun getDiff(strings: List<String>): Set<Change>

    fun oldValue(change: Change): String

    fun newValue(change: Change): String
}