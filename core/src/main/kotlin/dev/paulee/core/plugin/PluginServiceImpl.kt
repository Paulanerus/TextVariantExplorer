package dev.paulee.core.plugin

import dev.paulee.api.plugin.IPlugin
import dev.paulee.api.plugin.IPluginService
import dev.paulee.api.plugin.PluginMetadata
import dev.paulee.api.plugin.PluginOrder
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.walk
import kotlin.reflect.full.findAnnotation

class PluginServiceImpl : IPluginService {

    val plugins = mutableListOf<IPlugin>()

    @OptIn(ExperimentalPathApi::class)
    override fun loadFromDirectory(path: Path): Int {
        require(path.isDirectory())

        return path.walk()
            .filter { it.extension == "jar" }
            .mapNotNull { if (this.loadPlugin(it)) it else null }
            .count()
    }

    override fun loadPlugin(path: Path): Boolean {
        require(path.extension == "jar")

        return URLClassLoader(arrayOf(path.toUri().toURL()), this.javaClass.classLoader).use { classLoader ->
            val plugin =
                this.getPluginEntryPoint(path)?.let { runCatching { Class.forName(it, true, classLoader) }.getOrNull() }
                    ?.takeIf { IPlugin::class.java.isAssignableFrom(it) }
                    ?.let { runCatching { it.declaredConstructors.first() }.getOrNull() }
                    ?.newInstance() as? IPlugin

            return plugin?.let {
                this.plugins.add(it)
            } == true
        }
    }

    override fun getPluginMetadata(plugin: IPlugin): PluginMetadata? {
        return plugin::class.findAnnotation<PluginMetadata>()
    }

    override fun initAll() {
        this.plugins.sortBy { it::class.findAnnotation<PluginOrder>()?.order ?: 0 }

        this.plugins.forEach { it.init() }
    }

    private fun getPluginEntryPoint(path: Path): String? =
        JarFile(path.toFile()).use { return it.manifest.mainAttributes.getValue("Main-Class") }
}