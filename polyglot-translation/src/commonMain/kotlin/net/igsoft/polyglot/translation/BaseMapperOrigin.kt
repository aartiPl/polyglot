package net.igsoft.polyglot.translation

import kotlin.reflect.KClass
import net.igsoft.polyglot.locale.Locale

data class BaseMapperOrigin(
    override val translationClass: KClass<*>,
    val resolvedLocale: Locale,
    val locale: Locale?,
    val sourceLocale: Locale?,
    override val registrationPath: String,
    val source: TranslationOrigin,
) : TranslationOrigin
