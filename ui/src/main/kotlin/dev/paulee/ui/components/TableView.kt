package dev.paulee.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.collections.isNotEmpty

@Composable
fun TableView(columns: List<String>, data: List<List<String>>, onRowSelect: (Set<List<String>>) -> Unit, clicked: () -> Unit = {}) {
    val selectedRows = remember { mutableStateOf(setOf<Int>()) }
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
            IconButton(
                onClick = {
                    selectedRows.value = emptySet()
                    onRowSelect(emptySet())
                },
                modifier = Modifier.align(Alignment.CenterStart),
                enabled = selectedRows.value.isNotEmpty()
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
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

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState).background(Color.Gray)
                .padding(vertical = 8.dp)
        ) {
            columns.forEach {
                Text(
                    text = it,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(175.dp).padding(horizontal = 4.dp)
                )
            }
        }

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier.horizontalScroll(scrollState)
            ) {
                LazyColumn {
                    items(data.size) { index ->
                        val row = data[index]
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(22.dp),
                            modifier = Modifier.fillMaxWidth().clickable {
                                val selected = if (selectedRows.value.contains(index)) {
                                    selectedRows.value - index
                                } else {
                                    selectedRows.value + index
                                }

                                selectedRows.value = selected
                                onRowSelect(selected.map { data[it] }.toSet())
                            }.background(if (selectedRows.value.contains(index)) Color.LightGray else Color.Transparent)
                                .padding(vertical = 8.dp),
                        ) {
                            row.forEach { cell ->
                                Text(
                                    text = cell, modifier = Modifier.width(175.dp).padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            HorizontalScrollbar(
                adapter = rememberScrollbarAdapter(scrollState), modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}