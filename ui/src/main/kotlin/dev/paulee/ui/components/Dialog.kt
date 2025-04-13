package dev.paulee.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.AwtWindow
import java.awt.FileDialog
import java.awt.Frame
import java.io.FilenameFilter
import java.nio.file.Path

enum class DialogType {
    LOAD,
    SAVE
}

@Composable
fun FileDialog(
    parent: Frame? = null,
    dialogType: DialogType = DialogType.LOAD,
    extension: String? = null,
    onCloseRequest: (result: List<Path>) -> Unit
) = AwtWindow(
    create = {
        object :
            FileDialog(parent, if (dialogType == DialogType.SAVE) "Save file" else "Open file", dialogType.ordinal) {

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
    },
    dispose = FileDialog::dispose
)