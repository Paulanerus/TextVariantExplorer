package dev.paulee

import dev.paulee.api.data.provider.StorageType
import dev.paulee.core.data.DataServiceImpl
import dev.paulee.core.data.provider.StorageProvider
import dev.paulee.core.plugin.PluginServiceImpl
import dev.paulee.ui.TextExplorerUI

fun main() {
    val explorerUI = TextExplorerUI(PluginServiceImpl(), DataServiceImpl(StorageProvider.of(StorageType.SQLITE)))
    explorerUI.start()
}