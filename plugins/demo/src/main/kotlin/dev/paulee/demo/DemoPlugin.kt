package dev.paulee.demo

import dev.paulee.api.data.RequiresData
import dev.paulee.api.plugin.IPlugin
import dev.paulee.api.plugin.PluginMetadata
import dev.paulee.api.plugin.PluginOrder

@PluginOrder(4)
@RequiresData(name = "demo", [Verse::class])
@PluginMetadata(name = "Demo-Plugin", version = "1.0.0", author = "Paul")
class DemoPlugin : IPlugin {

    override fun init() {
        println("${greeting()} from Demo-Plugin")
    }
}