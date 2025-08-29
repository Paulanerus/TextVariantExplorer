package dev.paulee.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

object App {
    const val NAME = "TextVariant Explorer"

    const val APP_DIR = ".textexplorer"

    private val VERSION: String? by lazy { System.getProperty("app.version") }

    private val apiVersion: String? by lazy { System.getProperty("api.version") }

    private val coreVersion: String? by lazy { System.getProperty("core.version") }

    private val uiVersion: String? by lazy { System.getProperty("ui.version") }

    val VERSION_STRING = "v$VERSION (API - $apiVersion, Core - $coreVersion, UI - $uiVersion)"

    object Colors {

        val GREEN_HIGHLIGHT: java.awt.Color
            get() = java.awt.Color(0, 200, 83)

        val RED_HIGHLIGHT: java.awt.Color
            get() = java.awt.Color(200, 0, 0)

    }

    enum class ThemeMode { Light, Dark, System }

    object Theme {
        var mode by mutableStateOf(
            runCatching { ThemeMode.valueOf(Config.theme) }.getOrElse { ThemeMode.System }
        )
            private set

        fun set(mode: ThemeMode) {
            this.mode = mode
            Config.theme = mode.name
        }

        private val LightColors = null

        private val DarkColors = null

        @Composable
        fun Current(content: @Composable () -> Unit) {
            val useDarkTheme = when (mode) {
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
                ThemeMode.System -> isSystemInDarkTheme()
            }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
                    content()
                }
            }
        }
    }
}