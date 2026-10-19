package net.igsoft.polyglot.translation

import kotlin.reflect.KClass

class BundleScope internal constructor() {
    internal val instances = mutableMapOf<KClass<*>, Any>()

    inline fun <reified T : Any> add(instance: T) {
        add(instance, T::class)
    }

    fun <T : Any> add(instance: T, clazz: KClass<T>) {
        instances[clazz] = instance
    }
}
