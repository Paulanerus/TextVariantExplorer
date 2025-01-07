package dev.paulee.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import dev.paulee.api.plugin.IPlugin
import dev.paulee.api.plugin.IPluginService
import dev.paulee.api.plugin.Tag
import dev.paulee.api.plugin.Taggable
import dev.paulee.ui.MarkedText

fun generateHeatMap(selectedRows: List<Map<String, String>>): List<Map<String, Tag>> {

    if (selectedRows.size <= 1) return emptyList()

    return emptyList()
}

@Composable
fun DiffViewerWindow(
    pluginService: IPluginService,
    selected: String,
    selectedRows: List<Map<String, String>>,
    onClose: () -> Unit,
) {
    fun getTagName(taggable: Taggable?): String? {
        val plugin = taggable as? IPlugin ?: return null

        val pluginName = pluginService.getPluginMetadata(plugin)?.name ?: return null

        val viewFilterName = pluginService.getViewFilter(plugin)?.name ?: pluginName

        return when {
            viewFilterName.isNotEmpty() -> viewFilterName
            pluginName.isNotEmpty() -> pluginName
            else -> null
        }
    }

    val (pool, select) = selected.split(".", limit = 2)

    val heatmap = generateHeatMap(selectedRows)

    val associatedPlugins = pluginService.getPlugins()
        .filter { pluginService.getDataInfo(it)?.name == pool }

    val tagPlugins = associatedPlugins.mapNotNull { it as? Taggable }
    var selectedTagger = tagPlugins.firstOrNull()

    val viewFilter = associatedPlugins.mapNotNull { pluginService.getViewFilter(it) }.filter { it.global }
        .flatMap { it.fields.toList() }.toSet()

    var selectedText by remember { mutableStateOf(getTagName(selectedTagger) ?: "") }
    var showPopup by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(false) }

    Window(onCloseRequest = onClose, title = "DiffViewer") {
        MaterialTheme {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {

                    if (selectedTagger == null) return@Box

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            selectedText,
                            modifier = Modifier.padding(2.dp).then(
                                if (tagPlugins.size > 1) Modifier.clickable { showPopup = true }
                                else Modifier
                            ))

                        Checkbox(checked = selected, onCheckedChange = { selected = it })
                    }

                    DropdownMenu(
                        expanded = showPopup,
                        onDismissRequest = { showPopup = false }
                    ) {
                        val menuItems = tagPlugins.mapNotNull {
                            val name = getTagName(it) ?: return@mapNotNull null

                            Pair(name, it)
                        }
                        menuItems.forEach { item ->
                            DropdownMenuItem(
                                onClick = {
                                    selectedTagger = item.second
                                    selectedText = item.first
                                    showPopup = false
                                }
                            ) {
                                Text(item.first)
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier.padding(16.dp).align(Alignment.Center),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    selectedRows.forEach {
                        val text = it.entries.joinToString(" ") { if (it.key in viewFilter) it.value else "" }

                        val tags =
                            selectedTagger?.tag(select, text).orEmpty()

                        MarkedText(text = text, highlights = if (selected) tags else heatmap.first())
                    }
                }
            }
        }
    }
}