package dev.paulee.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.awt.Cursor

@Composable
fun TableView(
    modifier: Modifier = Modifier,
    columns: List<String>,
    data: List<List<String>>,
    onRowSelect: (Set<List<String>>) -> Unit,
    clicked: () -> Unit = {},
) {
    val selectedRows = remember { mutableStateOf(setOf<Int>()) }
    val scrollState = rememberScrollState()
    val verticalScrollState = rememberLazyListState()
    val hiddenColumns = remember { mutableStateOf(setOf<Int>()) }

    val columnWidths = remember {
        columns.map { mutableStateOf(175.dp) }
    }

    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = {
                        selectedRows.value = emptySet()
                        onRowSelect(emptySet())
                    }, modifier = Modifier.align(Alignment.CenterStart), enabled = selectedRows.value.isNotEmpty()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.align(Alignment.Center)) {
                    columns.forEachIndexed { index, column ->
                        Button(onClick = {
                            if (hiddenColumns.value.contains(index))
                                hiddenColumns.value = hiddenColumns.value - index
                            else
                                hiddenColumns.value = hiddenColumns.value + index

                        }, colors = ButtonDefaults.buttonColors(backgroundColor = Color.LightGray)) {
                            Text(column)
                        }
                    }
                }

                Button(
                    onClick = clicked,
                    enabled = selectedRows.value.isNotEmpty(),
                    modifier = Modifier.width(120.dp).align(Alignment.CenterEnd)
                ) {
                    if (selectedRows.value.size <= 1) Text("View")
                    else Text("View Diff")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState).background(Color.Gray)
            ) {
                columns.forEachIndexed { index, columnName ->
                    if (hiddenColumns.value.contains(index)) return@forEachIndexed

                    Box(
                        modifier = Modifier.height(IntrinsicSize.Min).width(columnWidths[index].value).drawBehind {
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

                        Box(
                            modifier = Modifier.align(Alignment.CenterEnd).width(4.dp)
                                .fillMaxHeight()
                                .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val newWidth =
                                            (columnWidths[index].value + dragAmount.x.dp).coerceAtLeast(50.dp)
                                        columnWidths[index].value = newWidth
                                    }
                                })
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
                                    val selected = if (selectedRows.value.contains(rowIndex)) {
                                        selectedRows.value - rowIndex
                                    } else {
                                        selectedRows.value + rowIndex
                                    }
                                    selectedRows.value = selected
                                    onRowSelect(selected.map { data[it] }.toSet())
                                }
                                    .background(if (selectedRows.value.contains(rowIndex)) Color.LightGray else Color.Transparent)
                                    .padding(vertical = 8.dp),
                            ) {
                                row.forEachIndexed { colIndex, cell ->
                                    if (hiddenColumns.value.contains(colIndex)) return@forEachIndexed

                                    Text(
                                        text = cell,
                                        modifier = Modifier.width(columnWidths[colIndex].value)
                                            .padding(horizontal = 4.dp)
                                    )
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