package dev.paulee.ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

class VariantUI {

    @Composable
    @Preview
    private fun content() {
        MaterialTheme {
            Button(onClick = {}) {
                Text("Click")
            }
        }
    }

    fun start() = application {
        Window(onCloseRequest = ::exitApplication) {
            content()
        }
    }
}