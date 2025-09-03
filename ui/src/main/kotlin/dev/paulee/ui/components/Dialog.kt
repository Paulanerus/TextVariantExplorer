package dev.paulee.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.AwtWindow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.paulee.ui.LocalI18n
import dev.paulee.ui.SimpleTextField
import java.awt.FileDialog
import java.awt.Frame
import java.io.FilenameFilter
import java.nio.file.Path

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
    val locale = LocalI18n.current

    AwtWindow(
        create = {
            object :
                FileDialog(parent, if (dialogType == DialogType.SAVE) locale["dialog.save"] else locale["dialog.open"], dialogType.ordinal) {

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
