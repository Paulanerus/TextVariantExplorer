package dev.paulee.demo

import dev.paulee.api.plugin.IPlugin
import dev.paulee.api.plugin.ISearchable
import dev.paulee.api.plugin.PluginMetadata
import dev.paulee.api.plugin.PluginOrder

@PluginOrder(4)
@PluginMetadata(name = "Demo-Plugin", version = "1.0.0", author = "Paul")
class DemoPlugin : IPlugin, ISearchable {

    override fun init() {
        println("${greeting()} from Demo-Plugin")
    }

    override fun convertValue(fields: Array<String>): Verse? {
        return fields.takeIf { it.size >= 2 }?.let { Verse(it[0], it[1]) }
    }

    override fun search(verse: Any?) {
        if(verse !is Verse) return

        println("${verse.author} says: ${verse.verse}")
    }
}