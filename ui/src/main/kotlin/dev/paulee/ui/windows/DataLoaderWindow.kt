package dev.paulee.ui.windows

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.onClick
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import dev.paulee.ui.components.FileDialog
import java.nio.file.Path
import kotlin.io.path.name

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DataLoaderWindow(onClose: () -> Unit) {
    val windowState =
        rememberWindowState(position = WindowPosition.Aligned(Alignment.Center), size = DpSize(700.dp, 500.dp))

    var toggleDialog by remember { mutableStateOf(false) }
    val files = remember { mutableStateListOf<Path>() }
    var selectedFile by remember { mutableStateOf<Path?>(null) }

    Window(state = windowState, onCloseRequest = onClose, title = "Data Import") {
        MaterialTheme {
            Box(modifier = Modifier.fillMaxSize()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.align(Alignment.Center)) {
                    files.forEach { file ->
                        Text(
                            file.asName(),
                            modifier = Modifier.onClick { selectedFile = file },
                            fontWeight = if (file == selectedFile) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                Column(modifier = Modifier.align(Alignment.TopEnd).padding(all = 16.dp)) {
                    Button(onClick = { toggleDialog = true }, modifier = Modifier.width(100.dp)) {
                        Text("Add")
                    }

                    Button(
                        onClick = {
                            selectedFile?.let { file -> files.remove(file) }
                            selectedFile = null
                        },
                        enabled = selectedFile != null,
                        modifier = Modifier.width(100.dp)
                    ) {
                        Text("Remove")
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(onClick = { println("Import") }, modifier = Modifier.width(100.dp)) {
                        Text("Import")
                    }

                    Button(onClick = { println("Export") }, enabled = false, modifier = Modifier.width(100.dp)) {
                        Text("Export")
                    }
                }
            }

            if (toggleDialog) {
                FileDialog { paths ->
                    files.addAll(paths.filter { it !in files }).also { toggleDialog = false }
                }
            }
        }
    }
}

private fun Path.asName(): String = this.name.substringBeforeLast(".")