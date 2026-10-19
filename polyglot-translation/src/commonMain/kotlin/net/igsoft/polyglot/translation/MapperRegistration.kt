package net.igsoft.polyglot.translation

import kotlin.reflect.KClass
import net.igsoft.polyglot.locale.Locale

internal data class MapperRegistration(
    val sourceClass: KClass<*>,
    val sourceLocale: Locale?,
    val registration: (Any) -> Any,
)
