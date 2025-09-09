package dev.paulee.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.paulee.api.data.Change
import dev.paulee.api.plugin.Drawable
import dev.paulee.api.plugin.Tag
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.valueParameters

private val NON_ALPHANUMERIC = Regex("[^A-Za-z0-9_]")

private val FORBIDDEN_PREFIXES = listOf("sqlite_")

fun normalizeSourceName(str: String): String {
    val replaced = NON_ALPHANUMERIC.replace(str, "_")

    val needsUnderscore =
        replaced.firstOrNull()?.isDigit() == true || FORBIDDEN_PREFIXES.any { replaced.startsWith(it) }

    return (if (needsUnderscore) "_$replaced" else replaced).trim()
}

fun java.awt.Color.toComposeColor(newAlpha: Int = alpha) = Color(red, green, blue, newAlpha)

fun String.capitalize() = lowercase().replaceFirstChar { it.uppercase() }

internal sealed class LoadState {
    object Idle : LoadState()
    data class Loading(val message: String = "") : LoadState()
    data class Success(val message: String = "") : LoadState()
    data class Error(val message: String) : LoadState()
}

internal data class HighlightMatch(val start: Int, val end: Int, val word: String, val tag: String, val color: Color)

internal data class AutocompleteContext(val field: String, val value: String, val startPos: Int, val endPos: Int)

internal object Autocomplete {
    fun getAutocompleteContext(text: String, caret: Int): AutocompleteContext? {
        val tokenStart = text.lastIndexOf(' ', caret - 1).let { if (it == -1) 0 else it + 1 }
        val tokenEnd = text.indexOf(' ', caret).let { if (it == -1) text.length else it }

        val token = text.substring(tokenStart, tokenEnd)
        val colonIndex = token.indexOf(':')

        return if (colonIndex > 0) {
            AutocompleteContext(
                field = token.take(colonIndex),
                value = token.substring(colonIndex + 1),
                startPos = tokenStart + colonIndex + 1,
                endPos = tokenEnd
            )
        } else null
    }

    fun acceptSuggestion(textField: TextFieldValue, suggestion: String): TextFieldValue? {
        return getAutocompleteContext(textField.text, textField.selection.start)?.let { ctx ->
            val newText = textField.text.replaceRange(ctx.startPos, ctx.endPos, suggestion)
            val newCaretPos = ctx.startPos + suggestion.length

            TextFieldValue(newText, TextRange(newCaretPos))
        }
    }
}

@Composable
fun MarkedText(
    modifier: Modifier = Modifier,
    textDecoration: TextDecoration = TextDecoration.None,
    textAlign: TextAlign = TextAlign.Start,
    text: String,
    highlights: Map<String, Tag>,
    exact: Boolean = false,
) {
    val allMatches = remember(text, highlights, exact) {
        buildList {
            highlights.filter { it.key.isNotBlank() && it.key.length > 1 }.forEach { (word, tagAndColor) ->
                val (tag, color) = tagAndColor

                var searchIndex = 0

                while (true) {
                    val foundIndex = text.indexOf(word, searchIndex)
                    if (foundIndex == -1) break

                    val endIndex = foundIndex + word.length


                    val isStartBoundary = foundIndex == 0 || !text[foundIndex - 1].isLetterOrDigit()
                    val isEndBoundary = endIndex == text.length || !text[endIndex].isLetterOrDigit()

                    val shouldHighlight = if (exact) isStartBoundary && isEndBoundary else true

                    if (shouldHighlight) {
                        add(
                            HighlightMatch(
                                start = foundIndex,
                                end = endIndex,
                                word = word,
                                tag = tag,
                                color = color.toComposeColor()
                            )
                        )
                    }
                    searchIndex = endIndex
                }
            }
        }.sortedWith(compareBy({ it.start }, { -it.end }))
    }

    val selectedMatches = remember(allMatches) {
        buildList {
            var lastEnd = 0

            allMatches.forEach {
                if (it.start < lastEnd) return@forEach

                add(it)
                lastEnd = it.end
            }
        }
    }

    val annotatedString = remember(text, selectedMatches) {
        buildAnnotatedString {
            var currentIndex = 0

            selectedMatches.forEach {
                if (it.start > currentIndex) append(text.substring(currentIndex, it.start))

                withStyle(style = SpanStyle(background = it.color.copy(alpha = 0.3f))) {
                    append(it.word)
                }

                if (it.tag.isNotEmpty()) {
                    withStyle(
                        style = SpanStyle(
                            fontSize = 13.sp, background = it.color.copy(alpha = 0.8f), fontWeight = FontWeight.Bold
                        )
                    ) {
                        append(" ${it.tag} ")
                    }

                    append(" ")
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

    val greenColor = App.Colors.GREEN_HIGHLIGHT.toComposeColor(76)
    val redColor = App.Colors.RED_HIGHLIGHT.toComposeColor(76)

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
                            withStyle(style = SpanStyle(background = redColor)) {
                                append(" ".repeat(token.trim('~').length))
                            }
                            ""
                        }

                        token.startsWith("**") && token.endsWith("**") -> {
                            withStyle(style = SpanStyle(background = greenColor)) {
                                append(token.trim('*'))
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

@Composable
fun SimpleTextField(
    textValue: String,
    onTextValueChange: (String) -> Unit,
    placeholderText: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
) {
    TextField(
        value = textValue,
        onValueChange = onTextValueChange,
        modifier = modifier,
        placeholder = { Text(placeholderText) },
        colors = TextFieldDefaults.textFieldColors(
            backgroundColor = Color(0xFFF0F0F0),
            unfocusedIndicatorColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
        ),
        shape = RoundedCornerShape(8.dp),
        singleLine = singleLine
    )
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        backgroundColor = Color(0xFFF5F5F5),
        shape = RoundedCornerShape(8.dp),
        elevation = 0.dp
    ) {
        Text(
            text,
            style = MaterialTheme.typography.caption,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
fun SimplePopup(
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.TopEnd,
    offset: IntOffset = IntOffset(-16, 24),
    content: @Composable () -> Unit,
) {
    Popup(
        alignment = alignment,
        offset = offset,
        properties = PopupProperties(focusable = false)
    ) {
        Card(
            modifier = modifier,
            shape = RoundedCornerShape(4.dp),
            backgroundColor = MaterialTheme.colors.surface,
            elevation = 4.dp
        ) {
            content()
        }
    }
}

internal fun readBitmapResource(path: String): Painter = BitmapPainter(readResourceBytes(path).decodeToImageBitmap())

private object ResourceLoader

private fun readResourceBytes(resourcePath: String) =
    ResourceLoader::class.java.classLoader
        ?.getResourceAsStream(resourcePath)
        ?.readAllBytes()
        ?: ByteArray(0)