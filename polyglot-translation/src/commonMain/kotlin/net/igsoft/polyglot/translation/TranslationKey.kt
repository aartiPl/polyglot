package net.igsoft.polyglot.translation

import kotlin.reflect.KClass
import net.igsoft.polyglot.locale.Locale

internal data class TranslationKey(
    val locale: Locale,
    val clazz: KClass<*>,
)
