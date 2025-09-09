package dev.paulee.ui.windows

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
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
import dev.paulee.api.data.DataInfo
import dev.paulee.api.plugin.IPluginService
import dev.paulee.api.plugin.PluginMetadata
import dev.paulee.ui.App
import dev.paulee.ui.LocalI18n

@Composable
fun PluginInfoWindow(pluginService: IPluginService, allDataInfo: Set<DataInfo>, onClose: () -> Unit) {
    val scrollState = rememberScrollState()

    val windowState =
        rememberWindowState(position = WindowPosition.Aligned(Alignment.Center), size = DpSize(500.dp, 600.dp))

    val locale = LocalI18n.current

    Window(state = windowState, icon = App.icon, onCloseRequest = onClose, title = locale["plugin.title"]) {
        App.Theme.Current {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(40.dp),
                    modifier = Modifier.padding(50.dp)
                        .verticalScroll(scrollState)
                ) {
                    Text(locale["plugin.plugins"], fontWeight = FontWeight.Bold, fontSize = 26.sp)

                    if (pluginService.getPlugins().isEmpty())
                        Text(locale["plugin.no_plugins"])

                    pluginService.getPlugins().forEach {
                        val metadata = pluginService.getPluginMetadata(it) ?: return@forEach

                        val dataInfoName = pluginService.getDataInfo(it) ?: return@forEach

                        val dataInfo = allDataInfo.firstOrNull { info -> info.name == dataInfoName }

                        PluginInfo(metadata, dataInfo)
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
private fun PluginInfo(metadata: PluginMetadata, dataInfo: DataInfo?) {
    val locale = LocalI18n.current

    Column(modifier = Modifier.padding(start = 6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${metadata.name} (${metadata.version})", fontSize = 18.sp, fontWeight = FontWeight.Bold)

            metadata.author.takeIf { it.isNotBlank() }?.let {
                Text(locale["plugin.by", it])
            }
        }

        val isNotBlank = metadata.description.isNotBlank()

        Text(
            if (isNotBlank) metadata.description else locale["plugin.no_description"],
            fontStyle = if (isNotBlank) FontStyle.Normal else FontStyle.Italic
        )

        Spacer(Modifier.height(8.dp))

        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(locale["plugin.data_pool.label"], fontWeight = FontWeight.Bold)
                Text(dataInfo?.name ?: locale["plugin.none"], fontStyle = if (dataInfo == null) FontStyle.Italic else FontStyle.Normal)
            }

            dataInfo?.sources.orEmpty().takeIf { it.isNotEmpty() }?.let {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(locale["plugin.sources.label"], fontWeight = FontWeight.Bold)
                    Text(it.joinToString(", ") { entry -> entry.name })
                }
            }
        }

        dataInfo?.sources.orEmpty().filter { it.variantMapping != null }.map { it.name }.takeIf { it.isNotEmpty() }
            ?.let {
                Spacer(Modifier.height(8.dp))

                Column {
                    Text(locale["plugin.variants.label"], fontWeight = FontWeight.Bold)


                    it.forEach { text -> Text(text, modifier = Modifier.padding(start = 4.dp)) }
                }
            }

        dataInfo?.sources.orEmpty().filter { it.preFilter != null }.map { it.name }.takeIf { it.isNotEmpty() }
            ?.let {
                Spacer(Modifier.height(8.dp))

                Column {
                    Text(locale["plugin.pre_filters.label"], fontWeight = FontWeight.Bold)


                    it.forEach { text -> Text(text, modifier = Modifier.padding(start = 4.dp)) }
                }
            }
    }
}