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
private fun DropDownBase(
    modifier: Modifier = Modifier,
    items: List<String>,
    onSelected: (String) -> Unit,
    trigger: @Composable (openMenu: () -> Unit) -> Unit,
    menuOffset: DpOffset,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        trigger { expanded = true }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = menuOffset
        ) {
            items.forEach { item ->
                if (item == "---") Divider(modifier = Modifier.padding(horizontal = 16.dp))
                else DropdownMenuItem(onClick = { onSelected(item); expanded = false }) { Text(item) }
            }
        }
    }
}

@Composable
fun IconDropDown(
    modifier: Modifier = Modifier,
    items: List<String>,
    left: Boolean = false,
    menuOffset: DpOffset = DpOffset(if (left) 50.dp else (-50).dp, (-15).dp),
    onSelected: (String) -> Unit,
) = DropDownBase(
    modifier = modifier,
    items = items,
    onSelected = onSelected,
    trigger = { openMenu ->
        IconButton(
            onClick = openMenu,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(Icons.Default.Menu, contentDescription = "Menu")
        }
    },
    menuOffset = menuOffset
)

@Composable
fun ButtonDropDown(
    modifier: Modifier = Modifier,
    items: List<String>,
    selected: String,
    left: Boolean = false,
    menuOffset: DpOffset = DpOffset(if (left) 50.dp else (-50).dp, (-15).dp),
    onSelected: (String) -> Unit,
) = DropDownBase(
    modifier = modifier,
    items = items,
    onSelected = onSelected,
    trigger = { openMenu ->
        Button(
            onClick = openMenu,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(selected)
        }
    },
    menuOffset = menuOffset
)