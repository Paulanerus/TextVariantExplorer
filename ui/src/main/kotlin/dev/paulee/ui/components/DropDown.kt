package dev.paulee.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

@Composable
fun DropDownMenu(
    modifier: Modifier = Modifier, items: List<String>, left: Boolean = false, clicked: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = true }, modifier = Modifier.padding(16.dp)
        ) {
            Icon(Icons.Default.Menu, contentDescription = "Menu")
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = DpOffset(if (left) 50.dp else (-50).dp, (-15).dp)
        ) {
            items.forEach { item ->
                if (item == "---") Divider(modifier = Modifier.padding(horizontal = 16.dp))
                else DropdownMenuItem(onClick = { clicked(item).also { expanded = false } }) { Text(item) }
            }
        }
    }
}
