package dev.paulee.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

class TextExplorerUI {

    @Composable
    private fun content() {
        var text by remember { mutableStateOf("") }

        MaterialTheme {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Text("Text Explorer", fontSize = 32.sp)

                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text("Search...") },
                        modifier = Modifier
                            .width(600.dp)
                            .background(
                                color = Color.LightGray,
                                shape = RoundedCornerShape(24.dp),
                            ),
                        colors = TextFieldDefaults.textFieldColors(
                            backgroundColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )

                    IconButton(
                        onClick = { println(text) },
                        modifier = Modifier
                            .height(70.dp)
                            .padding(horizontal = 10.dp),
                        enabled = text.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                }
            }
        }
    }

    fun start() = application {
        val windowState =
            rememberWindowState(position = WindowPosition.Aligned(Alignment.Center), size = DpSize(1600.dp, 900.dp))

        Window(title = "TextExplorer", state = windowState, onCloseRequest = ::exitApplication) {
            content()
        }
    }
}