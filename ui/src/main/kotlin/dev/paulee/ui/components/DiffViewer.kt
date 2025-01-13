package dev.paulee.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import dev.paulee.api.data.DiffService
import dev.paulee.api.plugin.IPlugin
import dev.paulee.api.plugin.IPluginService
import dev.paulee.api.plugin.Taggable
import dev.paulee.ui.HeatmapText
import dev.paulee.ui.MarkedText

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

    val pool = selected.substringBefore(".")

    val associatedPlugins = pluginService.getPlugins().filter { pluginService.getDataInfo(it)?.name == pool }

    val tagPlugins = associatedPlugins.mapNotNull { it as? Taggable }
    var selectedTaggablePlugin = tagPlugins.firstOrNull()

    val viewFilter = associatedPlugins.mapNotNull { pluginService.getViewFilter(it) }.filter { it.global }
        .flatMap { it.fields.toList() }.toSet()

    var selectedText by remember { mutableStateOf(getTagName(selectedTaggablePlugin) ?: "") }
    var showPopup by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(false) }

    val entries = remember(selected) {
        if (selected) {
            val tagFilter = pluginService.getViewFilter(selectedTaggablePlugin as IPlugin)?.fields.orEmpty()

            selectedRows.map { it.filterKeys { key -> tagFilter.isEmpty() || (key in tagFilter) } }
        } else selectedRows.map { it.filterKeys { key -> viewFilter.isEmpty() || (key in viewFilter) } }
    }

    Window(onCloseRequest = onClose, title = "DiffViewer") {
        MaterialTheme {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {

                    if (selectedTaggablePlugin == null) return@Box

                    Text("Tagger:", fontWeight = FontWeight.Bold)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                    ) {
                        Text(
                            selectedText,
                            fontSize = 14.sp,
                            modifier = Modifier.then(if (tagPlugins.size > 1) Modifier.clickable { showPopup = true }
                            else Modifier))

                        Checkbox(checked = selected, onCheckedChange = { selected = it })
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
                                    selectedText = item.first
                                    showPopup = false
                                }) {
                                Text(item.first)
                            }
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (selected) TagView(entries, selectedTaggablePlugin, Modifier.align(Alignment.Center))
                    else DiffView(diffService, entries, Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

@Composable
private fun TagView(entries: List<Map<String, String>>, taggable: Taggable?, modifier: Modifier = Modifier) {
    val horizontalScrollState = rememberScrollState()

    val grouped = entries.flatMap { it.entries }.groupBy({ it.key }, { it.value }).filterValues { it.isNotEmpty() }

    val greatestSize = grouped.values.maxOfOrNull { it.size } ?: 0

    val columns = entries.flatMap { it.keys }.distinct()

    var currentColumnIndex by remember { mutableStateOf(0) }

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val headerTextStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold)

    val maxWidth = columns.maxOf {
        val headerWidthPx = textMeasurer.measure(text = AnnotatedString(it), style = headerTextStyle).size.width

        with(density) { headerWidthPx.toDp() + 16.dp }
    }

    Box(modifier = modifier) {
        Column {
            Box(modifier = Modifier.padding(horizontal = 32.dp)) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (columns.isEmpty()) return@Column

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

                    Column(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
                        (0 until greatestSize).forEach { index ->
                            val columnName = columns.getOrNull(currentColumnIndex) ?: return@forEach

                            val values = grouped[columnName] ?: return@forEach

                            val value = values.getOrNull(index) ?: ""

                            val tags = taggable?.tag(columnName, value).orEmpty()

                            SelectionContainer {
                                MarkedText(
                                    text = value,
                                    highlights = tags,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            HorizontalScrollbar(
                adapter = rememberScrollbarAdapter(horizontalScrollState), modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

@Composable
private fun DiffView(
    diffService: DiffService,
    entries: List<Map<String, String>>,
    modifier: Modifier = Modifier,
) {
    val horizontalScrollState = rememberScrollState()

    val grouped = entries.flatMap { it.entries }.groupBy({ it.key }, { it.value }).filterValues { it.isNotEmpty() }

    val greatestSize = grouped.values.maxOfOrNull { it.size } ?: 0

    val columns = entries.flatMap { it.keys }.distinct()

    var currentColumnIndex by remember { mutableStateOf(0) }

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val headerTextStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold)

    val maxWidth = columns.maxOf {
        val headerWidthPx = textMeasurer.measure(text = AnnotatedString(it), style = headerTextStyle).size.width

        with(density) { headerWidthPx.toDp() + 16.dp }
    }

    Box(modifier = modifier) {
        Column {
            Box(modifier = Modifier.padding(horizontal = 32.dp)) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (columns.isEmpty()) return@Column

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

                    Column(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
                        (0 until greatestSize).forEach { index ->
                            val columnName = columns.getOrNull(currentColumnIndex) ?: return@forEach

                            val values = grouped[columnName] ?: return@forEach

                            val value = values.getOrNull(index) ?: ""

                            val change = diffService.getDiff(values[0], value)

                            SelectionContainer {
                                if (index == 0) {
                                    Text(value, modifier = Modifier.fillMaxWidth())
                                    Spacer(Modifier.height(40.dp))
                                } else {
                                    if (value.isEmpty()) Text(value)
                                    else HeatmapText(
                                        change,
                                        values[0],
                                        textAlign = TextAlign.Left,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalScrollbar(
                adapter = rememberScrollbarAdapter(horizontalScrollState), modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}
