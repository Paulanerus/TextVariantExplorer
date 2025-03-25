package dev.paulee.demo

import dev.paulee.api.data.*

@PreFilter(key = "verse_id", linkKey = "variant", value = "occurrence")
data class Occurrence(val verse_id: Long, val variantID: Long, val occurrence: String, val wordID: Long)

@Variant(base = "label_en", variants = ["label_el_norm", "variant"])
data class Name(
    val label_en: String,
    val gender: String,
    val label_el_norm: String,
    val factgrid: String,
    val variant: String,
    val wordID: Long,
    val variantID: Long,
)