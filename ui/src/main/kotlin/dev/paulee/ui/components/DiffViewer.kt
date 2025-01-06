package dev.paulee.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import dev.paulee.api.plugin.IPluginService
import dev.paulee.api.plugin.Taggable

@Composable
fun DiffViewerWindow(
    pluginService: IPluginService,
    selected: String,
    selectedRows: List<Map<String, String>>,
    onClose: () -> Unit,
) {
    val associatedPlugins = pluginService.getPlugins()
        .filter { pluginService.getDataInfo(it)?.name == selected.substringBefore(".") }

    val tagPlugins = associatedPlugins.mapNotNull { it as? Taggable }

    val viewFilter = associatedPlugins.mapNotNull { pluginService.getViewFilter(it) }.filter { it.global }
        .flatMap { it.fields.toList() }.toSet()

    Window(onCloseRequest = onClose, title = "DiffViewer") {
        MaterialTheme {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.padding(16.dp).align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    selectedRows.forEach {
                        val text = it.entries.joinToString(" ") { if (it.key in viewFilter) it.value else "" }

                        Text(text)
                    }
                }
            }
        }
    }
}