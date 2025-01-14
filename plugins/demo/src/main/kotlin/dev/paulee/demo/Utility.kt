package dev.paulee.demo

import dev.paulee.api.data.*

@PreFilter(key = "verse_id", linkKey = "variant", value = "occurrence")
@DataSource("occurrences")
data class Occurrence(val verse_id: Long, val variantID: Long, val occurrence: String, @Link(Name::class) val wordID: Long)

@Variant(base = "label_en", variants = ["label_el_norm", "variant"])
@DataSource("names")
data class Name(
    val label_en: String,
    val gender: String,
    val label_el_norm: String,
    val factgrid: String,
    val variant: String,
    val wordID: Long,
    val variantID: Long,
)

@DataSource("manuscripts")
data class Manuscript(
    val ga: String,
    val source: String,
    val docID: String,
    val pagesCount: Long,
    val leavesCount: Long,
    val century: String,
    val label: String,
    val dbpedia: String,
)

@DataSource("verses")
data class Verse(
    val bkv: String,
    val edition_date: String,
    val edition_version: String,
    val encoding_version: String,
    val founder: String,
    @Link(Manuscript::class) val ga: String,
    val lection: String,
    val nkv: String,
    val source: String,
    val transcript: String,
    @Index(Language.GREEK, default = true) val text: String,
    @Unique(true) val verse_id: Long,
)