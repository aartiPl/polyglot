package net.igsoft.polyglot.core

import kotlin.jvm.JvmName
import kotlin.reflect.KClass

abstract class MsgMapper : MsgAggregate() {
    @JvmName("addSameType")
    inline fun <reified T : Any> add(
        locale: MsgLocale? = null,
        sourceLocale: MsgLocale? = null,
        noinline mapping: (T) -> T,
    ) {
        add(T::class, T::class, locale, sourceLocale, mapping)
    }

    inline fun <reified S : Any, reified T : Any> add(
        locale: MsgLocale? = null,
        sourceLocale: MsgLocale? = null,
        noinline mapping: (S) -> T,
    ) {
        add(S::class, T::class, locale, sourceLocale, mapping)
    }

    fun <S : Any, T : Any> add(
        sourceClass: KClass<S>,
        targetClass: KClass<T>,
        locale: MsgLocale? = null,
        sourceLocale: MsgLocale? = null,
        mapping: (S) -> T,
    ) {
        registry.registerMapper(sourceClass, targetClass, locale, sourceLocale, mapping)
    }

    inline fun <reified S : Any, reified T : Any> add(
        locale: MsgLocale? = null,
        sourceLocale: MsgLocale? = null,
        noinline mapping: (S, T) -> T,
    ) {
        add(S::class, T::class, locale, sourceLocale, mapping)
    }

    fun <S : Any, T : Any> add(
        sourceClass: KClass<S>,
        targetClass: KClass<T>,
        locale: MsgLocale? = null,
        sourceLocale: MsgLocale? = null,
        mapping: (S, T) -> T,
    ) {
        registry.registerMapper(sourceClass, targetClass, locale, sourceLocale, mapping)
    }
}
