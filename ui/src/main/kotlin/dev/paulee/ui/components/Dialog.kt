package dev.paulee.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.AwtWindow
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path

@Composable
fun FileDialog(
    parent: Frame? = null,
    onCloseRequest: (result: List<Path>) -> Unit
) = AwtWindow(
    create = {
        object : FileDialog(parent, "Choose a file", LOAD) {

            init {
                isMultipleMode = true
            }

            override fun setVisible(value: Boolean) {
                super.setVisible(value)

                if (value) {
                    val paths = files.map { it.toPath() }.toList()

                    onCloseRequest(paths)
                }
            }
        }
    },
    dispose = FileDialog::dispose
)