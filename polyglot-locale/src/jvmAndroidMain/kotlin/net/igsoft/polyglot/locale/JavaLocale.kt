package net.igsoft.polyglot.locale

import java.util.Locale as JavaLocale

fun Locale.Companion.of(locale: JavaLocale): Locale = Locale.of(locale.toLanguageTag())

fun JavaLocale.toLocale(): Locale = Locale.of(this)

fun Locale.toJavaLocale(): JavaLocale = JavaLocale.forLanguageTag(languageTag)
