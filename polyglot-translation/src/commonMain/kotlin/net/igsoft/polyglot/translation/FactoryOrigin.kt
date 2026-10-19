package net.igsoft.polyglot.translation

import kotlin.reflect.KClass
import net.igsoft.polyglot.locale.Locale

data class FactoryOrigin(
    override val translationClass: KClass<*>,
    val locale: Locale,
    override val registrationPath: String,
) : TranslationOrigin
