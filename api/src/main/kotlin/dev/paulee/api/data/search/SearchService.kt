package dev.paulee.api.data.search

interface SearchService {

    fun init()

    fun search(query: String)
}