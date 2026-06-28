package net.igsoft.polyglot.core

import java.util.Locale

fun MsgLocale.Companion.of(locale: Locale): MsgLocale = MsgLocale.of(locale.toLanguageTag())

fun Locale.toMsgLocale(): MsgLocale = MsgLocale.of(this)

fun MsgLocale.toJavaLocale(): Locale = Locale.forLanguageTag(languageTag)
