package net.igsoft.polyglot.adapter

import java.util.Locale
import net.igsoft.polyglot.core.MsgLocale

fun MsgLocale.Companion.of(locale: Locale): MsgLocale = MsgLocale.of(locale.toLanguageTag())

fun Locale.toMsgLocale(): MsgLocale = MsgLocale.of(this)

fun MsgLocale.toJavaLocale(): Locale = Locale.forLanguageTag(languageTag)
