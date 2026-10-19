package net.igsoft.polyglot.translation

import kotlin.reflect.KClass
import net.igsoft.polyglot.locale.Locale

/** Snapshot of the provenance of [TranslationResolution.value] at the time of its lookup. */
data class TranslationDiagnostics(
    val requestedClass: KClass<*>,
    val requestedLocale: Locale,
    val fallbackChain: List<Locale>,
    val cacheHit: Boolean,
    val usesEnglishFallback: Boolean,
    val origin: TranslationOrigin,
)
