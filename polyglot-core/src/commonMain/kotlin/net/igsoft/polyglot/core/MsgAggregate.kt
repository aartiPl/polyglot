package net.igsoft.polyglot.core

abstract class MsgAggregate : MsgDefinition {
    abstract fun register(registration: MsgRegistration)
}
