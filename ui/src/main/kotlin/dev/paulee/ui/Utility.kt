package dev.paulee.ui

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import dev.paulee.api.data.Change
import dev.paulee.api.plugin.Tag

fun java.awt.Color.toComposeColor() = Color(red, green, blue, alpha)

internal data class HighlightMatch(val start: Int, val end: Int, val word: String, val tag: String, val color: Color)

@Composable
fun MarkedText(
    modifier: Modifier = Modifier,
    textDecoration: TextDecoration = TextDecoration.None,
    textAlign: TextAlign = TextAlign.Start,
    text: String,
    highlights: Map<String, Tag>,
) {
    val allMatches = remember(text, highlights) {
        buildList {
            highlights.forEach { (word, tagAndColor) ->
                val (tag, color) = tagAndColor

                var searchIndex = 0

                while (true) {
                    val foundIndex = text.indexOf(word, searchIndex)

                    if (foundIndex == -1) break

                    add(
                        HighlightMatch(
                            start = foundIndex,
                            end = foundIndex + word.length,
                            word = word,
                            tag = tag,
                            color = color.toComposeColor()
                        )
                    )

                    searchIndex = foundIndex + word.length
                }
            }
        }.sortedBy { it.start }
    }

    val annotatedString = remember(text, allMatches) {
        buildAnnotatedString {
            var currentIndex = 0

            allMatches.forEach {
                if (it.start > currentIndex) append(text.substring(currentIndex, it.start))

                withStyle(style = SpanStyle(background = it.color.copy(alpha = 0.3f))) {
                    append("${it.word} ")
                }

                if (it.tag.isNotEmpty()) {
                    withStyle(
                        style = SpanStyle(
                            fontSize = 13.sp, background = it.color.copy(alpha = 0.8f), fontWeight = FontWeight.Bold
                        )
                    ) {
                        append(" ${it.tag} ")
                    }
                }

                currentIndex = it.end
            }

            if (currentIndex < text.length) append(text.substring(currentIndex))
        }
    }
    Text(text = annotatedString, modifier = modifier, textDecoration = textDecoration, textAlign = textAlign)
}

@Composable
fun HeatmapText(
    change: Change?,
    fallback: String,
    modifier: Modifier = Modifier,
    textDecoration: TextDecoration = TextDecoration.None,
    textAlign: TextAlign = TextAlign.Center,
) {
    val text = change?.str ?: fallback

    val annotatedString = remember(change) {
        buildAnnotatedString {
            var currentIndex = 0

            val sortedTokens = change?.tokens.orEmpty().sortedBy { it.second.first }

            sortedTokens.forEach { (token, _) ->
                val startIndex = text.indexOf(token, currentIndex)

                if (startIndex != -1) {
                    append(text.substring(currentIndex, startIndex))

                    val processedToken = when {
                        token.startsWith("~~") && token.endsWith("~~") -> {
                            withStyle(style = SpanStyle(background = Color.Red.copy(alpha = 0.3f))) {
                                append(" ".repeat(token.trim('~').length))
                            }
                            ""
                        }

                        token.startsWith("**") && token.endsWith("**") -> {
                            val trimmedToken = token.removeSurrounding("**")
                            withStyle(style = SpanStyle(background = Color.Green.copy(alpha = 0.3f))) {
                                append(trimmedToken)
                            }
                            ""
                        }

                        else -> token
                    }

                    if (processedToken.isNotEmpty()) append(processedToken)

                    currentIndex = startIndex + token.length
                }
            }

            if (currentIndex < text.length) append(text.substring(currentIndex))
        }
    }
    Text(text = annotatedString, modifier = modifier, textDecoration = textDecoration, textAlign = textAlign)
}
