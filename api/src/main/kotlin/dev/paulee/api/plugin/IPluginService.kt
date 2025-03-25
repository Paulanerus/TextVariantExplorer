package dev.paulee.api.plugin

import dev.paulee.api.data.DataInfo
import dev.paulee.api.data.IDataService
import dev.paulee.api.data.ViewFilter
import java.nio.file.Path

interface IPluginService {

    fun loadFromDirectory(path: Path): Int

    fun loadPlugin(path: Path): IPlugin?

    fun getPluginMetadata(plugin: IPlugin): PluginMetadata?

    fun getDataInfo(plugin: IPlugin): String?

    fun initAll(dataService: IDataService, path: Path)

    fun getPlugins(): List<IPlugin>

    fun getViewFilter(plugin: IPlugin): ViewFilter?

    fun getVariants(dataInfo: DataInfo?): Set<String>

    fun getPreFilters(dataInfo: DataInfo?): Set<String>
}