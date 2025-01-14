package dev.paulee.demo

import dev.paulee.api.data.RequiresData
import dev.paulee.api.data.ViewFilter
import dev.paulee.api.plugin.IPlugin
import dev.paulee.api.plugin.PluginMetadata
import dev.paulee.api.plugin.Tag
import dev.paulee.api.plugin.Taggable

@RequiresData(name = "greek_variant", [Occurrence::class, Name::class, Manuscript::class, Verse::class])
@PluginMetadata(name = "GreekVariant-Plugin", version = "1.0.0", author = "Paul")
class DemoPlugin : IPlugin, Taggable {

    override fun init() {
        println("Loaded GreekVariant Plugin")
    }

    @ViewFilter("DemoTag", fields = ["text"], global = true)
    override fun tag(field: String, value: String): Map<String, Tag> = emptyMap()
}