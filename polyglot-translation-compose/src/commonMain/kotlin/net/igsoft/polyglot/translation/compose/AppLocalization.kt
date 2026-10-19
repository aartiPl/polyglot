package net.igsoft.polyglot.translation.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import net.igsoft.polyglot.locale.Locale
import net.igsoft.polyglot.locale.Locales
import net.igsoft.polyglot.translation.Resolver

val LocalLocale: ProvidableCompositionLocal<Locale> =
    staticCompositionLocalOf { Locales.EN }

val LocalSetLocale: ProvidableCompositionLocal<(Locale) -> Unit> =
    staticCompositionLocalOf {
        error("LocalSetLocale not provided. Wrap your UI in AppLocalizationProvider.")
    }

val LocalPolyglotResolver: ProvidableCompositionLocal<Resolver> =
    staticCompositionLocalOf {
        error("LocalPolyglotResolver not provided. Wrap your UI in AppLocalizationProvider.")
    }

private fun ComposeLocale.toLocale(): Locale = Locale.of(toLanguageTag())

@Composable
fun AppLocalizationProvider(
    polyglotResolver: Resolver,
    initialLocale: Locale? = null,
    content: @Composable () -> Unit,
) {
    var currentLocale by remember { mutableStateOf(initialLocale ?: ComposeLocale.current.toLocale()) }

    polyglotResolver.setCurrentLocale(currentLocale)

    CompositionLocalProvider(
        LocalLocale provides currentLocale,
        LocalSetLocale provides { newLocale -> currentLocale = newLocale },
        LocalPolyglotResolver provides polyglotResolver,
    ) {
        content()
    }
}
