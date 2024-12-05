package dev.paulee.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun TableView(columns: List<String>, data: List<List<String>>, onRowSelect: (Set<List<String>>) -> Unit) {
    val selectedRows = remember { mutableStateOf(setOf<Int>()) }
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {
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