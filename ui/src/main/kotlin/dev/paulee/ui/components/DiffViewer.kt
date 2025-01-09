package dev.paulee.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import dev.paulee.api.data.DiffService
import dev.paulee.api.plugin.IPlugin
import dev.paulee.api.plugin.IPluginService
import dev.paulee.api.plugin.Taggable
import dev.paulee.ui.HeatmapText
import dev.paulee.ui.MarkedText

@Composable
fun DiffViewerWindow(
    diffService: DiffService,
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

    val associatedPlugins = pluginService.getPlugins().filter { pluginService.getDataInfo(it)?.name == pool }

    val tagPlugins = associatedPlugins.mapNotNull { it as? Taggable }
    var selectedTagger = tagPlugins.firstOrNull()

    val viewFilter = associatedPlugins.mapNotNull { pluginService.getViewFilter(it) }.filter { it.global }
        .flatMap { it.fields.toList() }.toSet()

    var selectedText by remember { mutableStateOf(getTagName(selectedTagger) ?: "") }
    var showPopup by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(false) }

    val entries = remember(selected) {
        if (selected) selectedRows.map { it.filterKeys { key -> key in viewFilter } } // TODO apply plugin specific view filter.
        else selectedRows.map { it.filterKeys { key -> key in viewFilter } }
    }

    Window(onCloseRequest = onClose, title = "DiffViewer") {
        MaterialTheme {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {

                    if (selectedTagger == null) return@Box

                    Text("Tagger:", fontWeight = FontWeight.Bold)

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 8.dp)) {
                        Text(
                            selectedText,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .then(if (tagPlugins.size > 1) Modifier.clickable { showPopup = true }
                                else Modifier))

                        Checkbox(checked = selected, onCheckedChange = { selected = it })
                    }

                    DropdownMenu(
                        expanded = showPopup, onDismissRequest = { showPopup = false }) {
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
                                }) {
                                Text(item.first)
                            }
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (selected) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            entries.forEach { entry ->

                                val tags = selectedTagger?.tag("text", entry.values.first()).orEmpty()

                                MarkedText(text = entry.values.first(), highlights = tags, textAlign = TextAlign.Center)
                            }
                        }
                    } else {
                        val first = entries.first()

                        Column(modifier = Modifier.align(Alignment.Center)) {
                            Text(
                                first.entries.joinToString(" "),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(Modifier.height(24.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                entries.forEachIndexed { index, entry ->

                                    if (index == 0) return@forEachIndexed

                                    val change = diffService.getDiff(first.values.first(), entry.values.first())

                                    HeatmapText(change, entry.values.first())
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
