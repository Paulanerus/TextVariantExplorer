package dev.paulee.ui.components

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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.paulee.api.plugin.Tag
import dev.paulee.ui.Config
import dev.paulee.ui.MarkedText

var widthLimitWrapper by mutableStateOf(Config.noWidthRestriction)

var exactHighlightingWrapper by mutableStateOf(Config.exactHighlighting)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TableView(
    modifier: Modifier = Modifier,
    pool: String,
    indexStrings: Set<String> = emptySet(),
    columns: List<String>,
    data: List<List<String>>,
    links: Map<String, List<Map<String, String>>> = emptyMap(),
    onRowSelect: (List<Map<String, String>>) -> Unit,
    clicked: () -> Unit = {},
) {
    val hiddenColumnsScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val verticalScrollState = rememberLazyListState()

    var selectedRows by remember { mutableStateOf(setOf<Int>()) }
    var hiddenColumns by remember { mutableStateOf(Config.getHidden(pool)) }

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val headerTextStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold)
    val cellTextStyle = LocalTextStyle.current

    val columnWidths = remember(columns, data, widthLimitWrapper) {
        columns.mapIndexed { colIndex, colName ->
            val headerWidthPx = textMeasurer.measure(
                text = AnnotatedString(colName), style = headerTextStyle
            ).size.width

            val headerWidth = with(density) { headerWidthPx.toDp() }

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
                        selectedRows = emptySet()
                        onRowSelect(emptyList())
                    }, enabled = selectedRows.isNotEmpty(), modifier = Modifier.width(48.dp).padding(bottom = 12.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }

                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.horizontalScroll(hiddenColumnsScrollState)
                    ) {
                        columns.forEachIndexed { index, column ->
                            Button(onClick = {
                                hiddenColumns = if (hiddenColumns.contains(index)) hiddenColumns - index
                                else hiddenColumns + index

                                Config.setHidden(pool, hiddenColumns)
                            }, colors = ButtonDefaults.buttonColors(backgroundColor = Color.LightGray)) {
                                Text(column)
                            }
                        }
                    }

                    HorizontalScrollbar(
                        adapter = rememberScrollbarAdapter(hiddenColumnsScrollState),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Button(
                    onClick = clicked,
                    enabled = selectedRows.isNotEmpty(),
                    modifier = Modifier.width(140.dp).padding(bottom = 12.dp)
                ) {
                    Text("View Insights")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(horizontalScrollState).background(Color.Gray)
            ) {
                columns.forEachIndexed { index, columnName ->
                    if (hiddenColumns.contains(index)) return@forEachIndexed

                    Box(
                        modifier = Modifier.height(IntrinsicSize.Min).width(columnWidths[index]).drawBehind {
                            if (columns.lastIndex == index) return@drawBehind

                            val strokeWidth = 2.dp.toPx()
                            val halfStrokeWidth = strokeWidth / 2
                            drawLine(
                                color = Color.DarkGray,
                                start = Offset(size.width - halfStrokeWidth, 0f),
                                end = Offset(size.width - halfStrokeWidth, size.height),
                                strokeWidth = strokeWidth
                            )
                        }) {
                        Text(
                            text = columnName,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier.horizontalScroll(horizontalScrollState)
                ) {
                    LazyColumn(state = verticalScrollState) {
                        items(data.size) { rowIndex ->
                            val row = data[rowIndex]
                            Row(
                                modifier = Modifier.fillMaxWidth().pointerInput(rowIndex, selectedRows) {
                                    detectTapGestures(
                                        onTap = {
                                            val newSelection =
                                                if (selectedRows.contains(rowIndex)) {
                                                    selectedRows - rowIndex
                                                } else {
                                                    selectedRows + rowIndex
                                                }
                                            selectedRows = newSelection
                                            onRowSelect(
                                                newSelection.map { idx ->
                                                    columns.zip(data[idx]).toMap()
                                                }
                                            )
                                        }
                                    )
                                }
                                    .background(if (selectedRows.contains(rowIndex)) Color.LightGray else Color.Transparent)
                                    .padding(vertical = 8.dp),
                            ) {
                                row.forEachIndexed { colIndex, cell ->
                                    if (hiddenColumns.contains(colIndex)) return@forEachIndexed

                                    val col = columns[colIndex]

                                    val link = links[col]?.find { it[col] == cell }

                                    TooltipArea(
                                        tooltip = {
                                            if (link == null) return@TooltipArea

                                            Surface(
                                                color = Color.LightGray, shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Column(modifier = Modifier.padding(8.dp)) {
                                                    link.filter { !it.key.endsWith("_ag_id") }.forEach { entry ->
                                                        Row {
                                                            Text(text = entry.key, fontWeight = FontWeight.Bold)
                                                            Text(text = ": ${entry.value}")
                                                        }
                                                    }
                                                }
                                            }
                                        }) {
                                        SelectionContainer {
                                            MarkedText(
                                                modifier = Modifier.width(columnWidths[colIndex])
                                                    .padding(horizontal = 4.dp),
                                                textDecoration = if (link == null) TextDecoration.None else TextDecoration.Underline,
                                                text = cell,
                                                highlights = if (indexStrings.isEmpty()) emptyMap() else indexStrings.associateWith {
                                                    Tag(
                                                        "",
                                                        java.awt.Color.green
                                                    )
                                                },
                                                exact = exactHighlightingWrapper
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
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 4.dp)
                )

                HorizontalScrollbar(
                    adapter = rememberScrollbarAdapter(horizontalScrollState),
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}