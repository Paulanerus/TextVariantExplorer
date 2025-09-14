package dev.paulee.api.data

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dev.paulee.api.data.provider.StorageType
import dev.paulee.api.internal.Embedding

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
annotation class RequiresData(val name: String)

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class NullValue(val values: Array<String>)

@Target(AnnotationTarget.FUNCTION)
annotation class ViewFilter(
    val name: String,
    val fields: Array<String> = [],
    val alwaysShow: Array<String> = [],
    val global: Boolean = true,
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

    val sourceLink: String
}

data class BasicField(
    override val name: String,
    override val fieldType: FieldType,
    @param:JsonInclude(JsonInclude.Include.NON_EMPTY) override val sourceLink: String = "",
) :
    SourceField

data class IndexField(
    override val name: String,
    override val fieldType: FieldType,
    @param:JsonInclude(JsonInclude.Include.NON_EMPTY)
    override val sourceLink: String = "",
    val lang: Language,
    val default: Boolean = false,
    val embeddingModel: Embedding.Model? = null,
) : SourceField

data class UniqueField(
    override val name: String,
    override val fieldType: FieldType,
    @param:JsonInclude(JsonInclude.Include.NON_EMPTY)
    override val sourceLink: String = "",
    val identify: Boolean = false,
) : SourceField

data class VariantMapping(val base: String, val variants: List<String>)

data class PreFilter(val key: String, val linkKey: String, val value: String)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Source(
    val name: String,
    val fields: List<SourceField>,
    val variantMapping: VariantMapping? = null,
    val preFilter: PreFilter? = null,
)

data class DataInfo(val name: String, val sources: List<Source>, val storageType: StorageType = StorageType.SQLITE)
