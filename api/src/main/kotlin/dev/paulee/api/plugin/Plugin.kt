package dev.paulee.api.plugin

import dev.paulee.api.data.provider.IStorageProvider
import java.awt.Color

@Target(AnnotationTarget.CLASS)
annotation class PluginOrder(val order: Int)

@Target(AnnotationTarget.CLASS)
annotation class PluginMetadata(
    val name: String,
    val version: String,
    val author: String = "",
    val description: String = "",
)

interface IPlugin {
    fun init(storageProvider: IStorageProvider)
}

data class Tag(val name: String, val color: Color)

interface Taggable {
    fun tag(field: String, value: String): Map<String, Tag>
}

interface Drawable