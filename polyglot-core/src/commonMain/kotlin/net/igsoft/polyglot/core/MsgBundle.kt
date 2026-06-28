package net.igsoft.polyglot.core

import kotlin.reflect.KClass

abstract class MsgBundle : MsgAggregate() {
    abstract val locale: MsgLocale

    inline fun <reified T : Any> add(instance: T) {
        add(instance, T::class)
    }

    fun <T : Any> add(instance: T, clazz: KClass<T>) {
        registry.registerTranslationClass(locale, clazz) { instance }
    }
}
