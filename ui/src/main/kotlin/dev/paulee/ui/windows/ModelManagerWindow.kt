package dev.paulee.ui.windows

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import dev.paulee.ui.App
import dev.paulee.ui.Config
import dev.paulee.ui.LocalI18n

@Composable
fun ModelManagerWindow(onClose: () -> Unit) {
    val locale = LocalI18n.current

    val windowState = rememberWindowState(
        position = WindowPosition.Aligned(Alignment.Center),
        size = DpSize(600.dp, 600.dp)
    )

    Window(
        state = windowState,
        onCloseRequest = {
            Config.save()
            onClose()
        },
        title = locale["model_management.title"],
    ) {
        App.Theme.Current {
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
            }
        }
    }
}