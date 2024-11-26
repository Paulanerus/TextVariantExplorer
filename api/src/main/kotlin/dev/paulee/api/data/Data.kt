package dev.paulee.api.data

import kotlin.reflect.KClass

enum class Language {
    ARABIC,
    ARMENIAN,
    BASQUE,
    BENGALI,
    BRAZILIAN_PORTUGUESE,
    BULGARIAN,
    CATALAN,
    CHINESE,
    CZECH,
    DANISH,
    DUTCH,
    ENGLISH,
    ESTONIAN,
    FINNISH,
    FRENCH,
    GALICIAN,
    GERMAN,
    GREEK,
    HINDI,
    HUNGARIAN,
    INDONESIAN,
    IRISH,
    ITALIAN,
    JAPANESE,
    KOREAN,
    LATVIAN,
    LITHUANIAN,
    NEPALI,
    NORWEGIAN,
    PERSIAN,
    POLISH,
    PORTUGUESE,
    ROMANIAN,
    RUSSIAN,
    SERBIAN,
    SORANI_KURDISH,
    SPANISH,
    SWEDISH,
    TAMIL,
    TELUGU,
    THAI,
    TURKISH,
    UKRAINIAN
}

@Target(AnnotationTarget.CLASS)
annotation class RequiresData(val name: String, val sources: Array<KClass<*>> = [])

@Target(AnnotationTarget.CLASS)
annotation class DataSource(val file: String)

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Unique(val identify: Boolean = false)

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Index(val lang: Language = Language.ENGLISH)

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class NullValue(val values: Array<String>)

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class MergeWith(val field: String)
