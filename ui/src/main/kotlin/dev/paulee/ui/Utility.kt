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
import dev.paulee.api.plugin.Drawable
import dev.paulee.api.plugin.Tag
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.valueParameters

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
            highlights.filter { it.key.isNotBlank() }.forEach { (word, tagAndColor) ->
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

@Composable
@Suppress("UNCHECKED_CAST")
fun invokeDrawable(drawable: Drawable, entries: List<Map<String, String>>) {
    drawable::class.declaredFunctions
        .find { it.name == "composeContent" }
        ?.takeIf { it.isComposableFunction() }
        ?.let {
            when {
                it.hasNoParameters() -> (it.call(drawable) as? (@Composable () -> Unit))?.invoke()
                it.hasEntryParameter() -> (it.call(drawable, entries) as? (@Composable () -> Unit))?.invoke()
                else -> {}
            }
        }
}

private fun KFunction<*>.isComposableFunction(): Boolean =
    returnType.classifier == Function0::class && returnType.hasAnnotation<Composable>()

private fun KFunction<*>.hasNoParameters(): Boolean =
    valueParameters.isEmpty()

private fun KFunction<*>.hasEntryParameter(): Boolean =
    valueParameters.singleOrNull()?.type?.hasEntryType() == true

private fun KType.hasEntryType(): Boolean {
    if (this.classifier != List::class || this.arguments.size != 1) return false

    val argType = this.arguments.firstOrNull()?.type ?: return false

    if (argType.classifier != Map::class || argType.arguments.size != 2) return false

    val keyType = argType.arguments[0].type?.classifier

    val valueType = argType.arguments[1].type?.classifier

    return keyType == String::class && valueType == String::class
}
