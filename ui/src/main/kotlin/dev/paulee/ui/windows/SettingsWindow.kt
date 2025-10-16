package dev.paulee.ui.windows

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import dev.paulee.ui.App
import dev.paulee.ui.Config
import dev.paulee.ui.LocalI18n
import dev.paulee.ui.components.ButtonDropDown

@Composable
fun SettingsWindow(onClose: () -> Unit) {
    val windowState = rememberWindowState(
        position = WindowPosition.Aligned(Alignment.Center),
        size = DpSize(600.dp, 600.dp)
    )

    val colorModes = App.ThemeMode.entries.associate { "setting.theme.${it.name.lowercase()}" to it.name }
    var selectedTheme by remember { mutableStateOf("setting.theme.${App.Theme.mode.name.lowercase()}") }

    val supportedLanguages = App.SupportedLanguage.entries.map { it.name }
    var selectedLanguage by remember { mutableStateOf(App.Language.current) }

    val locale = LocalI18n.current

    Window(
        state = windowState,
        icon = App.icon,
        onCloseRequest = {
            Config.save()
            onClose()
        },
        title = locale["settings.title"],
    ) {
        App.Theme.Current {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Text(
                    text = locale["settings.title"],
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    SettingRow(
                        label = locale["settings.language.label"],
                        description = locale["settings.language.desc"]
                    ) {
                        ButtonDropDown(
                            items = supportedLanguages,
                            selected = selectedLanguage.name,
                            menuOffset = DpOffset(x = (-6).dp, y = 0.dp)
                        ) {
                            val lang = App.SupportedLanguage.valueOf(it)

                            selectedLanguage = lang
                            App.Language.set(lang)
                        }
                    }

                    SettingRow(
                        label = locale["settings.theme.label"],
                        description = locale["settings.theme.desc"]
                    ) {
                        ButtonDropDown(
                            items = colorModes.keys.toList(),
                            selected = selectedTheme,
                            menuOffset = DpOffset(x = (-6).dp, y = 0.dp)

                        ) {
                            val mode = App.ThemeMode.valueOf(colorModes[it] ?: App.ThemeMode.System.name)

                            selectedTheme = it
                            App.Theme.set(mode)
                        }
                    }

                    SettingRow(
                        label = locale["settings.width_limit.label"],
                        description = locale["settings.width_limit.desc"]
                    ) {
                        Switch(
                            checked = Config.noWidthRestriction,
                            onCheckedChange = { Config.noWidthRestriction = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colors.primary
                            )
                        )
                    }

                    SettingRow(
                        label = locale["settings.exact_highlighting.label"],
                        description = locale["settings.exact_highlighting.desc"]
                    ) {
                        Switch(
                            checked = Config.exactHighlighting,
                            onCheckedChange = { Config.exactHighlighting = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colors.primary
                            )
                        )
                    }

                    SettingRow(
                        label = locale["settings.legacy_filer_dialog.label"],
                        description = locale["settings.legacy_filer_dialog.desc"]
                    ) {
                        Switch(
                            checked = Config.useLegacyFileDialog,
                            onCheckedChange = { Config.useLegacyFileDialog = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colors.primary
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(
    label: String,
    description: String,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(
                text = label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        content()
    }
}