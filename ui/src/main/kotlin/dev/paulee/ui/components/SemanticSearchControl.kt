package dev.paulee.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.paulee.ui.Config
import dev.paulee.ui.LocalI18n
import dev.paulee.ui.Tooltip
import kotlin.math.round

@Composable
fun SemanticSearchControl(
    selected: Boolean,
    enabled: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalI18n.current

    val outlineColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colors.primary else Color.LightGray
    )
    val shape = RoundedCornerShape(24.dp)

    Tooltip(
        state = !enabled,
        tooltip = {
            Text(
                modifier = Modifier.padding(8.dp),
                text = locale["main.tooltip.no_semantic"]
            )
        }
    ) {
        Row(
            modifier = modifier
                .height(36.dp)
                .clip(shape)
                .border(0.75.dp, outlineColor, shape)
                .background(
                    if (selected) MaterialTheme.colors.primary.copy(alpha = 0.08f)
                    else Color.Gray.copy(alpha = 0.02f)
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { onSelectedChange(!selected) },
                enabled = enabled,
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(0.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (selected) MaterialTheme.colors.primary else Color.Gray
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(locale["main.search.semantic"])
            }

            AnimatedVisibility(
                visible = selected,
                enter = fadeIn(tween(100)) + expandHorizontally(tween(100)),
                exit = fadeOut(tween(100)) + shrinkHorizontally(tween(100))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Divider(
                        modifier = Modifier.width(0.75.dp).height(20.dp),
                        color = MaterialTheme.colors.primary.copy(alpha = 0.3f)
                    )
                    SemanticSettingsButton()
                }
            }
        }
    }
}

@Composable
private fun SemanticSettingsButton() {
    val locale = LocalI18n.current

    var expanded by remember { mutableStateOf(false) }

    val popupProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(if (expanded) 140 else 90)
    )

    Box {
        IconButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Default.Settings,
                contentDescription = locale["main.query_settings.title"],
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colors.primary
            )
        }

        if (expanded || popupProgress > 0f) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, 40),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true)
            ) {
                Card(
                    modifier = Modifier
                        .width(280.dp)
                        .graphicsLayer {
                            alpha = popupProgress
                            scaleX = 0.94f + popupProgress * 0.06f
                            scaleY = 0.94f + popupProgress * 0.06f
                            translationY = (popupProgress - 1f) * 4.dp.toPx()
                            transformOrigin = TransformOrigin(0f, 0f)
                        },
                    backgroundColor = MaterialTheme.colors.surface,
                    elevation = 4.dp
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(locale["main.query_settings.title"], style = MaterialTheme.typography.subtitle1)

                        Spacer(modifier = Modifier.height(8.dp))

                        SliderControl(
                            text = locale["main.query_settings.similarity"],
                            value = Config.queryEmbSimilarity,
                            onValueChange = { Config.queryEmbSimilarity = round(it * 100) / 100f },
                            minValue = 0.5f,
                            maxValue = 1.0f,
                            defaultValue = 0.8f,
                            scaleFactor = 100f,
                            decimalCount = 0,
                            postfix = " %"
                        )
                    }
                }
            }
        }
    }
}