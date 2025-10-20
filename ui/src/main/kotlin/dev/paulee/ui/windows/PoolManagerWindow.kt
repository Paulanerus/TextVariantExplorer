package dev.paulee.ui.windows

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import dev.paulee.api.data.DataInfo
import dev.paulee.api.data.IDataService
import dev.paulee.api.data.IndexField
import dev.paulee.api.data.provider.StorageType
import dev.paulee.ui.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.io.path.Path

@Composable
fun PoolManagerWindow(dataService: IDataService, onClose: () -> Unit) {
    val locale = LocalI18n.current

    val windowState = rememberWindowState(
        position = WindowPosition.Aligned(Alignment.Center), size = DpSize(800.dp, 600.dp)
    )

    Window(
        state = windowState,
        icon = App.icon,
        onCloseRequest = {
            Config.save()
            onClose()
        },
        title = locale["pools_management.title"],
    ) {
        App.Theme.Current {
            val scope = rememberCoroutineScope()

            var pools by remember {
                mutableStateOf(
                    dataService.getAvailableDataInfo().sortedBy { it.name }
                )
            }

            val rebuildProgress = remember { mutableStateMapOf<String, MutableStateFlow<Int>>() }

            fun refreshPools() {
                pools = dataService.getAvailableDataInfo().sortedBy { it.name }
            }

            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp)
            ) {
                Text(
                    text = locale["pools_management.title"],
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                if (pools.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(locale["pools_management.empty"])
                    }

                    return@Column
                }

                val listState = rememberLazyListState()

                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(pools, key = { it.name }) { info ->
                            val progressFlow = rebuildProgress[info.name]
                            val progress = progressFlow?.collectAsState()?.value

                            val isRebuilding = progressFlow != null

                            PoolCard(
                                dataInfo = info,
                                hasEmbeddings = info.sources.any { source ->
                                    source.fields.any { field ->
                                        field is IndexField && field.embeddingModel != null
                                    }
                                },
                                sourcesText = info.sources.joinToString(", ") { it.name },
                                locale = locale,
                                progress = progress,
                                onDelete = {
                                    if (isRebuilding) return@PoolCard

                                    scope.launch {
                                        dataService.deleteDataPool(info.name)

                                        refreshPools()

                                        poolSelected = !poolSelected
                                    }
                                },
                                onRebuild = {
                                    if (isRebuilding) return@PoolCard

                                    scope.launch {
                                        val flow = rebuildProgress.getOrPut(info.name) { MutableStateFlow(0) }
                                        flow.value = 0

                                        dataService.rebuildDataPool(info) { value ->
                                            flow.value = value
                                        }

                                        refreshPools()

                                        rebuildProgress.remove(info.name)
                                    }
                                },
                                onExport = {
                                    //TODO Temporary export to desktop
                                    val desktop = Path(System.getProperty("user.home"), "Desktop")

                                    scope.launch {
                                        dataService.exportPool(dataInfo = info, path = desktop)
                                    }
                                }
                            )
                        }
                    }

                    VerticalScrollbar(
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(listState)
                    )
                }
            }
        }
    }
}

@Composable
private fun PoolCard(
    dataInfo: DataInfo,
    hasEmbeddings: Boolean,
    sourcesText: String,
    locale: I18n,
    progress: Int?,
    onDelete: () -> Unit,
    onRebuild: () -> Unit,
    onExport: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().background(
            color = MaterialTheme.colors.secondary,
            shape = RoundedCornerShape(6.dp)
        ).padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dataInfo.name,
                        style = MaterialTheme.typography.h6
                    )

                    if (hasEmbeddings) {
                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            modifier = Modifier.background(
                                color = MaterialTheme.colors.primary,
                                shape = RoundedCornerShape(12.dp)
                            ).padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = locale["pools_management.embedding.label"],
                                color = MaterialTheme.colors.onPrimary,
                                style = MaterialTheme.typography.caption
                            )
                        }
                    }
                }

                if (dataInfo.storageType != StorageType.Default) {
                    Text(
                        text = locale["pools_management.provider", dataInfo.storageType.name],
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (dataInfo.storageType == StorageType.SQLITE) {
                    Text(
                        text = locale["pools_management.sqlite.warning"],
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Text(
                    text = locale["pools_management.sources", sourcesText],
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onDelete, enabled = progress == null) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = locale["pools_management.delete"]
                    )
                }

                IconButton(onClick = onRebuild, enabled = progress == null) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = locale["pools_management.recreate"]
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                enabled = progress == null,
                onClick = onExport,
            ) {
                Text(locale["pools_management.export"])
            }
        }

        progress?.let { value ->
            val coerced = value.coerceIn(0, 100)

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = coerced / 100f,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = locale["pools_management.recreate.status", coerced],
                style = MaterialTheme.typography.caption,
                modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
            )
        }
    }
}