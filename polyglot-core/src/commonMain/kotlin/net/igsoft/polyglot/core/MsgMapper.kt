package net.igsoft.polyglot.core

import kotlin.reflect.KClass

abstract class MsgMapper : MsgAggregate() {
    inline fun <reified S : Any, reified T : Any> add(
        noinline mapping: (S) -> T,
    ) {
        add(S::class, T::class, mapping)
    }

    fun <S : Any, T : Any> add(
        sourceClass: KClass<S>,
        targetClass: KClass<T>,
        mapping: (S) -> T,
    ) {
        registry.registerMapper(sourceClass, targetClass, mapping)
    }
}
