package dev.paulee.ui

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

fun java.awt.Color.toComposeColor() = Color(red, green, blue, alpha)

@Composable
fun MarkedText(
    modifier: Modifier = Modifier,
    textDecoration: TextDecoration = TextDecoration.None,
    text: String,
    highlights: Map<String, Color>,
) {
    val annotatedString = buildAnnotatedString {
        var currentIndex = 0

        highlights.keys.forEach { highlight ->
            val startIndex = text.indexOf(highlight, currentIndex)

            if (startIndex != -1) {
                append(text.substring(currentIndex, startIndex))

                withStyle(style = SpanStyle(background = (highlights[highlight] ?: Color.Blue).copy(alpha = 0.3f))) {
                    append(highlight)
                }

                currentIndex = startIndex + highlight.length
            }
        }

        if (currentIndex < text.length) append(text.substring(currentIndex))
    }
    Text(annotatedString, modifier = modifier, textDecoration = textDecoration)
}