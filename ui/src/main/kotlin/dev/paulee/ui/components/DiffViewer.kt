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

@Composable
fun DiffViewerWindow(
    pluginService: IPluginService,
    selected: String,
    selectedRows: List<Map<String, String>>,
    onClose: () -> Unit
) {
    Window(onCloseRequest = onClose, title = "DiffViewer") {
        MaterialTheme {
            val associatedPlugins = pluginService.getPlugins()
                .filter { pluginService.getDataInfo(it)?.name == selected.substringBefore(".") }

            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.padding(16.dp).align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    selectedRows.forEach {
                        Text(it.entries.joinToString(" "))
                    }
                }
            }
        }
    }
}