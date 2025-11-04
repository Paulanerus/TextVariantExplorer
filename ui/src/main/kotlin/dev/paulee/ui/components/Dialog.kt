package dev.paulee.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.unit.dp
import androidx.compose.ui.awt.AwtWindow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.paulee.ui.Config
import dev.paulee.ui.LocalI18n
import dev.paulee.ui.SimpleTextField
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

enum class DialogType {
    LOAD, SAVE
}

@Composable
fun FileDialog(
    parent: Frame? = null,
    dialogType: DialogType = DialogType.LOAD,
    extensions: List<String> = emptyList(),
    onCloseRequest: (result: List<Path>) -> Unit,
) {
    if (Config.useLegacyFileDialog) LegacyFileDialog(dialogType, extensions, onCloseRequest)
    else SystemFileDialog(parent, dialogType, extensions, onCloseRequest)
}

@Composable
fun SystemFileDialog(
    parent: Frame? = null,
    dialogType: DialogType = DialogType.LOAD,
    extensions: List<String> = emptyList(),
    onCloseRequest: (result: List<Path>) -> Unit,
) {
    val locale = LocalI18n.current
    val normalizedExtensions = remember(extensions) {
        extensions.mapNotNull { ext ->
            ext.trim().trimStart('.').takeIf { it.isNotBlank() }?.lowercase()
        }.distinct()
    }
    val defaultExtension = normalizedExtensions.firstOrNull()

    AwtWindow(
        create = {
            object :
                FileDialog(
                    parent,
                    if (dialogType == DialogType.SAVE) locale["dialog.save"] else locale["dialog.open"],
                    dialogType.ordinal
                ) {

                init {
                    isMultipleMode = dialogType == DialogType.LOAD

                    if (normalizedExtensions.isNotEmpty()) {
                        filenameFilter = FilenameFilter { _, name ->
                            val lower = name.lowercase()
                            normalizedExtensions.any { ext -> lower.endsWith(".${ext}") }
                        }

                        if (dialogType == DialogType.SAVE && defaultExtension != null) {
                            file = "untitled.$defaultExtension"
                        }
                    }
                }

                override fun setVisible(value: Boolean) {
                    super.setVisible(value)

                    if (value) {
                        val paths = files.map {
                            val path = it.toPath()
                            val lowerPath = path.toString().lowercase()

                            if (dialogType == DialogType.SAVE && defaultExtension != null &&
                                !normalizedExtensions.any { ext -> lowerPath.endsWith(".${ext}") }
                            ) {
                                Path.of("${path}.$defaultExtension")
                            } else {
                                path
                            }
                        }.toList()

                        onCloseRequest(paths)
                    }
                }
            }
        }, dispose = FileDialog::dispose
    )
}

@Composable
fun LegacyFileDialog(
    type: DialogType = DialogType.LOAD,
    extensions: List<String> = emptyList(),
    onCloseRequest: (result: List<Path>) -> Unit,
) {
    val locale = LocalI18n.current

    val title = if (type == DialogType.SAVE) locale["dialog.save"] else locale["dialog.open"]
    var closeResult by remember { mutableStateOf<List<Path>?>(null) }
    val normalizedExtensions = remember(extensions) {
        extensions.mapNotNull { ext ->
            ext.trim().trimStart('.').takeIf { it.isNotBlank() }?.lowercase()
        }.distinct()
    }

    val normalizedExtensionsSet = remember(normalizedExtensions) { normalizedExtensions.toSet() }
    val defaultExtension = normalizedExtensions.firstOrNull()

    closeResult?.let {
        LaunchedEffect(closeResult) { onCloseRequest(it) }
        return
    }

    fun JFileChooser.configure(initial: Boolean = true) {
        dialogTitle = title
        dialogType = if (type == DialogType.SAVE) JFileChooser.SAVE_DIALOG else JFileChooser.OPEN_DIALOG
        isMultiSelectionEnabled = type == DialogType.LOAD
        fileSelectionMode = JFileChooser.FILES_ONLY

        if (normalizedExtensions.isNotEmpty()) {
            val description = normalizedExtensions.joinToString(", ") { "*.$it" }
            val extensionFilter =
                FileNameExtensionFilter(description, *normalizedExtensions.toTypedArray())

            if (initial) {
                resetChoosableFileFilters()
                fileFilter = extensionFilter
                isAcceptAllFileFilterUsed = true
            } else {
                val filter = fileFilter
                val needsUpdate = filter !is FileNameExtensionFilter ||
                        filter.extensions.map { it.lowercase() }.toSet() != normalizedExtensionsSet

                if (needsUpdate) {
                    resetChoosableFileFilters()

                    fileFilter = extensionFilter
                    isAcceptAllFileFilterUsed = true
                }
            }
        }
    }

    Dialog(
        onDismissRequest = { closeResult = emptyList() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier.padding(16.dp),
            shape = MaterialTheme.shapes.medium,
            elevation = 8.dp
        ) {
            SwingPanel(
                modifier = Modifier.sizeIn(minWidth = 520.dp, minHeight = 420.dp),
                factory = {
                    JFileChooser().apply {
                        configure()

                        if (type == DialogType.SAVE && selectedFile == null) {
                            val baseName = buildString {
                                append("untitled")
                                defaultExtension?.let { append('.').append(it) }
                            }

                            selectedFile = File(currentDirectory, baseName)
                        }

                        addActionListener { event ->
                            val approved = event.actionCommand == JFileChooser.APPROVE_SELECTION

                            if (!approved) {
                                closeResult = emptyList()
                                return@addActionListener
                            }

                            val files =
                                if (isMultiSelectionEnabled) selectedFiles
                                else selectedFile?.let { arrayOf(it) }.orEmpty()

                            closeResult = files.mapNotNull { file ->
                                if (file == null) return@mapNotNull null

                                if (type == DialogType.SAVE && defaultExtension != null) {
                                    val hasAllowedExtension = normalizedExtensions.any { ext ->
                                        file.extension.equals(ext, ignoreCase = true)
                                    }

                                    val withExt =
                                        if (hasAllowedExtension) file
                                        else File(
                                            file.parentFile ?: currentDirectory,
                                            "${file.name}.$defaultExtension"
                                        )

                                    withExt.toPath()
                                } else file.toPath()
                            }
                        }
                    }
                },
                update = { it.configure(false) }
            )
        }
    }
}

@Composable
fun CustomInputDialog(
    title: String,
    placeholder: String,
    onDismissRequest: () -> Unit,
    onConfirmClick: (String) -> Unit,
    textFieldValue: String,
    onTextFieldValueChange: (String) -> Unit,
) {
    val locale = LocalI18n.current

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Surface(
            modifier = Modifier.widthIn(min = 280.dp), shape = MaterialTheme.shapes.medium, elevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                Text(
                    text = title, style = MaterialTheme.typography.h6,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                SimpleTextField(
                    textValue = textFieldValue,
                    onTextValueChange = onTextFieldValueChange,
                    placeholderText = placeholder,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text(locale["input.cancel"])
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { onConfirmClick(textFieldValue) }, enabled = textFieldValue.isNotBlank()
                    ) {
                        Text(locale["input.confirm"])
                    }
                }
            }
        }
    }
}

@Composable
fun YesNoDialog(
    title: String,
    text: String,
    onDismissRequest: () -> Unit,
    onResult: (Boolean) -> Unit,
) {
    val locale = LocalI18n.current

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Surface(
            modifier = Modifier.widthIn(min = 280.dp), shape = MaterialTheme.shapes.medium, elevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                Text(
                    text = title, style = MaterialTheme.typography.h6, modifier = Modifier.padding(bottom = 12.dp)
                )
                Text(
                    text = text, style = MaterialTheme.typography.body1, modifier = Modifier.padding(bottom = 24.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        onResult(false)
                        onDismissRequest()
                    }) {
                        Text(locale["dialog.no"])
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(onClick = {
                        onResult(true)
                        onDismissRequest()
                    }) {
                        Text(locale["dialog.yes"])
                    }
                }
            }
        }
    }
}
