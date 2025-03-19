package dev.paulee.ui.windows

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import dev.paulee.api.data.RequiresData
import dev.paulee.api.plugin.IPluginService
import dev.paulee.api.plugin.PluginMetadata

@Composable
fun PluginInfoWindow(pluginService: IPluginService, onClose: () -> Unit) {
    val scrollState = rememberScrollState()

    val windowState =
        rememberWindowState(position = WindowPosition.Aligned(Alignment.Center), size = DpSize(500.dp, 600.dp))

    Window(state = windowState, onCloseRequest = onClose, title = "Plugin Info") {
        MaterialTheme {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(40.dp),
                    modifier = Modifier.padding(50.dp)
                        .verticalScroll(scrollState)
                ) {
                    Text("Plugins:", fontWeight = FontWeight.Bold, fontSize = 26.sp)

                    pluginService.getPlugins().forEach {
                        val metadata = pluginService.getPluginMetadata(it) ?: return@forEach

                        val dataInfo = pluginService.getDataInfo(it)

                        PluginInfo(
                            metadata,
                            dataInfo,
                            pluginService.getVariants(dataInfo),
                            pluginService.getPreFilters(dataInfo)
                        )
                    }
                }

                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(scrollState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun PluginInfo(
    metadata: PluginMetadata,
    dataInfo: RequiresData?,
    variants: Set<String>,
    preFilters: Set<String>,
) {
    Column(modifier = Modifier.padding(start = 6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${metadata.name} (${metadata.version})", fontSize = 18.sp, fontWeight = FontWeight.Bold)

            metadata.author.takeIf { it.isNotBlank() }?.let {
                Text("by $it")
            }
        }

        val isNotBlank = metadata.description.isNotBlank()

        Text(
            if (isNotBlank) metadata.description else "No Description.",
            fontStyle = if (isNotBlank) FontStyle.Normal else FontStyle.Italic
        )

        Spacer(Modifier.height(8.dp))

        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Data Pool:", fontWeight = FontWeight.Bold)
                Text(dataInfo?.name ?: "None", fontStyle = if (dataInfo == null) FontStyle.Italic else FontStyle.Normal)
            }

            dataInfo?.sources.orEmpty().takeIf { it.isNotEmpty() }?.let {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Sources:", fontWeight = FontWeight.Bold)
                    Text(it.joinToString(", ") { it.simpleName ?: "" })
                }
            }
        }

        variants.takeIf { it.isNotEmpty() }?.let {
            Spacer(Modifier.height(8.dp))

            Column {
                Text("Variants:", fontWeight = FontWeight.Bold)

                variants.forEach { Text(it, modifier = Modifier.padding(start = 4.dp)) }
            }
        }

        preFilters.takeIf { it.isNotEmpty() }?.let {
            Spacer(Modifier.height(8.dp))

            Column {
                Text("Pre filters:", fontWeight = FontWeight.Bold)

                preFilters.forEach { Text(it, modifier = Modifier.padding(start = 4.dp)) }
            }
        }
    }
}