package net.igsoft.polyglot.core

import kotlin.reflect.KClass

internal data class OverrideRegistration(
    val sourceClass: KClass<*>,
    val sourceLocale: MsgLocale?,
    val registration: (Any, Any) -> Any,
)
