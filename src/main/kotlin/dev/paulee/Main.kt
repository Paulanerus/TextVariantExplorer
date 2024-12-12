package dev.paulee

import dev.paulee.core.plugin.PluginServiceImpl
import dev.paulee.ui.TextExplorerUI


fun main() {
    val explorerUI = TextExplorerUI(PluginServiceImpl())
    explorerUI.start()
}