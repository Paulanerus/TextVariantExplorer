package dev.paulee.demo

import dev.paulee.api.data.DataSource

fun greeting() = "Hello world"

@DataSource("verses.csv")
data class Verse(val text: String, val year: String, val book: Int)