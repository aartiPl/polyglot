package net.igsoft.polyglot.translation

import kotlin.reflect.KClass
import net.igsoft.polyglot.locale.Locale
import net.igsoft.polyglot.locale.Locales

class Resolver internal constructor(
    private val registry: Registry,
    private val diagnosticCache: MutableMap<TranslationKey, ResolutionTrace>?,
) {
    constructor(registry: Registry) : this(registry, null)

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

        val trace = diagnosticCache?.let {
            ResolutionTrace(defaultLocale.takeUnless { locale.languageTag.substringBefore('-') == it.languageTag })
        }
        val translation = registry.getTranslationClass(clazz, listOf(locale, defaultLocale), trace)
        invalidateCacheIfRegistryChanged()
        translationMap[key] = translation
        if (trace != null) {
            diagnosticCache[key] = trace
        }
        return translation
    }

    internal fun <T : Any> resolveWithDiagnostics(
        clazz: KClass<T>,
        locale: Locale = currentLocale,
    ): TranslationResolution<T> {
        invalidateCacheIfRegistryChanged()
        val key = TranslationKey(locale, clazz)
        val cacheHit = key in translationMap
        val value = getTranslationClass(clazz, locale)
        val trace = checkNotNull(diagnosticCache?.get(key))
        return TranslationResolution(
            value,
            TranslationDiagnostics(
                requestedClass = clazz,
                requestedLocale = locale,
                fallbackChain = trace.fallbackChain,
                cacheHit = cacheHit,
                usesEnglishFallback = trace.usesEnglishFallback,
                origin = checkNotNull(trace.origin),
            ),
        )
    }

    private fun invalidateCacheIfRegistryChanged() {
        if (cachedRevision != registry.revision) {
            translationMap.clear()
            diagnosticCache?.clear()
            cachedRevision = registry.revision
        }
    }

    fun setCurrentLocale(locale: Locale) {
        if (currentLocale == locale) {
            return
        }
        currentLocale = locale
        translationMap.clear()
        diagnosticCache?.clear()
        registry.releaseBundleTranslations()
    }

    fun registeredLocales(): Set<Locale> = registry.registeredLocales()
}
