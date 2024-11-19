package dev.paulee.demo

import dev.paulee.api.plugin.IPlugin
import dev.paulee.api.plugin.PluginMetadata
import dev.paulee.api.plugin.PluginOrder

@PluginOrder(4)
@PluginMetadata(name = "Demo-Plugin", version = "1.0.0", author = "Paul")
class DemoPlugin : IPlugin {

    override fun init() {
        println("Hello world from Demo-Plugin")
    }
}