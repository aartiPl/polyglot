package net.igsoft.polyglot.core

import kotlin.reflect.KClass

internal data class MapperRegistration(
    val sourceClass: KClass<*>,
    val sourceLocale: MsgLocale?,
    val registration: (Any) -> Any,
)
