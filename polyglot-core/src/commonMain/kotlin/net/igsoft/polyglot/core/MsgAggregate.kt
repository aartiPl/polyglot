package net.igsoft.polyglot.core

abstract class MsgAggregate {
    protected lateinit var registry: PolyglotRegistry

    abstract fun register()

    fun addBundle(msgAggregate: MsgAggregate) {
        registry.registerBundle(msgAggregate)
    }

    internal fun register(registry: PolyglotRegistry) = synchronized(this) {
        this.registry = registry
        register()
    }
}
