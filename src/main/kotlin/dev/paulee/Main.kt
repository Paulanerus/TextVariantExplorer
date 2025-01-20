package dev.paulee

import dev.paulee.core.data.DataServiceImpl
import dev.paulee.core.data.DiffServiceImpl
import dev.paulee.core.plugin.PluginServiceImpl
import dev.paulee.ui.TextExplorerUI

fun main() {
    val explorerUI = TextExplorerUI(
        PluginServiceImpl(),
        DataServiceImpl(),
        DiffServiceImpl())
    explorerUI.start()
}