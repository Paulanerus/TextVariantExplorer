package dev.paulee.core.plugin

import dev.paulee.api.data.DataInfo
import dev.paulee.api.data.IDataService
import dev.paulee.api.data.RequiresData
import dev.paulee.api.data.ViewFilter
import dev.paulee.api.data.provider.IStorageProvider
import dev.paulee.api.plugin.*
import org.slf4j.LoggerFactory.getLogger
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.walk
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions
import kotlin.reflect.full.valueParameters

class PluginServiceImpl : IPluginService {

    private val logger = getLogger(PluginServiceImpl::class.java)

    private val plugins = mutableListOf<IPlugin>()

    override fun loadFromDirectory(path: Path): Int {
        if (!path.isDirectory()) {
            this.logger.warn("$path is not a directory.")
            return -1
        }

        val amount = path.walk().filter { it.extension == "jar" }.mapNotNull { this.loadPlugin(it) }.count()

        if (amount == 0) this.logger.info("No plugins were loaded.")
        else this.logger.info("Loaded $amount ${if (amount == 1) "plugin" else "plugins"}.")

        return amount
    }

    override fun loadPlugin(path: Path): IPlugin? {
        if (path.extension != "jar") {
            this.logger.warn("'$path' is not a jar file.")
            return null
        }

        return URLClassLoader(arrayOf(path.toUri().toURL()), this.javaClass.classLoader).use { classLoader ->
            val entryPoint = this.getPluginEntryPoint(path)

            if (entryPoint == null) {
                this.logger.warn("Plugin '$path' entrypoint is missing.")
                return@use null
            }

            val plugin = entryPoint.let { runCatching { Class.forName(it, true, classLoader) }.getOrNull() }
                ?.takeIf { IPlugin::class.java.isAssignableFrom(it) }
                ?.let { runCatching { it.declaredConstructors.first() }.getOrNull() }?.newInstance() as? IPlugin

            if (plugin == null) {
                this.logger.warn("'$path' is not a plugin.")
                return@use null
            }

            if (this.invalidInitFunc(plugin::class)) {
                this.logger.warn("Invalid function structure.")
                return@use null
            }

            this.collectClasses(path).filter { it != entryPoint }
                .forEach { runCatching { Class.forName(it, true, classLoader) } }

            val metadata = this.getPluginMetadata(plugin)

            if (metadata == null) {
                this.logger.warn("Plugin '$path' is missing PluginMetadata.")
                return@use null
            }

            if (metadata.name.isBlank() || metadata.version.isBlank()) {
                this.logger.warn("Plugin '$path' is missing name and/or version.")
                return@use null
            }

            if (this.getDataInfo(plugin).isNullOrBlank()) {
                this.logger.warn("Plugin '$path' is missing data info annotation.")
                return@use null
            }

            this.plugins.add(plugin)

            this.logger.info("Loaded plugin (${metadata.name}).")

            return@use plugin
        }
    }

    override fun getPluginMetadata(plugin: IPlugin): PluginMetadata? = plugin::class.findAnnotation<PluginMetadata>()

    override fun getDataInfo(plugin: IPlugin): String? = plugin::class.findAnnotation<RequiresData>()?.name

    override fun initAll(dataService: IDataService, path: Path) {
        this.plugins.sortBy { it::class.findAnnotation<PluginOrder>()?.order ?: 0 }

        this.plugins.forEach {
            val dataInfo = this.getDataInfo(it) ?: return@forEach

            val provider = dataService.createStorageProvider(dataInfo, path.resolve(dataInfo)) ?: return@forEach

            runCatching { it.init(provider) }.getOrElse { e ->
                this.logger.error(
                    "Exception: Failed to initialize plugin.", e
                )
            }
        }

        this.logger.info("Initialized plugins.")
    }

    override fun getPlugins(): List<IPlugin> = this.plugins.toList()

    override fun getViewFilter(plugin: IPlugin): ViewFilter? {
        val taggable = plugin as? Taggable ?: return null

        return taggable::class.functions.find { it.name == "tag" }?.findAnnotation<ViewFilter>()
    }

    override fun getVariants(dataInfo: DataInfo?): Set<String> = dataInfo?.sources.orEmpty().mapNotNull {
        /*
        val dataSource = it.findAnnotation<DataSource>() ?: return@mapNotNull null

        if (it.hasAnnotation<Variant>()) dataSource.file
        else null

         */
        null
    }.toSet()

    override fun getPreFilters(dataInfo: DataInfo?): Set<String> = dataInfo?.sources.orEmpty().mapNotNull {
        /*
        val dataSource = it.findAnnotation<DataSource>() ?: return@mapNotNull null

        if (it.hasAnnotation<PreFilter>()) dataSource.file
        else null
        */
        null
    }.toSet()

    private fun getPluginEntryPoint(path: Path): String? =
        JarFile(path.toFile()).use { return it.manifest.mainAttributes.getValue("Main-Class") }

    private fun collectClasses(path: Path): Set<String> = JarFile(path.toFile()).use { jar ->
        jar.entries().asSequence().filter { !it.isDirectory && it.name.endsWith(".class") }
            .map { it.name.replace("/", ".").substring(0, it.name.length - 6) }.toSet()
    }

    private fun invalidInitFunc(pluginKlass: KClass<out IPlugin>): Boolean {
        val func = pluginKlass.declaredFunctions.find { it.name == "init" } ?: return true

        return func.returnType.classifier != Unit::class || func.valueParameters.size != 1 || func.valueParameters.first().type.classifier != IStorageProvider::class
    }
}