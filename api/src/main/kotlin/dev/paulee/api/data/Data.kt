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
annotation class Variant(val base: String, val variants: Array<String>)

@Target(AnnotationTarget.CLASS)
annotation class PreFilter(val key: String, val linkKey: String, val value: String)

@Target(AnnotationTarget.CLASS)
annotation class RequiresData(val name: String, val sources: Array<KClass<*>> = [])

@Target(AnnotationTarget.CLASS)
annotation class DataSource(val file: String)

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Unique(val identify: Boolean = false)

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Index(val lang: Language = Language.ENGLISH, val default: Boolean = false)

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class NullValue(val values: Array<String>)

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Link(val clazz: KClass<*>)

@Target(AnnotationTarget.FUNCTION)
annotation class ViewFilter(val name: String, val fields: Array<String>, val global: Boolean = true)
