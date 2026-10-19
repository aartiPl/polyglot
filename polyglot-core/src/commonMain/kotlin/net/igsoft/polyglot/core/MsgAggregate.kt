package net.igsoft.polyglot.core

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

abstract class MsgAggregate {
    private val registrationLock = SynchronizedObject()

    private var activeRegistry: PolyglotRegistry? = null

    protected var registry: PolyglotRegistry
        get() = checkNotNull(activeRegistry) { "Registry is available only during register()" }
        set(value) {
            activeRegistry = value
        }

    abstract fun register()

    fun addBundle(msgAggregate: MsgAggregate) {
        registry.registerBundle(msgAggregate)
    }

    internal fun register(registry: PolyglotRegistry) = synchronized(registrationLock) {
        val previousRegistry = activeRegistry
        try {
            this.registry = registry
            register()
        } finally {
            activeRegistry = previousRegistry
        }
    }
}
