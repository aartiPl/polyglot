package net.igsoft.polyglot.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import net.igsoft.polyglot.core.MsgLocale
import net.igsoft.polyglot.core.MsgLocales
import net.igsoft.polyglot.core.PolyglotResolver

val LocalLocale: ProvidableCompositionLocal<MsgLocale> =
    staticCompositionLocalOf { MsgLocales.EN }

val LocalSetLocale: ProvidableCompositionLocal<(MsgLocale) -> Unit> =
    staticCompositionLocalOf {
        error("LocalSetLocale not provided. Wrap your UI in AppLocalizationProvider.")
    }

val LocalPolyglotResolver: ProvidableCompositionLocal<PolyglotResolver> =
    staticCompositionLocalOf {
        error("LocalPolyglotResolver not provided. Wrap your UI in AppLocalizationProvider.")
    }

private fun ComposeLocale.toMsgLocale(): MsgLocale = MsgLocale.of(toLanguageTag())

@Composable
fun AppLocalizationProvider(
    polyglotResolver: PolyglotResolver,
    initialLocale: MsgLocale? = null,
    content: @Composable () -> Unit,
) {
    var currentLocale by remember { mutableStateOf(initialLocale ?: ComposeLocale.current.toMsgLocale()) }

    polyglotResolver.setCurrentLocale(currentLocale)

    CompositionLocalProvider(
        LocalLocale provides currentLocale,
        LocalSetLocale provides { newLocale -> currentLocale = newLocale },
        LocalPolyglotResolver provides polyglotResolver,
    ) {
        content()
    }
}

