package dev.paulee.demo

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import dev.paulee.api.data.RequiresData
import dev.paulee.api.data.ViewFilter
import dev.paulee.api.data.provider.IStorageProvider
import dev.paulee.api.plugin.*
import java.awt.Color

@RequiresData(name = "greek_variant", [Occurrence::class, Name::class, Manuscript::class, Verse::class])
@PluginMetadata(name = "GreekVariant-Plugin", version = "1.0.0", author = "Paul")
class DemoPlugin : IPlugin, Taggable, Drawable {

    val names = mutableSetOf<String>()

    override fun init(storageProvider: IStorageProvider) {
        println("Loaded GreekVariant Plugin")

        storageProvider.get("names").mapNotNullTo(names) { it["variant"] }
    }

    @ViewFilter("DemoTag", fields = ["text"], global = true)
    override fun tag(field: String, value: String): Map<String, Tag> = names.associate { it to Tag("NAME", Color.BLUE) }


    fun composeContent(entries: List<Map<String, String>>): @Composable () -> Unit = {
        Text("Selected entries: ${entries.size}")
    }
}