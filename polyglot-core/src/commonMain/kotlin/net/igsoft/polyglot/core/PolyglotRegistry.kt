package net.igsoft.polyglot.core

import kotlin.reflect.KClass

class PolyglotRegistry(msgAggregates: List<MsgAggregate> = emptyList()) {
    private val registrations = mutableMapOf<MsgLocale, MutableMap<KClass<*>, TranslationRegistration>>()
    private val mapperRegistrations = mutableMapOf<MsgLocale?, MutableMap<KClass<*>, MapperRegistration>>()
    private val overrideRegistrations = mutableMapOf<MsgLocale?, MutableMap<KClass<*>, OverrideRegistration>>()
    private val pendingBundles = mutableMapOf<MsgLocale, MutableList<MsgBundle>>()
    private val reloadingBundles = mutableSetOf<MsgBundle>()

    internal var revision: Long = 0
        private set

    init {
        msgAggregates.forEach { registerBundle(it) }
    }

    fun registerBundle(msgAggregate: MsgAggregate) {
        if (msgAggregate is MsgBundle) {
            pendingBundles.getOrPut(msgAggregate.locale) { mutableListOf() }.add(msgAggregate)
            revision += 1
            return
        }

        msgAggregate.register(this)
    }

    fun <T : Any> registerTranslationClass(locale: MsgLocale, clazz: KClass<T>, registration: () -> T) {
        val registrationsForLocale = registrations.getOrPut(locale) {
            mutableMapOf()
        }

        registrationsForLocale[clazz] = DirectRegistration(registration)
        revision += 1
    }

    internal fun <T : Any> registerBundleTranslation(bundle: MsgBundle, clazz: KClass<T>, instance: T) {
        val registrationsForLocale = registrations.getOrPut(bundle.locale) { mutableMapOf() }
        if (bundle in reloadingBundles) {
            val registration = registrationsForLocale[clazz] as? BundleRegistration
            if (registration?.bundle === bundle) {
                registration.instance = instance
            }
            return
        }

        registrationsForLocale[clazz] = BundleRegistration(bundle, instance)
        revision += 1
    }

    internal fun releaseBundleTranslations() {
        registrations.values.forEach { registrationsForLocale ->
            registrationsForLocale.values.forEach { registration ->
                if (registration is BundleRegistration) {
                    registration.instance = null
                }
            }
        }
    }

    private fun resolveRegistration(registration: TranslationRegistration): Any = when (registration) {
        is DirectRegistration -> registration.registration()
        is BundleRegistration -> {
            if (registration.instance == null) {
                val bundle = registration.bundle
                check(reloadingBundles.add(bundle)) { "Cyclic bundle registration [locale='${bundle.locale.languageTag}']" }
                try {
                    bundle.register(this)
                } finally {
                    reloadingBundles.remove(bundle)
                }
            }
            checkNotNull(registration.instance) {
                "Bundle must recreate its registered translation types [locale='${registration.bundle.locale.languageTag}']"
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <S : Any, T : Any> registerMapper(
        sourceClass: KClass<S>,
        targetClass: KClass<T>,
        locale: MsgLocale? = null,
        sourceLocale: MsgLocale? = null,
        registration: (S) -> T,
    ) {
        val registrationsForLocale = mapperRegistrations.getOrPut(locale) { mutableMapOf() }
        check(targetClass !in registrationsForLocale) {
            "Duplicate base mapper [translationClass='${targetClass.simpleName}', locale='${locale?.languageTag ?: "global"}']"
        }
        registrationsForLocale[targetClass] = MapperRegistration(
            sourceClass = sourceClass,
            sourceLocale = sourceLocale,
            registration = { source -> registration(source as S) },
        )
        revision += 1
    }

    @Suppress("UNCHECKED_CAST")
    fun <S : Any, T : Any> registerMapper(
        sourceClass: KClass<S>,
        targetClass: KClass<T>,
        locale: MsgLocale? = null,
        sourceLocale: MsgLocale? = null,
        registration: (S, T) -> T,
    ) {
        val registrationsForLocale = overrideRegistrations.getOrPut(locale) { mutableMapOf() }
        check(targetClass !in registrationsForLocale) {
            "Duplicate override mapper [translationClass='${targetClass.simpleName}', locale='${locale?.languageTag ?: "global"}']"
        }
        registrationsForLocale[targetClass] = OverrideRegistration(
            sourceClass = sourceClass,
            sourceLocale = sourceLocale,
            registration = { source, target -> registration(source as S, target as T) },
        )
        revision += 1
    }

    @Suppress("UNCHECKED_CAST")
    internal fun <T : Any> getTranslationClass(clazz: KClass<T>, locales: List<MsgLocale>): T {
        val fallbackLocales = locales.flatMap(::fallbackLocales).distinct()
        return resolveTranslationClassOrNull(
            clazz = clazz,
            locales = fallbackLocales,
            resolutionStack = emptyList(),
        ) as? T
            ?: error(
                "Can not find translations for locales: [${fallbackLocales.joinToString(", ") { it.languageTag }}] " +
                        "[translationClass='${clazz.simpleName}']"
            )
    }

    private fun resolveTranslationClassOrNull(
        clazz: KClass<*>,
        locales: List<MsgLocale>,
        resolutionStack: List<ResolutionKey>,
    ): Any? {
        var translation = resolveBaseTranslationClassOrNull(clazz, locales, resolutionStack) ?: return null
        val resolutionKey = ResolutionKey(clazz, locales.first(), ResolutionPhase.FINAL)
        checkResolutionCycle(resolutionKey, resolutionStack)
        val nextStack = resolutionStack + resolutionKey

        overrideRegistrations[null]?.get(clazz)?.let { overrideRegistration ->
            translation = resolveOverrideOrNull(overrideRegistration, translation, locales, nextStack) ?: translation
        }
        locales.forEach { locale ->
            val overrideRegistration = overrideRegistrations[locale]?.get(clazz) ?: return@forEach
            resolveOverrideOrNull(overrideRegistration, translation, locales, nextStack)?.let { return it }
        }

        return translation
    }

    private fun resolveOverrideOrNull(
        overrideRegistration: OverrideRegistration,
        translation: Any,
        locales: List<MsgLocale>,
        resolutionStack: List<ResolutionKey>,
    ): Any? {
        val sourceLocales = overrideRegistration.sourceLocale?.let(::listOf) ?: locales
        val source = resolveTranslationClassOrNull(
            overrideRegistration.sourceClass,
            sourceLocales,
            resolutionStack,
        ) ?: return null

        return overrideRegistration.registration(source, translation)
    }

    private fun resolveBaseTranslationClassOrNull(
        clazz: KClass<*>,
        locales: List<MsgLocale>,
        resolutionStack: List<ResolutionKey>,
    ): Any? {
        ensureLocalesRegistered(locales)
        locales.forEach { locale ->
            resolveMappedTranslationClassOrNull(
                clazz = clazz,
                locale = locale,
                resolutionStack = resolutionStack,
            )?.let { translation ->
                return translation
            }

            registrations[locale]?.get(clazz)?.let { registration ->
                return resolveRegistration(registration)
            }
        }

        return null
    }

    private fun resolveMappedTranslationClassOrNull(
        clazz: KClass<*>,
        locale: MsgLocale,
        resolutionStack: List<ResolutionKey>,
    ): Any? {
        val candidates = listOfNotNull(
            mapperRegistrations[locale]?.get(clazz),
            mapperRegistrations[null]?.get(clazz),
        )
        if (candidates.isEmpty()) {
            return null
        }
        val resolutionKey = ResolutionKey(clazz, locale, ResolutionPhase.BASE)
        checkResolutionCycle(resolutionKey, resolutionStack)
        candidates.forEach { mapperRegistration ->
            val sourceLocale = mapperRegistration.sourceLocale ?: locale
            val sourceTranslation = resolveBaseTranslationClassOrNull(
                clazz = mapperRegistration.sourceClass,
                locales = listOf(sourceLocale),
                resolutionStack = resolutionStack + resolutionKey,
            ) ?: return@forEach

            return mapperRegistration.registration(sourceTranslation)
        }

        return null
    }

    private fun checkResolutionCycle(key: ResolutionKey, stack: List<ResolutionKey>) {
        check(key !in stack) {
            "Cyclic translation dependency: " + (stack + key).joinToString(" -> ") {
                "${it.clazz.simpleName}[${it.locale.languageTag}, ${it.phase}]"
            }
        }
    }

    fun registeredLocales(): Set<MsgLocale> = buildSet {
        addAll(pendingBundles.keys)
        addAll(registrations.keys)
        addAll(mapperRegistrations.keys.filterNotNull())
        addAll(overrideRegistrations.keys.filterNotNull())
    }

    private fun ensureLocalesRegistered(locales: List<MsgLocale>) {
        locales.forEach(::ensureLocaleRegistered)
    }

    private fun ensureLocaleRegistered(locale: MsgLocale) {
        pendingBundles.remove(locale)
            ?.forEach { bundle -> bundle.register(this) }
    }

    private fun fallbackLocales(locale: MsgLocale): List<MsgLocale> {
        val parts = locale.languageTag.split('-')

        return (parts.size downTo 1).map { index ->
            MsgLocale.of(parts.take(index).joinToString("-"))
        }
    }

    private sealed interface TranslationRegistration

    private class DirectRegistration(val registration: () -> Any) : TranslationRegistration

    private class BundleRegistration(val bundle: MsgBundle, var instance: Any?) : TranslationRegistration

    private data class MapperRegistration(
        val sourceClass: KClass<*>,
        val sourceLocale: MsgLocale?,
        val registration: (Any) -> Any,
    )

    private data class ResolutionKey(
        val clazz: KClass<*>,
        val locale: MsgLocale,
        val phase: ResolutionPhase,
    )

    private data class OverrideRegistration(
        val sourceClass: KClass<*>,
        val sourceLocale: MsgLocale?,
        val registration: (Any, Any) -> Any,
    )

    private enum class ResolutionPhase {
        BASE,
        FINAL,
    }
}
