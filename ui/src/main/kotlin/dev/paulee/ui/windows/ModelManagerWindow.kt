package dev.paulee.ui.windows

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.Button
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
import dev.paulee.api.internal.Embedding
import dev.paulee.ui.App
import dev.paulee.ui.Config
import dev.paulee.ui.LocalI18n
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.*

@OptIn(ExperimentalPathApi::class)
@Composable
fun ModelManagerWindow(modelDir: Path, onClose: () -> Unit) {
    val locale = LocalI18n.current

    val windowState = rememberWindowState(
        position = WindowPosition.Aligned(Alignment.Center),
        size = DpSize(800.dp, 600.dp)
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
            val models = remember { Embedding.Models.entries }

            var installedDirs by remember { mutableStateOf<Set<String>>(emptySet()) }
            val busy = remember { mutableStateMapOf<Embedding.Models, Boolean>() }

            fun markBusy(m: Embedding.Models, v: Boolean) = if (v) busy[m] = true else busy.remove(m)

            LaunchedEffect(modelDir) {
                installedDirs = scanModelDirs(modelDir)
            }

            suspend fun refresh() {
                installedDirs = withContext(Dispatchers.IO) { scanModelDirs(modelDir) }
            }

            suspend fun download(model: Embedding.Models) {
                withContext(Dispatchers.IO) {

                    delay(4000)

                    // TODO: download and save model
                }

                refresh()
            }

            suspend fun remove(model: Embedding.Models) {
                withContext(Dispatchers.IO) {
                    val path = modelDir.resolve(model.name)

                    path.deleteRecursively()
                }

                refresh()
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
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
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(models) { model ->
                            val installed = installedDirs.contains(model.name) || installedDirs.contains(model.name)

                            val isBusy = busy[model] == true

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 16.dp)
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
                                        text = model.description,
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }

                                Button(
                                    modifier = Modifier.width(150.dp),
                                    enabled = !isBusy,
                                    onClick = {
                                        scope.launch {
                                            markBusy(model, true)

                                            runCatching { if (installed) remove(model) else download(model) }

                                            markBusy(model, false)
                                        }
                                    }
                                ) {
                                    Text(if (installed) locale["model_management.remove"] else locale["model_management.download"])
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