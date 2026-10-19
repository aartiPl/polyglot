package net.igsoft.polyglot.translation

data class TranslationResolution<T : Any>(
    val value: T,
    val diagnostics: TranslationDiagnostics,
)
