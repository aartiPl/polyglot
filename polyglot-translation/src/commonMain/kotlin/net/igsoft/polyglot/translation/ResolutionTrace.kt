package net.igsoft.polyglot.translation

import net.igsoft.polyglot.locale.Locale

/** Per-lookup state; only the diagnostic resolver allocates it. Child traces never retain models. */
internal class ResolutionTrace(val defaultFallback: Locale?) {
    var fallbackChain: List<Locale> = emptyList()
    var origin: TranslationOrigin? = null
    var usesEnglishFallback: Boolean = false
}
