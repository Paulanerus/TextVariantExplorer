package dev.paulee.ui.windows

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.Button
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import dev.paulee.api.data.IDataService
import dev.paulee.api.internal.Embedding
import dev.paulee.ui.App
import dev.paulee.ui.Config
import dev.paulee.ui.LocalI18n
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.*

@OptIn(ExperimentalPathApi::class)
@Composable
fun ModelManagerWindow(dataService: IDataService, onClose: () -> Unit) {
    val locale = LocalI18n.current

    val modelDir = remember { dataService.modelDir() }

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
        title = locale["model_management.title"],
    ) {
        App.Theme.Current {
            val scope = rememberCoroutineScope()
            val models = remember { Embedding.Model.entries }

            var downloadProgress by remember { mutableStateOf<Float?>(null) }

            var installedDirs by remember { mutableStateOf<Set<String>>(emptySet()) }
            var busy by remember { mutableStateOf<Embedding.Model?>(null) }

            LaunchedEffect(modelDir) {
                installedDirs = scanModelDirs(modelDir)
            }

            suspend fun refresh() {
                installedDirs = withContext(Dispatchers.IO) { scanModelDirs(modelDir) }
            }

            suspend fun download(model: Embedding.Model) {
                downloadProgress = 0f

                dataService.downloadModel(model, modelDir) {
                    downloadProgress = it / 100f
                }

                refresh()

                downloadProgress = null
            }

            suspend fun remove(model: Embedding.Model) {
                withContext(Dispatchers.IO) {
                    val path = modelDir.resolve(model.name)

                    path.deleteRecursively()
                }

                refresh()
            }

            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp)
            ) {
                Text(
                    text = locale["model_management.title"],
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                val listState = rememberLazyListState()

                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(36.dp)
                    ) {
                        items(models) { model ->
                            val installed = installedDirs.contains(model.name) || installedDirs.contains(model.name)

                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f).padding(end = 16.dp)
                                    ) {
                                        Text(
                                            text = model.name,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "${locale["model_management.by", model.author]} • ${model.parameter}",
                                                fontSize = 12.sp,
                                                color = Color.Gray,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Text(
                                            text = locale[model.description],
                                            fontSize = 12.sp,
                                            color = Color.Gray,
                                            modifier = Modifier.padding(top = 6.dp)
                                        )
                                    }

                                    Button(
                                        modifier = Modifier.width(150.dp), enabled = busy == null, onClick = {
                                            scope.launch {
                                                busy = model

                                                runCatching { if (installed) remove(model) else download(model) }

                                                busy = null
                                            }
                                        }) {
                                        Text(if (installed) locale["model_management.remove"] else locale["model_management.download"])
                                    }
                                }

                                if (downloadProgress != null && (busy == model)) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                                    ) {
                                        LinearProgressIndicator(
                                            progress = downloadProgress ?: 0f, modifier = Modifier.fillMaxWidth()
                                        )

                                        Text(
                                            text = "${((downloadProgress ?: 0f) * 100).toInt()} %",
                                            fontSize = 12.sp,
                                            modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
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

private fun scanModelDirs(modelDir: Path): Set<String> {
    if (modelDir.notExists() || !modelDir.isDirectory()) return emptySet()

    return runCatching { modelDir.listDirectoryEntries() }.getOrDefault(emptyList()).filter { it.isDirectory() }
        .map { it.fileName.toString() }.toSet()
}