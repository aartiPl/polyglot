package net.igsoft.polyglot.translation

import kotlin.reflect.KClass
import net.igsoft.polyglot.locale.Locale

internal data class OverrideRegistration(
    val sourceClass: KClass<*>,
    val sourceLocale: Locale?,
    val registration: (Any, Any) -> Any,
)
