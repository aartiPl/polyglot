package net.igsoft.polyglot.translation

import kotlin.reflect.KClass
import net.igsoft.polyglot.locale.Locale
import net.igsoft.polyglot.locale.Locales

class Resolver(private val registry: Registry) {
    private val translationMap = mutableMapOf<TranslationKey, Any>()
    private val defaultLocale: Locale = Locales.EN
    private var cachedRevision: Long = registry.revision

    private var currentLocale: Locale = defaultLocale

    inline fun <reified T : Any> getTranslationClass(): T = getTranslationClass(T::class)

    inline fun <reified T : Any> getTranslationClass(locale: Locale): T = getTranslationClass(T::class, locale)

    fun <T : Any> getTranslationClass(clazz: KClass<T>): T {
        return getTranslationClass(clazz, currentLocale)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getTranslationClass(clazz: KClass<T>, locale: Locale): T {
        invalidateCacheIfRegistryChanged()
        val key = TranslationKey(locale = locale, clazz = clazz)
        translationMap[key]?.let { return it as T }

        val translation = registry.getTranslationClass(clazz, listOf(locale, defaultLocale))
        invalidateCacheIfRegistryChanged()
        translationMap[key] = translation
        return translation
    }

    private fun invalidateCacheIfRegistryChanged() {
        if (cachedRevision != registry.revision) {
            translationMap.clear()
            cachedRevision = registry.revision
        }
    }

    fun setCurrentLocale(locale: Locale) {
        if (currentLocale == locale) {
            return
        }
        currentLocale = locale
        translationMap.clear()
        registry.releaseBundleTranslations()
    }

    fun registeredLocales(): Set<Locale> = registry.registeredLocales()

    private data class TranslationKey(
        val locale: Locale,
        val clazz: KClass<*>,
    )
}
