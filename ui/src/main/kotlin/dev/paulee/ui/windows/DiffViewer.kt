package dev.paulee.ui.windows

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import dev.paulee.api.data.Change
import dev.paulee.api.data.DiffService
import dev.paulee.api.plugin.*
import dev.paulee.ui.*
import dev.paulee.ui.components.TwoSegmentButton

private typealias EntryRow = Map<String, String>

private fun getTagName(pluginService: IPluginService, taggable: Taggable?): String? {
    val plugin = taggable as? IPlugin ?: return null

    val pluginName = pluginService.getPluginMetadata(plugin)?.name ?: return null
    val viewFilterName = pluginService.getViewFilter(plugin)?.name ?: pluginName

    return when {
        viewFilterName.isNotEmpty() -> viewFilterName
        pluginName.isNotEmpty() -> pluginName
        else -> null
    }
}

private fun getDrawableName(pluginService: IPluginService, drawable: Drawable?): String? {
    val plugin = drawable as? IPlugin ?: return null
    val pluginName = pluginService.getPluginMetadata(plugin)?.name ?: return null

    return pluginName.takeIf { it.isNotEmpty() }
}

private fun <T> buildNamedItems(items: List<T>, getName: (T) -> String?): List<Pair<String, T>> =
    items.mapNotNull { item ->
        getName(item)?.let { name -> name to item }
    }

@Composable
fun DiffViewerWindow(
    diffService: DiffService,
    pluginService: IPluginService,
    selected: String,
    selectedRows: List<EntryRow>,
    onClose: () -> Unit,
) {
    val pool = selected.substringBefore(".")

    val associatedPlugins = pluginService.getPlugins().filter { pluginService.getDataInfo(it) == pool }

    val tagPlugins = associatedPlugins.mapNotNull { it as? Taggable }
    var selectedTaggablePlugin by remember(tagPlugins) { mutableStateOf(tagPlugins.firstOrNull()) }

    val drawablePlugins = associatedPlugins.mapNotNull { it as? Drawable }
    var selectedDrawablePlugin by remember(drawablePlugins) { mutableStateOf(drawablePlugins.firstOrNull()) }

    val alwaysShowFields =
        associatedPlugins.mapNotNull { pluginService.getViewFilter(it) }.flatMap { it.alwaysShow.distinct() }

    var pluginSelected by remember { mutableStateOf(false) }
    var segment by remember { mutableStateOf(false) }
    var entries by remember(selectedRows) { mutableStateOf(selectedRows) }

    val viewFilter = remember(pluginSelected, selectedTaggablePlugin, associatedPlugins) {
        if (pluginSelected) {
            (selectedTaggablePlugin as? IPlugin)?.let { pluginService.getViewFilter(it)?.fields }.orEmpty()
                .filter { it.isNotBlank() }
        } else {
            associatedPlugins.mapNotNull { pluginService.getViewFilter(it) }.filter { it.global }
                .flatMap { filter -> filter.fields.filter { it.isNotBlank() } }.distinct()
        }
    }

    val windowState = rememberWindowState(
        position = WindowPosition.Aligned(Alignment.Center), size = DpSize(1160.dp, 760.dp)
    )

    val locale = LocalI18n.current

    LaunchedEffect(selectedRows) {
        entries = selectedRows
    }

    LaunchedEffect(tagPlugins) {
        if (selectedTaggablePlugin !in tagPlugins) {
            selectedTaggablePlugin = tagPlugins.firstOrNull()
        }
    }

    LaunchedEffect(drawablePlugins) {
        if (selectedDrawablePlugin !in drawablePlugins) {
            selectedDrawablePlugin = drawablePlugins.firstOrNull()
        }
    }

    Window(state = windowState, icon = App.icon, onCloseRequest = onClose, title = locale["diff.title"]) {
        App.Theme.Current {
            val gradient = Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colors.background, MaterialTheme.colors.secondary.copy(alpha = 0.92f)
                )
            )

            Box(
                modifier = Modifier.fillMaxSize().background(gradient).padding(18.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colors.surface.copy(alpha = 0.98f),
                    elevation = 0.dp
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
                        HeaderBar(
                            title = locale["diff.title"],
                            showToggle = drawablePlugins.isNotEmpty(),
                            segment = segment,
                            onSegmentChange = { segment = it },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(16.dp))

                        Crossfade(
                            targetState = segment, animationSpec = tween(190), modifier = Modifier.weight(1f)
                        ) { pluginSegment ->
                            if (pluginSegment) {
                                DrawablePanel(
                                    drawablePlugins = drawablePlugins,
                                    selectedDrawablePlugin = selectedDrawablePlugin,
                                    onSelectedDrawablePluginChange = { selectedDrawablePlugin = it },
                                    getDrawableName = { getDrawableName(pluginService, it) },
                                    selectedRows = selectedRows,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    SelectedReadingsPanel(
                                        entries = entries,
                                        alwaysShowFields = alwaysShowFields,
                                        tagPlugins = tagPlugins,
                                        selectedTaggablePlugin = selectedTaggablePlugin,
                                        onSelectedTaggablePluginChange = { selectedTaggablePlugin = it },
                                        getTagName = { getTagName(pluginService, it) },
                                        pluginSelected = pluginSelected,
                                        onPluginSelectedChange = { pluginSelected = it },
                                        modifier = Modifier.width(280.dp).fillMaxHeight()
                                    )

                                    if (pluginSelected) {
                                        TagView(
                                            entries = entries,
                                            viewFilter = viewFilter,
                                            taggable = selectedTaggablePlugin,
                                            modifier = Modifier.weight(1f).fillMaxHeight()
                                        )
                                    } else {
                                        DiffView(
                                            diffService = diffService,
                                            entries = entries,
                                            viewFilter = viewFilter,
                                            onEntriesChange = { entries = it },
                                            modifier = Modifier.weight(1f).fillMaxHeight()
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        LegendBar(
                            text = locale["diff.legend"], modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderBar(
    title: String,
    showToggle: Boolean,
    segment: Boolean,
    onSegmentChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colors.secondary.copy(alpha = 0.55f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (showToggle) {
                TwoSegmentButton(
                    left = LocalI18n.current["diff.segment.diff"],
                    right = LocalI18n.current["diff.segment.plugin"],
                    selected = segment,
                    onClick = onSegmentChange
                )
            }
        }
    }
}

@Composable
private fun DrawablePanel(
    drawablePlugins: List<Drawable>,
    selectedDrawablePlugin: Drawable?,
    onSelectedDrawablePluginChange: (Drawable) -> Unit,
    getDrawableName: (Drawable?) -> String?,
    selectedRows: List<EntryRow>,
    modifier: Modifier = Modifier,
) {
    val locale = LocalI18n.current

    var showPopup by remember { mutableStateOf(false) }

    val menuItems = remember(drawablePlugins) {
        buildNamedItems(drawablePlugins, getDrawableName)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colors.surface,
        border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f)),
        elevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            Text(locale["diff.plugin.label"], fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

            Box(modifier = Modifier.padding(top = 6.dp)) {
                OutlinedButton(
                    onClick = { if (menuItems.size > 1) showPopup = true }, shape = RoundedCornerShape(50.dp)
                ) {
                    Text(
                        text = getDrawableName(selectedDrawablePlugin).orEmpty().ifBlank { "-" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (menuItems.size > 1) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                    }
                }

                DropdownMenu(expanded = showPopup, onDismissRequest = { showPopup = false }) {
                    menuItems.forEach { (name, plugin) ->
                        DropdownMenuItem(onClick = {
                            onSelectedDrawablePluginChange(plugin)
                            showPopup = false
                        }) {
                            Text(name)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colors.secondary.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.08f))
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    selectedDrawablePlugin?.let {
                        invokeDrawable(it, selectedRows)
                    } ?: Hint(
                        locale["diff.no_drawable"], modifier = Modifier.align(Alignment.Center), transparent = true
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedReadingsPanel(
    entries: List<EntryRow>,
    alwaysShowFields: List<String>,
    tagPlugins: List<Taggable>,
    selectedTaggablePlugin: Taggable?,
    onSelectedTaggablePluginChange: (Taggable) -> Unit,
    getTagName: (Taggable?) -> String?,
    pluginSelected: Boolean,
    onPluginSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalI18n.current

    var showPopup by remember { mutableStateOf(false) }

    val menuItems = remember(tagPlugins) {
        buildNamedItems(tagPlugins, getTagName)
    }

    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colors.surface,
        border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f)),
        elevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            Text(locale["diff.selected_readings"], fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

            Spacer(Modifier.height(10.dp))

            Text(locale["diff.tagger.label"], fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

            Box(modifier = Modifier.padding(top = 6.dp)) {
                OutlinedButton(
                    onClick = { if (menuItems.size > 1) showPopup = true },
                    shape = RoundedCornerShape(50.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = getTagName(selectedTaggablePlugin).orEmpty().ifBlank { "-" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (menuItems.size > 1) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                    }
                }

                DropdownMenu(expanded = showPopup, onDismissRequest = { showPopup = false }) {
                    menuItems.forEach { (name, plugin) ->
                        DropdownMenuItem(onClick = {
                            onSelectedTaggablePluginChange(plugin)
                            showPopup = false
                        }) {
                            Text(name)
                        }
                    }
                }
            }

            val toggleBg by animateColorAsState(
                targetValue = if (pluginSelected) {
                    MaterialTheme.colors.primary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colors.onSurface.copy(alpha = 0.04f)
                }, animationSpec = tween(180)
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = toggleBg,
                border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = pluginSelected,
                        onCheckedChange = onPluginSelectedChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colors.primary,
                            checkedTrackColor = MaterialTheme.colors.primary.copy(alpha = 0.45f),
                            uncheckedThumbColor = MaterialTheme.colors.onSurface.copy(alpha = 0.85f),
                            uncheckedTrackColor = MaterialTheme.colors.onSurface.copy(alpha = 0.28f)
                        )
                    )

                    Spacer(Modifier.width(8.dp))

                    Text(
                        locale["diff.plugin_only"],
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (pluginSelected) {
                            MaterialTheme.colors.primary
                        } else {
                            MaterialTheme.colors.onSurface.copy(alpha = 0.86f)
                        }
                    )
                }
            }

            Text(
                text = locale["diff.selected_readings_hint"],
                fontSize = 11.sp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 4.dp)
            )

            Spacer(Modifier.height(4.dp))

            Box(modifier = Modifier.fillMaxSize()) {
                if (entries.isEmpty()) {
                    Hint(locale["diff.no_entries"], modifier = Modifier.align(Alignment.Center), transparent = true)
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(end = 14.dp).verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        entries.forEachIndexed { index, entry ->
                            SelectedReadingCard(
                                index = index, entry = entry, alwaysShowFields = alwaysShowFields
                            )
                        }
                    }

                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(scrollState),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(start = 6.dp, end = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedReadingCard(
    index: Int,
    entry: Map<String, String>,
    alwaysShowFields: List<String>,
) {
    val pairs = remember(entry, alwaysShowFields) {
        val selected = if (alwaysShowFields.isEmpty()) {
            entry.entries.take(3).map { it.key to it.value }
        } else {
            alwaysShowFields.mapNotNull { field ->
                if (!entry.containsKey(field)) return@mapNotNull null
                field to entry[field].orEmpty()
            }
        }

        selected.filter { it.second.isNotBlank() }.ifEmpty { listOf("value" to "-") }
    }

    val bg by animateColorAsState(
        targetValue = if (index == 0) {
            MaterialTheme.colors.primary.copy(alpha = 0.09f)
        } else {
            MaterialTheme.colors.onSurface.copy(alpha = 0.03f)
        }, animationSpec = tween(170)
    )

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bg,
        border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text("#${index + 1}", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)

            Spacer(Modifier.height(4.dp))

            pairs.take(3).forEach { (key, value) ->
                Text(key, fontSize = 11.sp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.72f))

                SelectionContainer {
                    Text(
                        value,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TagView(
    entries: List<EntryRow>,
    viewFilter: List<String> = emptyList(),
    taggable: Taggable?,
    modifier: Modifier = Modifier,
) {
    val columnData = remember(entries, viewFilter) {
        buildColumnData(entries, viewFilter)
    }

    var currentColumnIndex by remember(columnData.columns) { mutableStateOf(0) }

    LaunchedEffect(columnData.columns) {
        if (currentColumnIndex > columnData.columns.lastIndex) {
            currentColumnIndex = columnData.columns.lastIndex.coerceAtLeast(0)
        }
    }

    ColumnPanel(
        columns = columnData.columns,
        rowCount = columnData.rowCount,
        currentColumnIndex = currentColumnIndex,
        onColumnChange = { currentColumnIndex = it },
        modifier = modifier
    ) { columnName ->
        val values = columnData.filteredFields[columnName].orEmpty()

        repeat(columnData.rowCount) { index ->
            val value = values.getOrNull(index).orEmpty()
            val tags = taggable?.tag(columnName, value).orEmpty()

            TagEntryRow(index = index, value = value, tags = tags)
        }
    }
}

@Composable
private fun DiffView(
    diffService: DiffService,
    entries: List<EntryRow>,
    viewFilter: List<String> = emptyList(),
    onEntriesChange: (List<EntryRow>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalI18n.current

    val columnData = remember(entries, viewFilter) {
        buildColumnData(entries, viewFilter)
    }

    var currentColumnIndex by remember(columnData.columns) { mutableStateOf(0) }

    LaunchedEffect(columnData.columns) {
        if (currentColumnIndex > columnData.columns.lastIndex) {
            currentColumnIndex = columnData.columns.lastIndex.coerceAtLeast(0)
        }
    }

    ColumnPanel(
        columns = columnData.columns,
        rowCount = columnData.rowCount,
        currentColumnIndex = currentColumnIndex,
        onColumnChange = { currentColumnIndex = it },
        modifier = modifier
    ) { columnName ->
        val values = columnData.filteredFields[columnName].orEmpty()
        val fallback = values.firstOrNull().orEmpty()

        repeat(columnData.rowCount) { index ->
            val value = values.getOrNull(index).orEmpty()
            val change = if (index == 0) null else diffService.getDiff(fallback, value)

            DiffEntryRow(
                index = index,
                value = value,
                fallback = fallback,
                change = change,
                onMoveUp = if (index == 0) {
                    null
                } else {
                    {
                        val itemIndex = entries.indexOfFirst { it[columnName] == value }

                        if (itemIndex != -1) {
                            val updated = entries.toMutableList()
                            val selectedItem = updated.removeAt(itemIndex)
                            updated.add(0, selectedItem)
                            onEntriesChange(updated)
                        }
                    }
                },
                moveUpLabel = locale["diff.move_up"])
        }
    }
}

private data class ColumnData(
    val filteredFields: Map<String, List<String>>,
    val columns: List<String>,
    val rowCount: Int,
)

private fun buildColumnData(
    entries: List<EntryRow>,
    viewFilter: List<String>,
): ColumnData {
    val groupedFields =
        entries.flatMap { it.entries }.groupBy({ it.key }, { it.value }).filterValues { it.isNotEmpty() }

    val filteredFields = groupedFields.filterKeys { viewFilter.isEmpty() || it in viewFilter }

    return ColumnData(
        filteredFields = filteredFields,
        columns = filteredFields.keys.toList(),
        rowCount = filteredFields.values.maxOfOrNull { it.size } ?: 0)
}

@Composable
private fun ColumnPanel(
    columns: List<String>,
    rowCount: Int,
    currentColumnIndex: Int,
    onColumnChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    rowContent: @Composable (columnName: String) -> Unit,
) {
    val locale = LocalI18n.current

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colors.surface,
        border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f)),
        elevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            Text(locale["diff.view.title"], fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

            Spacer(Modifier.height(10.dp))

            if (columns.isEmpty() || rowCount == 0) {
                Hint(locale["diff.no_columns"], modifier = Modifier.fillMaxWidth(), transparent = true)
                return@Column
            }

            ColumnNavigator(
                columns = columns,
                currentColumnIndex = currentColumnIndex,
                onColumnChange = onColumnChange,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            val verticalScrollState = rememberScrollState()

            Box(modifier = Modifier.fillMaxSize()) {
                Crossfade(
                    targetState = currentColumnIndex, animationSpec = tween(160), modifier = Modifier.fillMaxSize()
                ) { columnIndex ->
                    val columnName = columns[columnIndex]

                    Column(
                        modifier = Modifier.fillMaxSize().padding(end = 10.dp).verticalScroll(verticalScrollState),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowContent(columnName)
                    }
                }

                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(verticalScrollState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun ColumnNavigator(
    columns: List<String>,
    currentColumnIndex: Int,
    onColumnChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalI18n.current

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        IconButton(
            onClick = { onColumnChange(currentColumnIndex - 1) }, enabled = currentColumnIndex > 0
        ) {
            Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = locale["diff.prev_column"])
        }

        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colors.secondary.copy(alpha = 0.42f),
            border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.1f))
        ) {
            Text(
                text = columns[currentColumnIndex],
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = { onColumnChange(currentColumnIndex + 1) }, enabled = currentColumnIndex < columns.size - 1
        ) {
            Icon(Icons.AutoMirrored.Default.ArrowForward, contentDescription = locale["diff.next_column"])
        }
    }
}

@Composable
private fun TagEntryRow(
    index: Int,
    value: String,
    tags: Map<String, Tag>,
) {
    val locale = LocalI18n.current

    val bg by animateColorAsState(
        targetValue = if (index == 0) {
            MaterialTheme.colors.onSurface.copy(alpha = 0.06f)
        } else {
            MaterialTheme.colors.onSurface.copy(alpha = 0.03f)
        }, animationSpec = tween(160)
    )

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bg,
        border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            RowIndexBadge(index = index)

            Spacer(Modifier.width(10.dp))

            if (value.isBlank()) {
                NoValueIndicator(locale["diff.no_value"])
            } else {
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    SmoothMarkedText(text = value, highlights = tags)
                }
            }
        }
    }
}

@Composable
private fun DiffEntryRow(
    index: Int,
    value: String,
    fallback: String,
    change: Change?,
    onMoveUp: (() -> Unit)?,
    moveUpLabel: String,
) {
    val locale = LocalI18n.current

    val rowKind = remember(index, change) {
        if (index == 0) DiffRowKind.Reference else detectRowKind(change)
    }

    val bg by animateColorAsState(
        targetValue = when (rowKind) {
            DiffRowKind.Reference -> MaterialTheme.colors.onSurface.copy(alpha = 0.06f)
            DiffRowKind.Addition -> App.Colors.GREEN_HIGHLIGHT.toComposeColor(34)
            DiffRowKind.Omission -> App.Colors.RED_HIGHLIGHT.toComposeColor(34)
            DiffRowKind.Mixed -> MaterialTheme.colors.primary.copy(alpha = 0.1f)
            DiffRowKind.Neutral -> MaterialTheme.colors.onSurface.copy(alpha = 0.03f)
        }, animationSpec = tween(160)
    )

    val border by animateColorAsState(
        targetValue = when (rowKind) {
            DiffRowKind.Reference -> MaterialTheme.colors.onSurface.copy(alpha = 0.1f)
            DiffRowKind.Addition -> App.Colors.GREEN_HIGHLIGHT.toComposeColor(95)
            DiffRowKind.Omission -> App.Colors.RED_HIGHLIGHT.toComposeColor(95)
            DiffRowKind.Mixed -> MaterialTheme.colors.primary.copy(alpha = 0.22f)
            DiffRowKind.Neutral -> MaterialTheme.colors.onSurface.copy(alpha = 0.08f)
        }, animationSpec = tween(160)
    )

    Surface(
        shape = RoundedCornerShape(10.dp), color = bg, border = BorderStroke(1.dp, border)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RowIndexBadge(index = index)

            SelectionContainer(modifier = Modifier.weight(1f)) {
                if (index == 0) {
                    if (value.isBlank()) {
                        NoValueIndicator(locale["diff.no_value"])
                    } else {
                        Text(
                            text = value, textAlign = TextAlign.Left, fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    if (value.isBlank()) {
                        NoValueIndicator(locale["diff.no_value"])
                    } else {
                        HeatmapText(
                            change = change, fallback = fallback, textAlign = TextAlign.Left
                        )
                    }
                }
            }

            if (onMoveUp != null) {
                IconButton(onClick = onMoveUp) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = moveUpLabel)
                }
            }
        }
    }
}

@Composable
private fun LegendBar(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(11.dp),
        color = MaterialTheme.colors.secondary.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier.size(width = 22.dp, height = 10.dp)
                    .background(App.Colors.GREEN_HIGHLIGHT.toComposeColor(110), RoundedCornerShape(50.dp))
            )

            Box(
                modifier = Modifier.size(width = 22.dp, height = 10.dp)
                    .background(App.Colors.RED_HIGHLIGHT.toComposeColor(110), RoundedCornerShape(50.dp))
            )

            Text(text, fontSize = 12.sp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun NoValueIndicator(
    text: String,
) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontStyle = FontStyle.Italic,
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    )
}

@Composable
private fun RowIndexBadge(
    index: Int,
) {
    Surface(
        modifier = Modifier.size(24.dp),
        shape = CircleShape,
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f))
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = (index + 1).toString(),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.75f),
                textAlign = TextAlign.Center
            )
        }
    }
}

private data class SmoothHighlight(
    val start: Int,
    val end: Int,
    val word: String,
    val tag: String,
    val color: Color,
)

private sealed class SmoothSegment {
    data class Plain(val text: String) : SmoothSegment()

    data class Highlighted(val word: String, val tag: String, val color: Color) :
        SmoothSegment()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SmoothMarkedText(
    text: String,
    highlights: Map<String, Tag>,
) {
    val textColor = MaterialTheme.colors.onBackground.copy(alpha = 0.92f)

    val allMatches = remember(text, highlights) {
        buildList {
            highlights.filter { (word, _) -> word.isNotBlank() && word.length > 1 }.forEach { (word, tagAndColor) ->
                val (tag, color) = tagAndColor

                var searchIndex = 0

                while (true) {
                    val foundIndex = text.indexOf(word, searchIndex)
                    if (foundIndex == -1) break

                    val endIndex = foundIndex + word.length

                    if (isValidWordBoundary(text, foundIndex, endIndex)) {
                        add(
                            SmoothHighlight(
                                start = foundIndex,
                                end = endIndex,
                                word = word,
                                tag = tag,
                                color = color.toComposeColor()
                            )
                        )
                    }

                    searchIndex = endIndex
                }
            }
        }.sortedWith(compareBy({ it.start }, { -it.end }))
    }

    val selectedMatches = remember(allMatches) {
        buildList {
            var lastEnd = 0

            allMatches.forEach {
                if (it.start < lastEnd) return@forEach

                add(it)
                lastEnd = it.end
            }
        }
    }

    val segments = remember(text, selectedMatches) {
        buildList {
            var currentIndex = 0

            selectedMatches.forEach { match ->
                if (match.start > currentIndex) {
                    add(SmoothSegment.Plain(text.substring(currentIndex, match.start)))
                }

                add(
                    SmoothSegment.Highlighted(
                        word = match.word, tag = match.tag, color = match.color
                    )
                )

                currentIndex = match.end
            }

            if (currentIndex < text.length) {
                add(SmoothSegment.Plain(text.substring(currentIndex)))
            }
        }
    }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        segments.forEach { segment ->
            when (segment) {
                is SmoothSegment.Plain -> {
                    if (segment.text.isNotEmpty()) {
                        Text(
                            text = segment.text, fontSize = 15.sp, color = textColor, textAlign = TextAlign.Left
                        )
                    }
                }

                is SmoothSegment.Highlighted -> {
                    SmoothTagPill(
                        word = segment.word, tag = segment.tag, color = segment.color, textColor = textColor
                    )
                }
            }
        }
    }
}

@Composable
private fun SmoothTagPill(
    word: String,
    tag: String,
    color: Color,
    textColor: Color,
) {
    val border = color.copy(alpha = 0.55f)
    val wordBg = color.copy(alpha = 0.16f)
    val shape = RoundedCornerShape(8.dp)

    Row(
        modifier = Modifier.height(IntrinsicSize.Min).clip(shape).border(1.dp, border, shape),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Box(
            modifier = Modifier.background(wordBg).padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = word, fontSize = 14.sp, color = textColor, fontWeight = FontWeight.Medium
            )
        }

        if (tag.isNotBlank()) {
            val brightness = (color.red * 0.299f) + (color.green * 0.587f) + (color.blue * 0.114f)
            val tagTextColor = if (brightness > 0.58f) Color.Black else Color.White

            Box(
                modifier = Modifier.background(color.copy(alpha = 0.92f)).padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tag, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = tagTextColor, maxLines = 1
                )
            }
        }
    }
}

private fun String.isWordBoundary(index: Int): Boolean {
    if (index !in 1..<length) return true
    return !this[index].isLetterOrDigit()
}

private fun isValidWordBoundary(text: String, start: Int, end: Int): Boolean {
    val startBoundary = start == 0 || text.isWordBoundary(start - 1)
    val endBoundary = end == text.length || text.isWordBoundary(end)
    return startBoundary && endBoundary
}

private enum class DiffRowKind {
    Reference, Addition, Omission, Mixed, Neutral,
}

private fun detectRowKind(change: Change?): DiffRowKind {
    if (change == null) return DiffRowKind.Neutral

    val tokens = change.tokens.map { it.first }

    val additions = tokens.any { token -> token.startsWith("**") && token.endsWith("**") }
    val omissions = tokens.any { token -> token.startsWith("~~") && token.endsWith("~~") }

    return when {
        additions && omissions -> DiffRowKind.Mixed
        additions -> DiffRowKind.Addition
        omissions -> DiffRowKind.Omission
        else -> DiffRowKind.Neutral
    }
}
