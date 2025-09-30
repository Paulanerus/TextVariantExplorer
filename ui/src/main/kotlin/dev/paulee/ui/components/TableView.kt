package dev.paulee.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.paulee.api.data.provider.QueryOrder
import dev.paulee.api.plugin.Tag
import dev.paulee.ui.*

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun TableView(
    modifier: Modifier = Modifier,
    pool: String,
    indexStrings: Set<String> = emptySet(),
    columns: List<String>,
    data: List<List<String>>,
    links: Map<String, List<Map<String, String>>> = emptyMap(),
    queryOrder: QueryOrder?,
    onQueryOrderChange: (QueryOrder) -> Unit = {},
    totalAmountOfSelectedRows: Int,
    selectedIndices: Set<Int>,
    onSelectionChange: (Set<Int>) -> Unit,
    clicked: () -> Unit = {},
) {
    val locale = LocalI18n.current

    val hiddenColumnsScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val verticalScrollState = rememberLazyListState()

    var hiddenColumns by remember { mutableStateOf(Config.getHidden(pool)) }
    var panelExpanded by remember { mutableStateOf(false) }

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val radius = 12.dp
    val hairline = MaterialTheme.colors.onSurface.copy(alpha = 0.08f)
    val divider = MaterialTheme.colors.onSurface.copy(alpha = 0.07f)
    val headerBg = MaterialTheme.colors.surface
    val headerTextStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.SemiBold)
    val cellTextStyle = LocalTextStyle.current

    val columnWidths = remember(columns, data, Config.noWidthRestriction) {
        columns.mapIndexed { colIndex, colName ->
            val headerWidthPx = textMeasurer.measure(
                text = AnnotatedString(colName), style = headerTextStyle
            ).size.width
            val headerWidth = with(density) { headerWidthPx.toDp() * 2 }

            val maxDataWidthPx = data.map { it[colIndex] }.maxOf { text ->
                textMeasurer.measure(
                    text = AnnotatedString(text), style = cellTextStyle
                ).size.width
            }

            val maxDataWidth = with(density) { maxDataWidthPx.toDp() }

            if (Config.noWidthRestriction) maxOf(headerWidth, maxDataWidth) + 16.dp
            else minOf(maxOf(headerWidth, maxDataWidth) + 16.dp, 700.dp)
        }
    }

    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = {
                        onSelectionChange(emptySet())
                    }, enabled = selectedIndices.isNotEmpty(), modifier = Modifier.width(48.dp).padding(bottom = 12.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = locale["table.delete"])
                }

                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = { panelExpanded = !panelExpanded },
                            shape = RoundedCornerShape(50.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.width(170.dp)
                        ) {
                            Icon(
                                imageVector = if (panelExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (panelExpanded) locale["table.hide"] else locale["table.show"],
                                modifier = Modifier.size(18.dp)
                            )

                            Spacer(Modifier.width(8.dp))

                            Text(if (panelExpanded) locale["table.hide"] else locale["table.show"])
                        }
                    }

                    AnimatedVisibility(
                        visible = panelExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.animateContentSize()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.horizontalScroll(hiddenColumnsScrollState)
                            ) {
                                columns.forEachIndexed { index, label ->
                                    val isHidden = hiddenColumns.contains(index)

                                    ColumnChip(
                                        label = label,
                                        selected = !isHidden,
                                        onClick = {
                                            hiddenColumns =
                                                if (isHidden) hiddenColumns - index else hiddenColumns + index
                                            Config.setHidden(pool, hiddenColumns)
                                        }
                                    )
                                }
                            }

                            HorizontalScrollbar(
                                adapter = rememberScrollbarAdapter(hiddenColumnsScrollState),
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }

                Button(
                    onClick = clicked,
                    shape = RoundedCornerShape(50.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    enabled = totalAmountOfSelectedRows > 0,
                    modifier = Modifier.width(140.dp).padding(bottom = 12.dp)
                ) {
                    Text(locale["table.insights"])
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                shape = RoundedCornerShape(radius),
                border = BorderStroke(1.dp, hairline),
                elevation = 0.dp,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(modifier = Modifier.clip(RoundedCornerShape(radius))) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(horizontalScrollState)
                            .background(headerBg)
                            .drawBehind {
                                val y = size.height - 0.5.dp.toPx()
                                drawLine(
                                    divider,
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                    ) {
                        columns.forEachIndexed { index, columnName ->
                            if (hiddenColumns.contains(index)) return@forEachIndexed

                            val isSorted = queryOrder?.first == columnName
                            val sortedBg =
                                if (isSorted) MaterialTheme.colors.primary.copy(alpha = 0.1f)
                                else MaterialTheme.colors.onSurface.copy(alpha = 0.06f)

                            Box(
                                modifier = Modifier
                                    .height(IntrinsicSize.Min)
                                    .width(columnWidths[index])
                                    .fillMaxWidth()
                                    .background(sortedBg)
                                    .clickable {
                                        val newQueryOrderState = if (queryOrder?.first == columnName) {
                                            QueryOrder(columnName, !queryOrder.second)
                                        } else {
                                            QueryOrder(columnName, false)
                                        }
                                        onQueryOrderChange(newQueryOrderState)
                                    }
                                    .drawBehind {
                                        if (columns.lastIndex == index) return@drawBehind
                                        drawLine(
                                            color = divider,
                                            start = Offset(size.width - 0.5.dp.toPx(), 0f),
                                            end = Offset(size.width - 0.5.dp.toPx(), size.height),
                                            strokeWidth = 1.dp.toPx()
                                        )
                                    }
                            ) {
                                val rotation by animateFloatAsState(
                                    targetValue = if (isSorted && queryOrder.second) 180f else 0f
                                )
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp, vertical = 10.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = columnName,
                                        style = headerTextStyle,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isSorted) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowUp,
                                            contentDescription = if (queryOrder.second) locale["table.desc"] else locale["table.asc"],
                                            modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = rotation },
                                            tint = MaterialTheme.colors.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
                            LazyColumn(state = verticalScrollState) {
                                items(data.size) { rowIndex ->
                                    val row = data[rowIndex]

                                    val isSelected = selectedIndices.contains(rowIndex)
                                    var hovered by remember { mutableStateOf(false) }

                                    val baseRow = if (rowIndex % 2 == 0)
                                        MaterialTheme.colors.onSurface.copy(alpha = 0.03f)
                                    else Color.Transparent

                                    val rowBg = when {
                                        isSelected -> MaterialTheme.colors.primary.copy(alpha = 0.12f)
                                        hovered -> MaterialTheme.colors.onSurface.copy(alpha = 0.14f)
                                        else -> baseRow
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onPointerEvent(PointerEventType.Enter) { hovered = true }
                                            .onPointerEvent(PointerEventType.Exit) { hovered = false }
                                            .pointerInput(rowIndex, selectedIndices) {
                                                detectTapGestures(
                                                    onTap = {
                                                        val newSelection =
                                                            if (selectedIndices.contains(rowIndex)) {
                                                                selectedIndices - rowIndex
                                                            } else {
                                                                selectedIndices + rowIndex
                                                            }

                                                        onSelectionChange(newSelection)
                                                    }
                                                )
                                            }
                                            .background(rowBg)
                                            .padding(vertical = 10.dp),
                                    ) {
                                        row.forEachIndexed { colIndex, cell ->
                                            if (hiddenColumns.contains(colIndex)) return@forEachIndexed

                                            val col = columns[colIndex]
                                            val link = links[col]?.find { it[col] == cell }

                                            Tooltip(
                                                state = link != null,
                                                tooltip = {
                                                    Column(modifier = Modifier.padding(8.dp)) {
                                                        link?.filter { !it.key.endsWith("_ag_id") }
                                                            ?.forEach { entry ->
                                                                Row {
                                                                    Text(
                                                                        text = entry.key,
                                                                        fontWeight = FontWeight.SemiBold
                                                                    )
                                                                    Text(text = ": ${entry.value}")
                                                                }
                                                            }
                                                    }
                                                }
                                            ) {
                                                SelectionContainer {
                                                    MarkedText(
                                                        modifier = Modifier
                                                            .width(columnWidths[colIndex])
                                                            .padding(horizontal = 8.dp),
                                                        underline = link != null,
                                                        text = cell,
                                                        highlights = if (indexStrings.isEmpty()) emptyMap() else indexStrings.associateWith {
                                                            Tag(
                                                                "",
                                                                App.Colors.GREEN_HIGHLIGHT
                                                            )
                                                        },
                                                        exact = Config.exactHighlighting
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        VerticalScrollbar(
                            adapter = rememberScrollbarAdapter(verticalScrollState),
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .padding(end = 4.dp)
                        )

                        HorizontalScrollbar(
                            adapter = rememberScrollbarAdapter(horizontalScrollState),
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(
        targetValue = if (selected)
            MaterialTheme.colors.primary.copy(alpha = 0.08f)
        else
            MaterialTheme.colors.surface
    )
    val border by animateColorAsState(
        targetValue = if (selected)
            MaterialTheme.colors.primary.copy(alpha = 0.65f)
        else
            Color.LightGray.copy(alpha = 0.7f)
    )
    val content by animateColorAsState(
        targetValue = if (selected)
            MaterialTheme.colors.primary
        else
            LocalContentColor.current.copy(alpha = 0.65f)
    )

    Surface(
        color = bg,
        shape = RoundedCornerShape(50.dp),
        border = BorderStroke(1.dp, border),
        elevation = 0.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClick = onClick, role = Role.Button)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = label,
                color = content,
                style = LocalTextStyle.current.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            )
        }
    }
}