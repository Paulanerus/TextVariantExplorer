package dev.paulee.ui.windows

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import dev.paulee.api.data.DiffService
import dev.paulee.api.plugin.Drawable
import dev.paulee.api.plugin.IPlugin
import dev.paulee.api.plugin.IPluginService
import dev.paulee.api.plugin.Taggable
import dev.paulee.ui.HeatmapText
import dev.paulee.ui.MarkedText
import dev.paulee.ui.components.TwoSegmentButton
import dev.paulee.ui.invokeDrawable

@Composable
fun DiffViewerWindow(
    diffService: DiffService,
    pluginService: IPluginService,
    selected: String,
    selectedRows: List<Map<String, String>>,
    onClose: () -> Unit,
) {
    fun getTagName(taggable: Taggable?): String? {
        val plugin = taggable as? IPlugin ?: return null

        val pluginName = pluginService.getPluginMetadata(plugin)?.name ?: return null

        val viewFilterName = pluginService.getViewFilter(plugin)?.name ?: pluginName

        return when {
            viewFilterName.isNotEmpty() -> viewFilterName
            pluginName.isNotEmpty() -> pluginName
            else -> null
        }
    }

    fun getDrawableName(drawable: Drawable?): String? {
        val plugin = drawable as? IPlugin ?: return null

        val pluginName = pluginService.getPluginMetadata(plugin)?.name ?: return null

        return when {
            pluginName.isNotEmpty() -> pluginName
            else -> null
        }
    }

    val pool = selected.substringBefore(".")

    val associatedPlugins = pluginService.getPlugins().filter { pluginService.getDataInfo(it) == pool }

    val tagPlugins = associatedPlugins.mapNotNull { it as? Taggable }
    var selectedTaggablePlugin = tagPlugins.firstOrNull()

    val drawablePlugins = associatedPlugins.mapNotNull { it as? Drawable }
    var selectedDrawablePlugin = drawablePlugins.firstOrNull()

    val alwaysShowFields =
        associatedPlugins.mapNotNull { pluginService.getViewFilter(it) }.flatMap { it.alwaysShow.distinct() }

    var selectedTextDrawable by remember { mutableStateOf(getDrawableName(selectedDrawablePlugin) ?: "") }
    var selectedTextTaggable by remember { mutableStateOf(getTagName(selectedTaggablePlugin) ?: "") }
    var showPopup by remember { mutableStateOf(false) }
    var pluginSelected by remember { mutableStateOf(false) }
    var segment by remember { mutableStateOf(false) }

    val viewFilter = remember(pluginSelected) {
        if (pluginSelected) pluginService.getViewFilter(selectedTaggablePlugin as IPlugin)?.fields.orEmpty()
            .filter { it.isNotBlank() }
        else associatedPlugins.mapNotNull { pluginService.getViewFilter(it) }.filter { it.global }
            .flatMap { filter -> filter.fields.filter { it.isNotBlank() }.toList().distinct() }
    }

    val windowState = rememberWindowState(position = WindowPosition.Aligned(Alignment.Center))

    Window(state = windowState, onCloseRequest = onClose, title = "DiffViewer") {
        MaterialTheme {
            Box(modifier = Modifier.fillMaxSize()) {
                if (drawablePlugins.isNotEmpty()) {
                    TwoSegmentButton(
                        "Diff",
                        "Plugin",
                        segment,
                        onClick = { segment = it },
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
                    )
                }

                if (segment) {
                    Box(modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) inner@{

                        if (selectedDrawablePlugin == null) return@inner

                        Text("Plugin:", fontWeight = FontWeight.Bold)

                        Box(Modifier.padding(start = 4.dp, top = 20.dp)) {
                            Text(
                                selectedTextDrawable,
                                fontSize = 14.sp,
                                modifier = Modifier.then(if (drawablePlugins.size > 1) Modifier.clickable {
                                    showPopup = true
                                }
                                else Modifier))
                        }

                        DropdownMenu(
                            expanded = showPopup, onDismissRequest = { showPopup = false }) {
                            val menuItems = drawablePlugins.mapNotNull {
                                val name = getDrawableName(it) ?: return@mapNotNull null

                                Pair(name, it)
                            }
                            menuItems.forEach { item ->
                                DropdownMenuItem(
                                    onClick = {
                                        selectedDrawablePlugin = item.second
                                        selectedTextDrawable = item.first
                                        showPopup = false
                                    }) {
                                    Text(item.first)
                                }
                            }
                        }
                    }

                    selectedDrawablePlugin?.let { invokeDrawable(it, selectedRows) }
                } else {
                    Box(modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) inner@{

                        if (selectedTaggablePlugin == null) return@inner

                        Text("Tagger:", fontWeight = FontWeight.Bold)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                        ) {
                            Text(
                                selectedTextTaggable,
                                fontSize = 14.sp,
                                modifier = Modifier.then(if (tagPlugins.size > 1) Modifier.clickable {
                                    showPopup = true
                                }
                                else Modifier))

                            Checkbox(checked = pluginSelected, onCheckedChange = { pluginSelected = it })
                        }

                        DropdownMenu(
                            expanded = showPopup, onDismissRequest = { showPopup = false }) {
                            val menuItems = tagPlugins.mapNotNull {
                                val name = getTagName(it) ?: return@mapNotNull null

                                Pair(name, it)
                            }
                            menuItems.forEach { item ->
                                DropdownMenuItem(
                                    onClick = {
                                        selectedTaggablePlugin = item.second
                                        selectedTextTaggable = item.first
                                        showPopup = false
                                    }) {
                                    Text(item.first)
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        if (pluginSelected) {
                            TagView(
                                selectedRows,
                                viewFilter,
                                alwaysShowFields,
                                selectedTaggablePlugin,
                                Modifier.align(Alignment.CenterStart)
                            )
                        } else {
                            DiffView(
                                diffService,
                                selectedRows,
                                viewFilter,
                                alwaysShowFields,
                                Modifier.align(Alignment.CenterStart)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TagView(
    initialEntries: List<Map<String, String>>,
    viewFilter: List<String> = emptyList(),
    alwaysShowFields: List<String> = emptyList(),
    taggable: Taggable?,
    modifier: Modifier = Modifier,
) {
    val allFieldsGrouped =
        initialEntries.flatMap { it.entries }.groupBy({ it.key }, { it.value }).filterValues { it.isNotEmpty() }

    val filteredFields = allFieldsGrouped.filterKeys { viewFilter.isEmpty() || it in viewFilter }

    val alwaysShowGrouped = allFieldsGrouped.filterKeys { it in alwaysShowFields }

    val greatestSize = filteredFields.values.maxOfOrNull { it.size } ?: 0

    val columns = filteredFields.keys.toList()

    var currentColumnIndex by remember { mutableStateOf(0) }

    val horizontalScrollState = rememberScrollState()

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val headerTextStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold)

    val maxWidth = columns.maxOfOrNull {
        val headerWidthPx = textMeasurer.measure(text = AnnotatedString(it), style = headerTextStyle).size.width

        with(density) { headerWidthPx.toDp() + 16.dp }
    } ?: 0.dp

    Box(modifier = modifier) {
        Column {
            Box(modifier = Modifier.padding(horizontal = 32.dp)) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) inner@{
                    if (columns.isEmpty()) return@inner

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconButton(
                            onClick = { if (currentColumnIndex > 0) currentColumnIndex-- },
                            enabled = currentColumnIndex > 0
                        ) {
                            Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Previous column")
                        }

                        Text(
                            text = columns[currentColumnIndex],
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp, top = 12.dp, end = 4.dp).width(maxWidth)
                        )

                        IconButton(
                            onClick = { if (currentColumnIndex < columns.size - 1) currentColumnIndex++ },
                            enabled = currentColumnIndex < columns.size - 1
                        ) {
                            Icon(Icons.AutoMirrored.Default.ArrowForward, contentDescription = "Next column")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        AlwaysVisibleFields(alwaysShowFields, alwaysShowGrouped, true)

                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(horizontalScrollState)
                        ) {
                            Spacer(Modifier.height(16.dp))

                            (0 until greatestSize).forEach { index ->
                                val columnName = columns.getOrNull(currentColumnIndex) ?: return@forEach

                                val values = filteredFields[columnName] ?: return@forEach

                                val value = values.getOrNull(index) ?: ""

                                val tags = taggable?.tag(columnName, value).orEmpty()

                                SelectionContainer {
                                    MarkedText(text = value, highlights = tags, textAlign = TextAlign.Left)
                                }
                            }
                        }
                    }

                    HorizontalScrollbar(adapter = rememberScrollbarAdapter(horizontalScrollState))
                }
            }
        }
    }
}

@Composable
private fun DiffView(
    diffService: DiffService,
    initialEntries: List<Map<String, String>>,
    viewFilter: List<String> = emptyList(),
    alwaysShowFields: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    var entries by remember { mutableStateOf(initialEntries) }

    val allFieldsGrouped =
        entries.flatMap { it.entries }.groupBy({ it.key }, { it.value }).filterValues { it.isNotEmpty() }

    val filteredFields = allFieldsGrouped.filterKeys { viewFilter.isEmpty() || it in viewFilter }

    val alwaysShowGrouped = allFieldsGrouped.filterKeys { it in alwaysShowFields }

    val greatestSize = filteredFields.values.maxOfOrNull { it.size } ?: 0

    val columns = filteredFields.keys.toList()

    var currentColumnIndex by remember { mutableStateOf(0) }

    val horizontalScrollState = rememberScrollState()

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val headerTextStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold)

    val maxWidth = columns.maxOfOrNull {
        val headerWidthPx = textMeasurer.measure(text = AnnotatedString(it), style = headerTextStyle).size.width

        with(density) { headerWidthPx.toDp() + 16.dp }
    } ?: 0.dp

    Box(modifier = modifier) {
        Column {
            Box(modifier = Modifier.padding(horizontal = 32.dp)) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) inner@{
                    if (columns.isEmpty()) return@inner

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconButton(
                            onClick = { if (currentColumnIndex > 0) currentColumnIndex-- },
                            enabled = currentColumnIndex > 0
                        ) {
                            Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Previous column")
                        }

                        Text(
                            text = columns[currentColumnIndex],
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp, top = 12.dp, end = 4.dp).width(maxWidth)
                        )

                        IconButton(
                            onClick = { if (currentColumnIndex < columns.size - 1) currentColumnIndex++ },
                            enabled = currentColumnIndex < columns.size - 1
                        ) {
                            Icon(Icons.AutoMirrored.Default.ArrowForward, contentDescription = "Next column")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        AlwaysVisibleFields(alwaysShowFields, alwaysShowGrouped)

                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(horizontalScrollState)
                        ) {
                            Spacer(Modifier.height(16.dp))

                            val columnName = columns.getOrNull(currentColumnIndex) ?: return@Column
                            val values = filteredFields[columnName] ?: return@Column

                            (0 until greatestSize).forEach { index ->
                                val value = values.getOrNull(index) ?: ""
                                val change = diffService.getDiff(values[0], value)

                                key("$index|$columnName|$value") {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        SelectionContainer {
                                            if (index == 0) {
                                                Column {
                                                    Text(value)
                                                    Spacer(Modifier.height(40.dp))
                                                }
                                            } else {
                                                if (value.isEmpty()) Text(value)
                                                else HeatmapText(change, values[0], textAlign = TextAlign.Left)
                                            }
                                        }

                                        if (index != 0) {
                                            IconButton(
                                                onClick = {
                                                    val itemIndex = entries.indexOfFirst {
                                                        it[columnName] == value
                                                    }
                                                    if (itemIndex != -1) {
                                                        val updatedList = entries.toMutableList()
                                                        val selectedItem = updatedList.removeAt(itemIndex)
                                                        updatedList.add(0, selectedItem)
                                                        entries = updatedList
                                                    }
                                                }) {
                                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalScrollbar(adapter = rememberScrollbarAdapter(horizontalScrollState))
                }
            }
        }
    }
}

@Composable
private fun AlwaysVisibleFields(
    alwaysShowFields: List<String>,
    alwaysShowGrouped: Map<String, List<String>>,
    tagView: Boolean = false,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        alwaysShowFields.forEach {
            Column {
                Text(it, fontWeight = FontWeight.Bold)
                alwaysShowGrouped[it]?.forEachIndexed { index, value ->
                    Column {
                        Text(value)
                        if (tagView) Spacer(Modifier.height(6.dp))
                        else Spacer(Modifier.height(if (index == 0) 57.dp else 30.dp))
                    }
                }
            }
        }
    }
}
