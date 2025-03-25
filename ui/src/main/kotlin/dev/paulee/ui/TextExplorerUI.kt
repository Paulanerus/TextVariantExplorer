package dev.paulee.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.paulee.api.data.DiffService
import dev.paulee.api.data.IDataService
import dev.paulee.api.plugin.IPluginService
import dev.paulee.ui.components.DropDownMenu
import dev.paulee.ui.components.FileDialog
import dev.paulee.ui.components.TableView
import dev.paulee.ui.components.widthLimitWrapper
import dev.paulee.ui.windows.DataLoaderWindow
import dev.paulee.ui.windows.DiffViewerWindow
import dev.paulee.ui.windows.PluginInfoWindow
import java.nio.file.Path
import kotlin.io.path.*

enum class Window {
    NONE,
    LOAD_PLUGIN,
    LOAD_DATA,
    DIFF,
    PLUGIN_INFO
}

class TextExplorerUI(
    private val pluginService: IPluginService,
    private val dataService: IDataService,
    private val diffService: DiffService,
) {

    private val appDir = Path(System.getProperty("user.home")).resolve(".textexplorer")

    private val pluginsDir = appDir.resolve("plugins")

    private val dataDir = appDir.resolve("data")

    private val versionString = "v${System.getProperty("app.version")} ${
        listOf("api", "core", "ui").joinToString(
            prefix = "(", postfix = ")", separator = ", "
        ) {
            "${it.uppercase()} - ${System.getProperty("${it}.version")}"
        }
    }"

    private var poolSelected by mutableStateOf(false)

    init {
        if (!this.pluginsDir.exists()) this.pluginsDir.createDirectories()

        Config.load(this.appDir)

        this.pluginService.loadFromDirectory(this.pluginsDir)
        this.dataService.loadDataPools(this.dataDir)

        this.pluginService.initAll(this.dataService, this.dataDir)

        if (Config.selectedPool in this.dataService.getAvailablePools()) this.dataService.selectDataPool(Config.selectedPool)
    }

    @Composable
    private fun content() {
        var text by remember { mutableStateOf("") }
        var selectedRows by remember { mutableStateOf(listOf<Map<String, String>>()) }
        var openWindow by remember { mutableStateOf(Window.NONE) }
        var showTable by remember { mutableStateOf(false) }
        var showPopup by remember { mutableStateOf(false) }
        var totalPages by remember { mutableStateOf(0L) }
        var amount by remember { mutableStateOf(0L) }
        var currentPage by remember { mutableStateOf(0) }

        var selectedText = remember(this.poolSelected) {
            val (pool, field) = dataService.getSelectedPool().split(".", limit = 2)

            if (pool == "null") "No source available"
            else "$pool ($field)"
        }

        var header by remember { mutableStateOf(listOf<String>()) }
        var indexStrings by remember { mutableStateOf(emptySet<String>()) }
        var data by remember { mutableStateOf(listOf<List<String>>()) }
        var links by remember { mutableStateOf(mapOf<String, List<Map<String, String>>>()) }

        MaterialTheme {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.align(Alignment.TopStart).padding(25.dp)) {
                    Text(
                        selectedText,
                        modifier = Modifier.padding(2.dp)
                            .then(if (dataService.getAvailablePools().size > 1) Modifier.clickable { showPopup = true }
                            else Modifier))

                    DropdownMenu(
                        expanded = showPopup, onDismissRequest = { showPopup = false }) {
                        val menuItems = dataService.getAvailablePools().map {
                            val (p, f) = it.split(".", limit = 2)
                            Pair(it, "$p ($f)")
                        }
                        menuItems.forEach { item ->
                            DropdownMenuItem(
                                onClick = {
                                    dataService.selectDataPool(item.first)
                                    Config.selectedPool = item.first
                                    poolSelected = !poolSelected

                                    if (selectedText != item.second) {
                                        text = ""
                                        showTable = false
                                    }

                                    selectedText = item.second
                                    showPopup = false
                                }) {
                                Text(item.second)
                            }
                        }
                    }
                }

                DropDownMenu(
                    modifier = Modifier.align(Alignment.TopEnd),
                    items = listOf("Load Plugin", "Load Data", "Width Limit", "Plugin Info"),
                    clicked = {
                        when (it) {
                            "Load Plugin" -> openWindow = Window.LOAD_PLUGIN
                            "Load Data" -> openWindow = Window.LOAD_DATA

                            "Width Limit" -> {
                                Config.noWidthRestriction = !Config.noWidthRestriction
                                widthLimitWrapper = !widthLimitWrapper
                            }

                            "Plugin Info" -> openWindow = Window.PLUGIN_INFO
                        }
                    }
                )

                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Text Explorer", fontSize = 32.sp)

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextField(
                            value = text,
                            onValueChange = { text = it },
                            placeholder = { Text("Search...") },
                            modifier = Modifier.width(600.dp).background(
                                color = Color.LightGray,
                                shape = RoundedCornerShape(24.dp),
                            ),
                            colors = TextFieldDefaults.textFieldColors(
                                backgroundColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            trailingIcon = {
                                IconButton(onClick = {
                                    text = ""
                                    showTable = false
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close")
                                }
                            })

                        IconButton(
                            onClick = {
                                currentPage = 0
                                val (count, pages, indexed) = dataService.getPageCount(text)

                                amount = count

                                totalPages = pages

                                indexStrings = indexed

                                if (totalPages > 0) {
                                    dataService.getPage(text, currentPage).let { (pageEntries, pageLinks) ->

                                        val first = pageEntries.firstOrNull() ?: return@let

                                        header = first.keys.toList()

                                        data = pageEntries.map { it.values.toList() }

                                        links = pageLinks
                                    }
                                }

                                showTable = true
                            },
                            modifier = Modifier.height(70.dp).padding(horizontal = 10.dp),
                            enabled = text.isNotBlank() && dataService.hasSelectedPool()
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    AnimatedVisibility(visible = showTable) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                        ) inner@{
                            if (totalPages == 0L) {
                                Text(
                                    "No results were found.",
                                    fontSize = 24.sp,
                                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp)
                                )
                                return@inner
                            }

                            TableView(
                                modifier = Modifier.weight(1f),
                                dataService.getSelectedPool(),
                                indexStrings = indexStrings,
                                columns = header,
                                data = data,
                                links = links,
                                onRowSelect = { selectedRows = it },
                                clicked = { openWindow = Window.DIFF })

                            if (totalPages < 2) {
                                Text("Total: $amount", modifier = Modifier.align(Alignment.CenterHorizontally))
                                return@inner
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                IconButton(
                                    onClick = {
                                        if (currentPage > 0) {
                                            currentPage--

                                            dataService.getPage(text, currentPage).let { result ->
                                                data = result.first.map { it.values.toList() }

                                                links = result.second
                                            }
                                        }
                                    }, enabled = currentPage > 0
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Left")
                                }

                                Text("Page ${currentPage + 1} of $totalPages (Total: $amount)")

                                IconButton(
                                    onClick = {
                                        if (currentPage < totalPages - 1) {
                                            currentPage++

                                            dataService.getPage(text, currentPage).let { result ->
                                                data = result.first.map { it.values.toList() }

                                                links = result.second
                                            }
                                        }
                                    }, enabled = currentPage < totalPages - 1
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Right")
                                }
                            }
                        }
                    }
                }

                Text(
                    versionString,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    fontSize = 10.sp,
                    color = Color.LightGray
                )

                when (openWindow) {
                    Window.PLUGIN_INFO -> PluginInfoWindow(pluginService) { openWindow = Window.NONE }
                    Window.DIFF -> DiffViewerWindow(
                        diffService,
                        pluginService,
                        dataService.getSelectedPool(),
                        selectedRows
                    ) { openWindow = Window.NONE }

                    Window.LOAD_PLUGIN -> FileDialog { paths ->
                        openWindow = Window.NONE

                        paths.filter { it.extension == "jar" }.forEach { loadPlugin(it) }
                    }

                    Window.LOAD_DATA -> DataLoaderWindow { openWindow = Window.NONE }
                    else -> {}
                }
            }
        }
    }

    fun start() = application(exitProcessOnExit = true) {
        val windowState =
            rememberWindowState(position = WindowPosition.Aligned(Alignment.Center), size = DpSize(1600.dp, 900.dp))

        Window(title = "TextExplorer", state = windowState, onCloseRequest = {
            dataService.close()
            Config.save()
            exitApplication()
        }) {
            content()
        }
    }

    private fun loadPlugin(path: Path): Boolean {
        val pluginPath = pluginsDir.resolve(path.name)

        if (pluginPath.exists()) return true

        val plugin = this.pluginService.loadPlugin(path) ?: return false

        this.pluginService.getDataInfo(plugin)?.let { dataInfo ->
            val provider =
                this.dataService.createStorageProvider(dataInfo, this.dataDir.resolve(dataInfo)) ?: return true

            plugin.init(provider)
        }

        path.copyTo(pluginPath)

        return true
    }
}