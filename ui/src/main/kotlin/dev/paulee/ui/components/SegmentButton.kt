package dev.paulee.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

@Composable
fun TwoSegmentButton(
    left: String,
    right: String,
    selected: Boolean,
    onClick: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()

    val leftWidth = textMeasurer.measure(
        text = AnnotatedString(left),
        style = LocalTextStyle.current
    ).size.width

    val rightWidth = textMeasurer.measure(
        text = AnnotatedString(right),
        style = LocalTextStyle.current
    ).size.width

    val maxSegmentWidth = (maxOf(leftWidth, rightWidth) + 32).dp

    Row(modifier = modifier) {
        Box(
            modifier = Modifier
                .width(maxSegmentWidth)
                .clickable { onClick(false) }
                .background(
                    if (selected == false) Color.Gray else Color.LightGray,
                    shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = left)
        }

        Box(
            modifier = Modifier
                .width(maxSegmentWidth)
                .clickable { onClick(true) }
                .background(
                    if (selected == true) Color.Gray else Color.LightGray,
                    shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = right)
        }
    }
}