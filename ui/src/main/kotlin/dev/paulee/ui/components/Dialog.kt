package dev.paulee.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.AwtWindow
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
    extension: String? = null,
    onCloseRequest: (result: List<Path>) -> Unit,
) {
    if (Config.useLegacyFileDialog) LegacyFileDialog(dialogType, extension, onCloseRequest)
    else SystemFileDialog(parent, dialogType, extension, onCloseRequest)
}

@Composable
fun SystemFileDialog(
    parent: Frame? = null,
    dialogType: DialogType = DialogType.LOAD,
    extension: String? = null,
    onCloseRequest: (result: List<Path>) -> Unit,
) {
    val locale = LocalI18n.current

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

                    if (!extension.isNullOrBlank()) {
                        filenameFilter = FilenameFilter { _, name ->
                            name.endsWith(".$extension", ignoreCase = true)
                        }

                        if (dialogType == DialogType.SAVE) file = "untitled"
                    }
                }

                override fun setVisible(value: Boolean) {
                    super.setVisible(value)

                    if (value) {
                        val paths = files.map {
                            val path = it.toPath()

                            if (dialogType == DialogType.SAVE && extension != null && !path.toString().lowercase()
                                    .endsWith(".$extension")
                            ) {
                                Path.of("${path}.$extension")
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
    extension: String? = null,
    onCloseRequest: (result: List<Path>) -> Unit,
) {
    val locale = LocalI18n.current

    val title = if (type == DialogType.SAVE) locale["dialog.save"] else locale["dialog.open"]
    var closeResult by remember { mutableStateOf<List<Path>?>(null) }

    closeResult?.let {
        LaunchedEffect(closeResult) { onCloseRequest(it) }
        return
    }

    val normalizedExtension = extension?.trim()?.lowercase()?.takeIf { it.isNotBlank() }

    fun JFileChooser.configure(initial: Boolean = true) {
        dialogTitle = title
        dialogType = if (type == DialogType.SAVE) JFileChooser.SAVE_DIALOG else JFileChooser.OPEN_DIALOG
        isMultiSelectionEnabled = type == DialogType.LOAD
        fileSelectionMode = JFileChooser.FILES_ONLY

        normalizedExtension?.let { ext ->
            val extensionFilter = FileNameExtensionFilter("*.$ext", ext)

            if (initial) {
                fileFilter = extensionFilter
                isAcceptAllFileFilterUsed = true

            } else {
                val filter = fileFilter

                if (filter !is FileNameExtensionFilter || filter.extensions.any { it.equals(ext, true) }) {
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
                                normalizedExtension?.let { append('.').append(it) }
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

                                if (type == DialogType.SAVE && normalizedExtension != null) {

                                    val withExt = if (file.extension == normalizedExtension) file
                                    else File(file.parentFile ?: currentDirectory, "${file.name}.$normalizedExtension")

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
