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

    private val plugins = mutableListOf<IPlugin>()

    @OptIn(ExperimentalPathApi::class)
    override fun loadFromDirectory(path: Path): Int {
        if (!path.isDirectory()) return -1

        return path.walk().filter { it.extension == "jar" }.mapNotNull { if (this.loadPlugin(it)) it else null }.count()
    }

    override fun loadPlugin(path: Path, init: Boolean): Boolean {
        if (path.extension != "jar") return false

        return URLClassLoader(arrayOf(path.toUri().toURL()), this.javaClass.classLoader).use { classLoader ->
            val entryPoint = this.getPluginEntryPoint(path)

            val plugin = entryPoint?.let { runCatching { Class.forName(it, true, classLoader) }.getOrNull() }
                ?.takeIf { IPlugin::class.java.isAssignableFrom(it) }
                ?.let { runCatching { it.declaredConstructors.first() }.getOrNull() }?.newInstance() as? IPlugin

            plugin?.let {
                this.collectClasses(path).filter { it != entryPoint }
                    .forEach { runCatching { Class.forName(it, true, classLoader) } }

                if (init) plugin.init()

                this.plugins.add(plugin)
            } == true
        }
    }

    override fun getPluginMetadata(plugin: IPlugin): PluginMetadata? = plugin::class.findAnnotation<PluginMetadata>()

    override fun initAll() {
        this.plugins.sortBy { it::class.findAnnotation<PluginOrder>()?.order ?: 0 }

        this.plugins.forEach { it.init() }
    }

    override fun getPlugins(): List<IPlugin> = this.plugins.toList()

    private fun getPluginEntryPoint(path: Path): String? =
        JarFile(path.toFile()).use { return it.manifest.mainAttributes.getValue("Main-Class") }

    private fun collectClasses(path: Path): Set<String> = JarFile(path.toFile()).use { jar ->
        jar.entries().asSequence().filter { !it.isDirectory && it.name.endsWith(".class") }
            .map { it.name.replace("/", ".").substring(0, it.name.length - 6) }.toSet()
    }
}