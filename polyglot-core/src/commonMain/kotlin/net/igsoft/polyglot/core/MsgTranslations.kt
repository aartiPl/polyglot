package net.igsoft.polyglot.core

import kotlin.reflect.KClass

class MsgTranslations internal constructor() {
    internal val instances = mutableMapOf<KClass<*>, Any>()

    inline fun <reified T : Any> add(instance: T) {
        add(instance, T::class)
    }

    fun <T : Any> add(instance: T, clazz: KClass<T>) {
        instances[clazz] = instance
    }
}
