package net.igsoft.polyglot.translation

abstract class Module : Definition {
    abstract fun register(registration: ModuleScope)
}
