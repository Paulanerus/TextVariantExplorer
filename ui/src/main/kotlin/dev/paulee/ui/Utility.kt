package dev.paulee.ui

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import dev.paulee.api.plugin.Tag

fun java.awt.Color.toComposeColor() = Color(red, green, blue, alpha)

@Composable
fun MarkedText(
    modifier: Modifier = Modifier,
    textDecoration: TextDecoration = TextDecoration.None,
    text: String,
    highlights: Map<String, Tag>,
) {
    val annotatedString = buildAnnotatedString {
        var currentIndex = 0

        highlights.forEach { (highlightedWord, tagAndColor) ->
            val (tag, color) = tagAndColor
            val startIndex = text.indexOf(highlightedWord, currentIndex)

            val composeColor = color.toComposeColor()

            if (startIndex != -1) {
                append(text.substring(currentIndex, startIndex))

                withStyle(style = SpanStyle(background = composeColor.copy(alpha = 0.3f))) {
                    append(highlightedWord)
                }

                if (tag.isNotEmpty()) {
                    withStyle(
                        style = SpanStyle(
                            fontSize = 13.sp,
                            background = composeColor.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        append("  $tag ")
                    }
                }

                currentIndex = startIndex + highlightedWord.length
            }
        }

        if (currentIndex < text.length) append(text.substring(currentIndex))
    }
    Text(text = annotatedString, modifier = modifier, textDecoration = textDecoration)
}