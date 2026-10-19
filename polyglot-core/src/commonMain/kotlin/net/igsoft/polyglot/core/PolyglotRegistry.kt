package net.igsoft.polyglot.core

import kotlin.jvm.JvmName
import kotlin.reflect.KClass

class PolyglotRegistry(definitions: List<MsgDefinition> = emptyList()) {
    private val registrations = mutableMapOf<MsgLocale, MutableMap<KClass<*>, TranslationRegistration>>()
    private val mapperRegistrations = mutableMapOf<MsgLocale?, MutableMap<KClass<*>, MapperRegistration>>()
    private val overrideRegistrations = mutableMapOf<MsgLocale?, MutableMap<KClass<*>, OverrideRegistration>>()
    private val pendingBundles = mutableMapOf<MsgLocale, ArrayDeque<MsgBundle>>()

    internal var revision: Long = 0
        private set

    init {
        definitions.forEach { registerBundle(it) }
    }

    fun registerBundle(definition: MsgDefinition) {
        when (definition) {
            is MsgBundle -> {
                pendingBundles.getOrPut(definition.locale) { ArrayDeque() }.addLast(definition)
                revision += 1
            }
            is MsgAggregate -> {
                val registration = MsgRegistration()
                definition.register(registration)
                applyRegistration(registration)
            }
        }
    }

    private fun applyRegistration(registration: MsgRegistration) {
        registration.validateAgainst(mapperRegistrations, overrideRegistrations)
        registration.bundles.forEach { bundle ->
            pendingBundles.getOrPut(bundle.locale) { ArrayDeque() }.addLast(bundle)
            revision += 1
        }
        registration.mappers.forEach { (locale, entries) ->
            mapperRegistrations.getOrPut(locale) { mutableMapOf() }.putAll(entries)
            revision += entries.size
        }
        registration.overrides.forEach { (locale, entries) ->
            overrideRegistrations.getOrPut(locale) { mutableMapOf() }.putAll(entries)
            revision += entries.size
        }
    }

    inline fun <reified T : Any> registerTranslationClass(locale: MsgLocale, noinline registration: () -> T) {
        registerTranslationClass(locale, T::class, registration)
    }

    fun <T : Any> registerTranslationClass(locale: MsgLocale, clazz: KClass<T>, registration: () -> T) {
        val registrationsForLocale = registrations.getOrPut(locale) {
            mutableMapOf()
        }

        registrationsForLocale[clazz] = DirectRegistration(registration)
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
                restoreBundle(registration.bundle)
            }
            checkNotNull(registration.instance) {
                "Bundle must recreate its registered translation types [locale='${registration.bundle.locale.languageTag}']"
            }
        }
    }

    private fun collectTranslations(bundle: MsgBundle): Map<KClass<*>, Any> {
        val translations = MsgTranslations()
        bundle.register(translations)
        return translations.instances
    }

    private fun restoreBundle(bundle: MsgBundle) {
        val instances = collectTranslations(bundle)
        val registrationsForLocale = registrations.getValue(bundle.locale)
        check(registrationsForLocale.all { (clazz, registration) ->
            registration !is BundleRegistration || registration.bundle !== bundle || clazz in instances
        }) {
            "Bundle must recreate its registered translation types [locale='${bundle.locale.languageTag}']"
        }
        instances.forEach { (clazz, instance) ->
            val registration = registrationsForLocale[clazz] as? BundleRegistration
            if (registration?.bundle === bundle) {
                registration.instance = instance
            }
        }
    }

    @JvmName("registerMapperSameType")
    inline fun <reified T : Any> registerMapper(
        locale: MsgLocale? = null,
        sourceLocale: MsgLocale? = null,
        noinline registration: (T) -> T,
    ) {
        registerMapper(T::class, T::class, locale, sourceLocale, registration)
    }

    inline fun <reified S : Any, reified T : Any> registerMapper(
        locale: MsgLocale? = null,
        sourceLocale: MsgLocale? = null,
        noinline registration: (S) -> T,
    ) {
        registerMapper(S::class, T::class, locale, sourceLocale, registration)
    }

    fun <S : Any, T : Any> registerMapper(
        sourceClass: KClass<S>,
        targetClass: KClass<T>,
        locale: MsgLocale? = null,
        sourceLocale: MsgLocale? = null,
        registration: (S) -> T,
    ) {
        val declarations = MsgRegistration()
        declarations.registerMapper(sourceClass, targetClass, locale, sourceLocale, registration)
        applyRegistration(declarations)
    }

    inline fun <reified S : Any, reified T : Any> registerMapper(
        locale: MsgLocale? = null,
        sourceLocale: MsgLocale? = null,
        noinline registration: (S, T) -> T,
    ) {
        registerMapper(S::class, T::class, locale, sourceLocale, registration)
    }

    fun <S : Any, T : Any> registerMapper(
        sourceClass: KClass<S>,
        targetClass: KClass<T>,
        locale: MsgLocale? = null,
        sourceLocale: MsgLocale? = null,
        registration: (S, T) -> T,
    ) {
        val declarations = MsgRegistration()
        declarations.registerMapper(sourceClass, targetClass, locale, sourceLocale, registration)
        applyRegistration(declarations)
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
        val pending = pendingBundles[locale] ?: return
        while (pending.isNotEmpty()) {
            val bundle = pending.first()
            val instances = collectTranslations(bundle)
            val registrationsForLocale = registrations.getOrPut(locale) { mutableMapOf() }
            instances.forEach { (clazz, instance) ->
                registrationsForLocale[clazz] = BundleRegistration(bundle, instance)
                revision += 1
            }
            pending.removeFirst()
        }
        pendingBundles.remove(locale)
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

    private data class ResolutionKey(
        val clazz: KClass<*>,
        val locale: MsgLocale,
        val phase: ResolutionPhase,
    )

    private enum class ResolutionPhase {
        BASE,
        FINAL,
    }
}
