package net.igsoft.polyglot.core

import kotlin.reflect.KClass

class PolyglotResolver(private val registry: PolyglotRegistry) {
    private val translationMap = mutableMapOf<TranslationKey, Any>()
    private val defaultLocale: MsgLocale = MsgLocales.EN

    private var currentLocale: MsgLocale = defaultLocale

    fun <T : Any> getTranslationClass(clazz: KClass<T>): T {
        return getTranslationClass(clazz, currentLocale)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getTranslationClass(clazz: KClass<T>, locale: MsgLocale): T {
        val key = TranslationKey(locale = locale, clazz = clazz)
        return translationMap.getOrPut(key) {
            registry.getTranslationClass(clazz, listOf(locale, defaultLocale))
        } as T
    }

    fun setCurrentLocale(locale: MsgLocale) {
        currentLocale = locale
        translationMap.clear()
    }

    fun registeredLocales(): Set<MsgLocale> = registry.registeredLocales()

    private data class TranslationKey(
        val locale: MsgLocale,
        val clazz: KClass<*>,
    )
}

