package dev.paulee.api.plugin

import java.awt.Color

@Target(AnnotationTarget.CLASS)
annotation class PluginOrder(val order: Int)

@Target(AnnotationTarget.CLASS)
annotation class PluginMetadata(
    val name: String,
    val version: String = "",
    val author: String = "",
    val description: String = "",
)

interface IPlugin {
    fun init()
}

interface Taggable {
    fun tag(field: String, value: String): Map<String, Color>
}