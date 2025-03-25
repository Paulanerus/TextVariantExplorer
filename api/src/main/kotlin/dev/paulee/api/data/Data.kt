package dev.paulee.api.data

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dev.paulee.api.data.provider.StorageType
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
annotation class RequiresData(val name: String)

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class NullValue(val values: Array<String>)

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Link(val clazz: KClass<*>)

@Target(AnnotationTarget.FUNCTION)
annotation class ViewFilter(
    val name: String,
    val fields: Array<String> = [],
    val alwaysShow: Array<String> = [],
    val global: Boolean = true
)

enum class FieldType {
    TEXT,
    INT,
    FLOAT,
    BOOLEAN
}

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
    defaultImpl = BasicField::class
)
@JsonSubTypes(
    JsonSubTypes.Type(value = BasicField::class, name = "basic"),
    JsonSubTypes.Type(value = IndexField::class, name = "index"),
)
sealed interface SourceField {
    val name: String

    val fieldType: FieldType
}

data class BasicField(override val name: String, override val fieldType: FieldType) : SourceField

data class IndexField(
    override val name: String,
    override val fieldType: FieldType,
    val lang: Language,
    val default: Boolean = false
) : SourceField

data class UniqueField(override val name: String, override val fieldType: FieldType, val identify: Boolean = false) :
    SourceField

data class Source(val name: String, val fields: List<SourceField>)

data class DataInfo(val name: String, val sources: List<Source>, val storageType: StorageType = StorageType.SQLITE)
