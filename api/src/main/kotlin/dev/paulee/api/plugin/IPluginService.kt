package dev.paulee.api.plugin

import dev.paulee.api.data.IDataService
import dev.paulee.api.data.ViewFilter
import java.nio.file.Path

interface IPluginService {

    fun loadPlugin(path: Path): IPlugin?

    fun getPluginMetadata(plugin: IPlugin): PluginMetadata?

    fun getDataInfo(plugin: IPlugin): String?

    fun initAll(dataService: IDataService)

    fun getPlugins(): List<IPlugin>

    fun getViewFilter(plugin: IPlugin): ViewFilter?
}