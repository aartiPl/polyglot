package net.igsoft.polyglot.core

abstract class MsgBundle : MsgDefinition {
    abstract val locale: MsgLocale

    abstract fun register(translations: MsgTranslations)
}
