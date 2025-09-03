package dev.paulee.ui

import androidx.compose.runtime.*
import java.text.MessageFormat
import java.util.*

internal data class I18n(private val resolver: (String, Array<out Any?>) -> String) {
    operator fun get(key: String, vararg args: Any?) = resolver(key, args)
}

internal val LocalI18n = staticCompositionLocalOf<I18n> { error("No I18n provided") }

@Composable
internal fun WithLangScope(
    locale: Locale,
    bundleName: String = "i18n/strings",
    content: @Composable () -> Unit
) {
    val current by remember(locale) { mutableStateOf(ResourceBundle.getBundle(bundleName, locale)) }

    val fallback by remember { mutableStateOf(ResourceBundle.getBundle(bundleName, Locale.ENGLISH)) }

    val provider = remember(locale, current) {
        I18n { key, args ->
            val pattern = runCatching { current.getString(key) }
                .recoverCatching { fallback.getString(key) }
                .getOrDefault(key)

            if (args.isEmpty()) pattern else MessageFormat(pattern, locale).format(args)
        }
    }

    CompositionLocalProvider(LocalI18n provides provider, content = content)
}