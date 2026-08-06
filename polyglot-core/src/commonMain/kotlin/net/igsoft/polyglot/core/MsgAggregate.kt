package net.igsoft.polyglot.core

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

abstract class MsgAggregate {
    private val registrationLock = SynchronizedObject()

    protected lateinit var registry: PolyglotRegistry

    abstract fun register()

    fun addBundle(msgAggregate: MsgAggregate) {
        registry.registerBundle(msgAggregate)
    }

    internal fun register(registry: PolyglotRegistry) = synchronized(registrationLock) {
        this.registry = registry
        register()
    }
}
