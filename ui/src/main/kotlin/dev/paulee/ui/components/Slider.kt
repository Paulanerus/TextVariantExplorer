package dev.paulee.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.paulee.ui.App
import dev.paulee.ui.Config
import dev.paulee.ui.LocalI18n
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

@Composable
fun SliderControl(
    text: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    minValue: Float,
    maxValue: Float,
    defaultValue: Float,
    scaleFactor: Float = 1f,
    decimalCount: Int = 1,
    postfix: String = "",
    modifier: Modifier = Modifier,
) {
    val locale = LocalI18n.current
    val clampedValue = value.coerceIn(minValue, maxValue)
    val supportingColor = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
    val showReset = abs(clampedValue - defaultValue) > 0.001f

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (text.isNotBlank()) {
                Text(
                    text = "$text:",
                    style = MaterialTheme.typography.subtitle2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.85f)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showReset) {
                    IconButton(
                        onClick = { onValueChange(defaultValue) },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = locale["main.query_settings.reset"])
                    }

                    Spacer(modifier = Modifier.width(14.dp))
                }

                Surface(
                    color = MaterialTheme.colors.primary.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colors.primary.copy(alpha = 0.14f))
                ) {
                    Text(
                        text = formatSliderValue(clampedValue, scaleFactor, decimalCount, postfix),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colors.primary
                    )
                }
            }
        }

        Slider(
            value = clampedValue,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            valueRange = minValue..maxValue,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colors.primary,
                activeTrackColor = MaterialTheme.colors.primary,
                inactiveTrackColor = MaterialTheme.colors.onSurface.copy(alpha = 0.15f),
                activeTickColor = MaterialTheme.colors.primary.copy(alpha = 0.4f),
                inactiveTickColor = MaterialTheme.colors.onSurface.copy(alpha = 0.15f),
                disabledThumbColor = MaterialTheme.colors.onSurface.copy(alpha = 0.3f),
                disabledActiveTrackColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
                disabledInactiveTrackColor = MaterialTheme.colors.onSurface.copy(alpha = 0.08f)
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatSliderValue(minValue, scaleFactor, decimalCount, postfix),
                style = MaterialTheme.typography.caption,
                color = supportingColor
            )

            Text(
                text = formatSliderValue(maxValue, scaleFactor, decimalCount, postfix),
                style = MaterialTheme.typography.caption,
                color = supportingColor
            )
        }
    }
}

private fun formatSliderValue(value: Float, scaleFactor: Float, decimalCount: Int, postfix: String): String {
    val pattern = buildString {
        append("%.")
        append(decimalCount.coerceAtLeast(0))
        append("f")
    }

    val numberAsStr = String.format(Locale.forLanguageTag(App.Language.current.tag), pattern, value * scaleFactor)
    return if (postfix.isNotBlank()) "$numberAsStr$postfix" else numberAsStr
}
