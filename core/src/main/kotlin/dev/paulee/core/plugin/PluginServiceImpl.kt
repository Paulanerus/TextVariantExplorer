package dev.paulee.core.plugin

import dev.paulee.api.data.*
import dev.paulee.api.plugin.*
import dev.paulee.core.normalizeDataSource
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.walk
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.primaryConstructor

class PluginServiceImpl : IPluginService {

    private val plugins = mutableListOf<IPlugin>()

    @OptIn(ExperimentalPathApi::class)
    override fun loadFromDirectory(path: Path): Int {
        if (!path.isDirectory()) return -1

        return path.walk().filter { it.extension == "jar" }.mapNotNull { this.loadPlugin(it) }.count()
    }

    override fun loadPlugin(path: Path, init: Boolean): IPlugin? {
        if (path.extension != "jar") return null

        return URLClassLoader(arrayOf(path.toUri().toURL()), this.javaClass.classLoader).use { classLoader ->
            val entryPoint = this.getPluginEntryPoint(path)

            val plugin = entryPoint?.let { runCatching { Class.forName(it, true, classLoader) }.getOrNull() }
                ?.takeIf { IPlugin::class.java.isAssignableFrom(it) }
                ?.let { runCatching { it.declaredConstructors.first() }.getOrNull() }?.newInstance() as? IPlugin

            plugin?.let {
                this.collectClasses(path).filter { it != entryPoint }
                    .forEach { runCatching { Class.forName(it, true, classLoader) } }

                val metadata = plugin::class.findAnnotation<PluginMetadata>() ?: return null

                if (metadata.name.isBlank() || metadata.version.isBlank()) return null

                if (plugin::class.findAnnotation<RequiresData>()?.name.isNullOrBlank()) return null

                if (init) plugin.init()

                this.plugins.add(plugin)

                return plugin
            }
        }
    }

    override fun getPluginMetadata(plugin: IPlugin): PluginMetadata? = plugin::class.findAnnotation<PluginMetadata>()

    override fun getDataInfo(plugin: IPlugin): RequiresData? = plugin::class.findAnnotation<RequiresData>()

    override fun initAll() {
        this.plugins.sortBy { it::class.findAnnotation<PluginOrder>()?.order ?: 0 }

        this.plugins.forEach { it.init() }
    }

    override fun getPlugins(): List<IPlugin> = this.plugins.toList()

    override fun getAllDataInfos(): Set<RequiresData> = this.plugins.mapNotNull { this.getDataInfo(it) }.toSet()

    override fun getDataSources(datInfo: String): Set<String> {
        val dataSources = mutableSetOf<String>()

        this.plugins.mapNotNull { this.getDataInfo(it) }
            .filter { it.name == datInfo }
            .map { it.sources }
            .forEach {
                it.forEach { clazz ->
                    clazz.findAnnotation<DataSource>()?.file?.let { dataSources.add(normalizeDataSource(it)) }
                }
            }

        return dataSources
    }

    override fun tagFields(plugin: IPlugin, field: String, value: String): Map<String, Tag> =
        (plugin as? Taggable)?.tag(field, value) ?: emptyMap()

    override fun getViewFilter(plugin: IPlugin): ViewFilter? {
        val taggable = plugin as? Taggable ?: return null

        val func = taggable::class.functions.find { it.name == "tag" } ?: return null

        val annotation = func.findAnnotation<ViewFilter>() ?: return null

        val fields = getAllFields(plugin)

        if (fields.isEmpty()) return annotation

        if (annotation.fields.none { it in fields }) return null

        return annotation
    }

    override fun getVariants(dataInfo: RequiresData?): Set<String> =
        dataInfo?.sources.orEmpty().mapNotNull {
            val dataSource = it.findAnnotation<DataSource>() ?: return@mapNotNull null

            if (it.hasAnnotation<Variant>()) dataSource.file
            else null
        }.toSet()

    override fun getPreFilters(dataInfo: RequiresData?): Set<String> =
        dataInfo?.sources.orEmpty().mapNotNull {
            val dataSource = it.findAnnotation<DataSource>() ?: return@mapNotNull null

            if (it.hasAnnotation<PreFilter>()) dataSource.file
            else null
        }.toSet()

    private fun getAllFields(plugin: IPlugin): Set<String> = this.getDataInfo(plugin)?.sources.orEmpty()
        .flatMap { it.primaryConstructor?.parameters.orEmpty().mapNotNull { it.name } }.toSet()

    private fun getPluginEntryPoint(path: Path): String? =
        JarFile(path.toFile()).use { return it.manifest.mainAttributes.getValue("Main-Class") }

    private fun collectClasses(path: Path): Set<String> = JarFile(path.toFile()).use { jar ->
        jar.entries().asSequence().filter { !it.isDirectory && it.name.endsWith(".class") }
            .map { it.name.replace("/", ".").substring(0, it.name.length - 6) }.toSet()
    }
}