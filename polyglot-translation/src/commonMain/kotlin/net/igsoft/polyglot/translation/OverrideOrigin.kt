package net.igsoft.polyglot.translation

import kotlin.reflect.KClass
import net.igsoft.polyglot.locale.Locale

data class OverrideOrigin(
    override val translationClass: KClass<*>,
    val locale: Locale?,
    val sourceLocale: Locale?,
    override val registrationPath: String,
    val target: TranslationOrigin,
    val source: TranslationOrigin,
) : TranslationOrigin
