package dev.paulee.api.data

data class Change(val str: String, val tokens: List<Pair<String, IntRange>>)

interface DiffService {

    fun getDiff(strings: List<String>): Set<Change>
}