package dev.paulee.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TwoSegmentButton(
    left: String,
    right: String,
    selected: Boolean,
    onClick: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val style = LocalTextStyle.current.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium)

    val maxSegmentWidth = remember(left, right, style) {
        val leftWidth = textMeasurer.measure(text = AnnotatedString(left), style = style).size.width
        val rightWidth = textMeasurer.measure(text = AnnotatedString(right), style = style).size.width

        with(density) { maxOf(leftWidth, rightWidth).toDp() + 30.dp }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50.dp),
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f)),
        elevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            SegmentToggleItem(
                text = left, width = maxSegmentWidth, selected = !selected, onClick = { onClick(false) })

            SegmentToggleItem(
                text = right, width = maxSegmentWidth, selected = selected, onClick = { onClick(true) })
        }
    }
}

@Composable
private fun SegmentToggleItem(
    text: String,
    width: Dp,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colors.primary.copy(alpha = 0.16f)
        else MaterialTheme.colors.surface, animationSpec = tween(180)
    )

    val textColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface.copy(alpha = 0.8f),
        animationSpec = tween(180)
    )

    Surface(
        color = bg,
        shape = RoundedCornerShape(50.dp),
        border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = if (selected) 0.12f else 0.04f))
    ) {
        Box(
            modifier = Modifier.width(width).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Medium
            )
        }
    }
}