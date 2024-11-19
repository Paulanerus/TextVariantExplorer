package dev.paulee.api.plugin

@Target(AnnotationTarget.CLASS)
annotation class PluginOrder(val order: Int)

@Target(AnnotationTarget.CLASS)
annotation class PluginMetadata(
    val name: String,
    val version: String = "",
    val author: String = "",
    val description: String = ""
)

interface IPlugin {
    fun init()
}

interface ISearchable {
    fun convertValue(fields: Array<String>): Any?

    fun search(entry: Any?)
}