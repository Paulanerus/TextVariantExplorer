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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.*
import dev.paulee.api.data.DiffService
import dev.paulee.api.data.IDataService
import dev.paulee.api.data.provider.QueryOrder
import dev.paulee.api.plugin.IPluginService
import dev.paulee.ui.components.*
import dev.paulee.ui.windows.DataLoaderWindow
import dev.paulee.ui.windows.DiffViewerWindow
import dev.paulee.ui.windows.PluginInfoWindow
import dev.paulee.ui.windows.SettingsWindow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.file.Path
import kotlin.io.path.*

enum class Window {
    NONE,
    LOAD_PLUGIN,
    LOAD_DATA,
    DIFF,
    PLUGIN_INFO,
    SETTINGS,
}

class TextExplorerUI(
    private val pluginService: IPluginService,
    private val dataService: IDataService,
    private val diffService: DiffService,
) {
    private val appDir = Path(System.getProperty("user.home")).resolve(App.APP_DIR)

    private val pluginsDir = appDir.resolve("plugins")

    private val dataDir = appDir.resolve("data")

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
        var textField by remember { mutableStateOf(TextFieldValue("")) }
        var selectedRows by remember { mutableStateOf(listOf<Map<String, String>>()) }
        var openWindow by remember { mutableStateOf(Window.NONE) }
        var showTable by remember { mutableStateOf(false) }
        var showPopup by remember { mutableStateOf(false) }
        var totalPages by remember { mutableStateOf(0L) }
        var amount by remember { mutableStateOf(0L) }
        var currentPage by remember { mutableStateOf(0) }

        var queryOrderState by remember { mutableStateOf(null as QueryOrder?) }

        var selectedByPage by remember { mutableStateOf(mapOf<Int, Set<Int>>()) }
        var selectedRowsCache by remember { mutableStateOf(mapOf<String, List<String>>()) }

        var showSuggestions by remember { mutableStateOf(false) }
        var suggestions by remember { mutableStateOf(listOf<String>()) }
        var highlightedIndex by remember { mutableStateOf(0) }
        var suppressNextEnterSearch by remember { mutableStateOf(false) }

        var selectedText = remember(this.poolSelected) {
            val (pool, field) = dataService.getSelectedPool().split(".", limit = 2)

            if (pool == "null") "No source available"
            else "$pool ($field)"
        }

        var header by remember { mutableStateOf(listOf<String>()) }
        var indexStrings by remember { mutableStateOf(emptySet<String>()) }
        var data by remember { mutableStateOf(listOf<List<String>>()) }
        var links by remember { mutableStateOf(mapOf<String, List<Map<String, String>>>()) }

        var loadState by remember { mutableStateOf<LoadState>(LoadState.Idle) }
        val scope = rememberCoroutineScope()

        val performSearch: () -> Unit = {
            showSuggestions = false

            currentPage = 0

            selectedByPage = emptyMap()
            selectedRowsCache = emptyMap()
            selectedRows = emptyList()

            val queryText = textField.text

            val (count, pages, indexed) = dataService.getPageCount(queryText)
            amount = count

            totalPages = pages

            indexStrings = indexed

            if (totalPages > 0) {
                dataService.getPage(queryText, queryOrderState, currentPage).let { (pageEntries, pageLinks) ->
                    val first = pageEntries.firstOrNull() ?: return@let

                    header = first.keys.toList()

                    data = pageEntries.map { it.values.toList() }

                    links = pageLinks
                }
            }

            showTable = true
        }

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
                                    textField = TextFieldValue("")
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

            IconDropDown(
                modifier = Modifier.align(Alignment.TopEnd),
                items = listOf("Load Plugin", "Load Data", "Plugin Info", "---", "Settings"),
            ) {
                when (it) {
                    "Load Plugin" -> openWindow = Window.LOAD_PLUGIN
                    "Load Data" -> openWindow = Window.LOAD_DATA
                    "Settings" -> openWindow = Window.SETTINGS
                    "Plugin Info" -> openWindow = Window.PLUGIN_INFO
                }
            }

            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(App.NAME, fontSize = 32.sp)

                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        TextField(
                            value = textField,
                            onValueChange = { newValue ->
                                textField = newValue
                                val text = newValue.text
                                val caret = newValue.selection.start

                                val ctx = Autocomplete.getAutocompleteContext(text, caret)

                                if (ctx != null && ctx.value.isNotEmpty()) {
                                    scope.launch {
                                        delay(300)

                                        if (textField.text == text) {
                                            suggestions = dataService.getSuggestions(ctx.field, ctx.value)
                                            highlightedIndex = 0
                                            showSuggestions = suggestions.isNotEmpty()
                                        }
                                    }
                                } else {
                                    showSuggestions = false
                                }
                            },
                            placeholder = { Text("Search...") },
                            modifier = Modifier
                                .width(600.dp)
                                .background(
                                    color = Color.LightGray,
                                    shape = RoundedCornerShape(24.dp),
                                )
                                .onPreviewKeyEvent { event ->
                                    if (showSuggestions) {
                                        when (event.type) {
                                            KeyEventType.KeyDown if event.key == Key.DirectionDown -> {
                                                highlightedIndex =
                                                    (highlightedIndex + 1).coerceAtMost(suggestions.lastIndex)
                                                true
                                            }

                                            KeyEventType.KeyDown if event.key == Key.DirectionUp -> {
                                                highlightedIndex = (highlightedIndex - 1).coerceAtLeast(0)
                                                true
                                            }

                                            KeyEventType.KeyDown if (event.key == Key.Enter || event.key == Key.Tab) -> {
                                                suggestions.getOrNull(highlightedIndex)
                                                    ?.let {
                                                        Autocomplete.acceptSuggestion(textField, it)
                                                            ?.let { newValue ->
                                                                textField = newValue
                                                                showSuggestions = false
                                                            }
                                                    }
                                                suppressNextEnterSearch = event.key == Key.Enter
                                                true
                                            }

                                            KeyEventType.KeyDown if event.key == Key.Escape -> {
                                                showSuggestions = false
                                                true
                                            }

                                            else -> false
                                        }
                                    } else {
                                        if (event.key == Key.Enter && event.type == KeyEventType.KeyUp) {
                                            if (suppressNextEnterSearch) {
                                                suppressNextEnterSearch = false
                                                return@onPreviewKeyEvent true
                                            }
                                            if (textField.text.isNotBlank() && dataService.hasSelectedPool()) {
                                                performSearch()
                                                return@onPreviewKeyEvent true
                                            }
                                        }
                                        false
                                    }
                                },
                            colors = TextFieldDefaults.textFieldColors(
                                backgroundColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = {
                                    textField = TextFieldValue("")
                                    showSuggestions = false
                                    showTable = false
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close")
                                }
                            }
                        )

                        DropdownMenu(
                            expanded = showSuggestions,
                            onDismissRequest = { showSuggestions = false },
                            properties = PopupProperties(focusable = false)
                        ) {
                            suggestions.forEachIndexed { idx, s ->
                                DropdownMenuItem(
                                    onClick = {
                                        Autocomplete.acceptSuggestion(textField, s)?.let { newValue ->
                                            textField = newValue
                                            showSuggestions = false
                                        }
                                    }
                                ) {
                                    val isSel = idx == highlightedIndex
                                    Text(
                                        text = s,
                                        color = if (isSel) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface
                                    )
                                }
                            }
                        }
                    }

                    IconButton(
                        onClick = { performSearch() },
                        modifier = Modifier.height(70.dp).padding(horizontal = 10.dp),
                        enabled = textField.text.isNotBlank() && dataService.hasSelectedPool()
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
                            queryOrder = queryOrderState,
                            onQueryOrderChange = { newQueryOrder ->
                                queryOrderState = newQueryOrder

                                selectedByPage = emptyMap()
                                selectedRowsCache = emptyMap()
                                selectedRows = emptyList()

                                currentPage = 0
                                dataService.getPage(textField.text, queryOrderState, currentPage).let { result ->
                                    data = result.first.map { it.values.toList() }
                                    links = result.second
                                }
                            },
                            totalAmountOfSelectedRows = selectedRows.size,
                            selectedIndices = selectedByPage[currentPage] ?: emptySet(),
                            onSelectionChange = { newSelection ->
                                val currentSelected = selectedByPage[currentPage] ?: emptySet()

                                selectedByPage = if (newSelection.isEmpty()) {
                                    selectedByPage - currentPage
                                } else {
                                    selectedByPage + (currentPage to newSelection)
                                }

                                val added = newSelection - currentSelected
                                val removed = currentSelected - newSelection

                                val updatedCache = selectedRowsCache.toMutableMap()

                                added.forEach { idx ->
                                    data.getOrNull(idx)?.let { updatedCache["${currentPage}_$idx"] = it }
                                }

                                removed.forEach { updatedCache.remove("${currentPage}_$it") }

                                selectedRowsCache = updatedCache

                                selectedRows = selectedRowsCache.values
                                    .map { header.zip(it).toMap() }
                                    .toList()
                            },
                            clicked = { openWindow = Window.DIFF }
                        )

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

                                        dataService.getPage(textField.text, queryOrderState, currentPage)
                                            .let { result ->
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

                                        dataService.getPage(textField.text, queryOrderState, currentPage)
                                            .let { result ->
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
                App.VERSION_STRING,
                modifier = Modifier.align(Alignment.BottomCenter),
                fontSize = 10.sp,
                color = Color.LightGray
            )

            when (loadState) {
                is LoadState.Loading -> {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text(text = (loadState as LoadState.Loading).message)
                    }
                }

                is LoadState.Success -> {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Success",
                            tint = Color(0xFF388E3C),
                            modifier = Modifier.size(24.dp)
                        )

                        Text(
                            text = (loadState as LoadState.Success).message,
                            color = Color(0xFF388E3C)
                        )
                    }
                    LaunchedEffect(loadState) {
                        delay(4000)
                        loadState = LoadState.Idle
                    }
                }

                is LoadState.Error -> {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "Error",
                            tint = Color(0xFFD32F2F),
                            modifier = Modifier.size(24.dp)
                        )

                        Text(
                            text = (loadState as LoadState.Error).message,
                            color = Color(0xFFD32F2F)
                        )
                    }
                    LaunchedEffect(loadState) {
                        delay(4000)
                        loadState = LoadState.Idle
                    }
                }

                else -> {}
            }

            when (openWindow) {
                Window.PLUGIN_INFO -> PluginInfoWindow(
                    pluginService,
                    dataService.getAvailableDataInfo()
                ) { openWindow = Window.NONE }

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

                Window.LOAD_DATA -> DataLoaderWindow(dataService, dataDir) { dataInfo ->
                    openWindow = Window.NONE

                    if (dataInfo == null) return@DataLoaderWindow

                    scope.launch {
                        loadState = LoadState.Loading("Loading data pool")

                        val poolsEmpty = dataService.getAvailablePools().isEmpty()

                        val success = dataService.createDataPool(dataInfo, dataDir) { progress ->
                            loadState = LoadState.Loading("Loading data pool ($progress %)")
                        }

                        loadState = if (success) {
                            dataService.getAvailablePools().firstOrNull()?.let inner@{
                                if (!poolsEmpty) return@inner

                                dataService.selectDataPool(it)
                                poolSelected = !poolSelected
                            }

                            LoadState.Success("Successfully loaded data pool '${dataInfo.name}'")
                        } else LoadState.Error("Failed to create data pool")
                    }
                }

                Window.SETTINGS -> SettingsWindow { openWindow = Window.NONE }

                else -> {}
            }
        }
    }

    fun start() = application(exitProcessOnExit = true) {
        val windowState =
            rememberWindowState(
                placement = when (Config.windowState) {
                    "Fullscreen" -> WindowPlacement.Fullscreen
                    "Maximized" -> WindowPlacement.Maximized
                    else -> WindowPlacement.Floating
                },
                position = if (Config.windowState != "Floating" || Config.windowX < 0 || Config.windowY < 0) WindowPosition.Aligned(
                    Alignment.Center
                ) else WindowPosition.Absolute(
                    Config.windowX.dp,
                    Config.windowY.dp
                ),
                size = DpSize(maxOf(Config.windowWidth.dp, 100.dp), maxOf(Config.windowHeight.dp, 100.dp))
            )

        LaunchedEffect(windowState.size, windowState.position, windowState.placement) {
            Config.windowState = windowState.placement.name

            if (Config.windowState == "Floating") {
                Config.windowWidth = windowState.size.width.value.toInt()
                Config.windowHeight = windowState.size.height.value.toInt()

                Config.windowX = windowState.position.x.value.toInt()
                Config.windowY = windowState.position.y.value.toInt()
            }
        }

        Window(title = App.NAME.replace(" ", ""), state = windowState, onCloseRequest = {
            dataService.close()
            Config.save()
            exitApplication()
        }) {
            App.Theme.Current {
                content()
            }
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