package dev.paulee.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.paulee.ui.MarkedText

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TableView(
    modifier: Modifier = Modifier,
    indexStrings: Set<String> = emptySet<String>(),
    columns: List<String>,
    data: List<List<String>>,
    links: Map<String, List<Map<String, String>>>,
    onRowSelect: (Set<List<String>>) -> Unit,
    clicked: () -> Unit = {},
) {
    val scrollState = rememberScrollState()
    val verticalScrollState = rememberLazyListState()

    var selectedRows by remember { mutableStateOf(setOf<Int>()) }
    var hiddenColumns by remember { mutableStateOf(setOf<Int>()) }

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val headerTextStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold)
    val cellTextStyle = LocalTextStyle.current

    val columnWidths = remember(columns, data) {
        columns.mapIndexed { colIndex, colName ->
            val headerWidthPx = textMeasurer.measure(
                text = AnnotatedString(colName),
                style = headerTextStyle
            ).size.width

            val headerWidth = with(density) { headerWidthPx.toDp() }

            val maxDataWidthPx = data.map { it[colIndex] }.maxOf { text ->
                textMeasurer.measure(
                    text = AnnotatedString(text),
                    style = cellTextStyle
                ).size.width
            }

            val maxDataWidth = with(density) { maxDataWidthPx.toDp() }

            minOf(maxOf(headerWidth, maxDataWidth) + 16.dp, 700.dp)
        }
    }

    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = {
                        selectedRows = emptySet()
                        onRowSelect(emptySet())
                    }, modifier = Modifier.align(Alignment.CenterStart), enabled = selectedRows.isNotEmpty()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.align(Alignment.Center)) {
                    columns.forEachIndexed { index, column ->
                        Button(onClick = {
                            hiddenColumns = if (hiddenColumns.contains(index))
                                hiddenColumns - index
                            else
                                hiddenColumns + index

                        }, colors = ButtonDefaults.buttonColors(backgroundColor = Color.LightGray)) {
                            Text(column)
                        }
                    }
                }

                Button(
                    onClick = clicked,
                    enabled = selectedRows.isNotEmpty(),
                    modifier = Modifier.width(120.dp).align(Alignment.CenterEnd)
                ) {
                    if (selectedRows.size <= 1) Text("View")
                    else Text("View Diff")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState).background(Color.Gray)
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
                        }
                    ) {
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
                    modifier = Modifier.horizontalScroll(scrollState)
                ) {
                    LazyColumn(state = verticalScrollState) {
                        items(data.size) { rowIndex ->
                            val row = data[rowIndex]
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    val selected = if (selectedRows.contains(rowIndex)) {
                                        selectedRows - rowIndex
                                    } else {
                                        selectedRows + rowIndex
                                    }
                                    selectedRows = selected
                                    onRowSelect(selected.map { data[it] }.toSet())
                                }
                                    .background(if (selectedRows.contains(rowIndex)) Color.LightGray else Color.Transparent)
                                    .padding(vertical = 8.dp),
                            ) {
                                row.forEachIndexed { colIndex, cell ->
                                    if (hiddenColumns.contains(colIndex)) return@forEachIndexed

                                    val col = columns[colIndex]

                                    val link = links[col]?.find { it[col] != null }

                                    TooltipArea(
                                        tooltip = {
                                            if (link == null) return@TooltipArea

                                            Surface(
                                                color = Color.Gray,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = link.toString(),
                                                    modifier = Modifier.padding(10.dp)
                                                )
                                            }
                                        }
                                    ) {
                                        MarkedText(
                                            modifier = Modifier.width(columnWidths[colIndex])
                                                .padding(horizontal = 4.dp),
                                            textDecoration = if (link == null) TextDecoration.None else TextDecoration.Underline,
                                            text = cell,
                                            highlights = indexStrings,
                                            color = Color.Green
                                        )
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
                    adapter = rememberScrollbarAdapter(scrollState), modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}