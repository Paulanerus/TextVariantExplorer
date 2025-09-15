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
import java.awt.Color
import java.util.*

internal object App {
    const val NAME = "TextVariant Explorer"

    private val VERSION: String? by lazy { System.getProperty("app.version") }

    private val apiVersion: String? by lazy { System.getProperty("api.version") }

    private val coreVersion: String? by lazy { System.getProperty("core.version") }

    private val uiVersion: String? by lazy { System.getProperty("ui.version") }

    val VERSION_STRING = "v$VERSION (API - $apiVersion, Core - $coreVersion, UI - $uiVersion)"

    val icon by lazy { readBitmapResource("icon.png") }

    enum class SupportedLanguage(val tag: String) {
        Deutsch("de"),
        English("en");

        val locale: Locale = Locale.forLanguageTag(tag)

        companion object {
            fun fromTag(tag: String?): SupportedLanguage = entries.firstOrNull { it.tag == tag } ?: English
        }
    }

    object Language {
        var current by mutableStateOf(SupportedLanguage.fromTag(Config.lang))
            private set

        fun set(lang: SupportedLanguage) {
            this.current = lang
            Config.lang = lang.tag
        }
    }

    object Colors {

        val GREEN_HIGHLIGHT: Color
            get() = Color(0, 200, 83)

        val RED_HIGHLIGHT: Color
            get() = Color(200, 0, 0)

    }

    enum class ThemeMode { Light, Dark, System }

    object Theme {
        var mode by mutableStateOf(runCatching { ThemeMode.valueOf(Config.theme) }.getOrElse { ThemeMode.System })
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
                    WithLangScope(Language.current.locale) {
                        content()
                    }
                }
            }
        }
    }
}