package dev.paulee.api.plugin

import dev.paulee.api.data.RequiresData
import java.nio.file.Path

interface IPluginService {

    fun loadFromDirectory(path: Path): Int

    fun loadPlugin(path: Path, init: Boolean = false): IPlugin?

    fun getPluginMetadata(plugin: IPlugin): PluginMetadata?

    fun getDataInfo(plugin: IPlugin): RequiresData?

    fun initAll()

    fun getPlugins(): List<IPlugin>

    fun getAllDataInfos(): Set<String>

    fun getDataSources(dataInfo: String): Set<String>
}