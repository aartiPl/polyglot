package net.igsoft.polyglot.translation

import kotlin.jvm.JvmName
import kotlin.reflect.KClass
import net.igsoft.polyglot.locale.Locale

class Registry(definitions: List<Definition> = emptyList()) {
    private val registrations = mutableMapOf<Locale, MutableMap<KClass<*>, TranslationRegistration>>()
    private val mapperRegistrations = mutableMapOf<Locale?, MutableMap<KClass<*>, MapperRegistration>>()
    private val overrideRegistrations = mutableMapOf<Locale?, MutableMap<KClass<*>, OverrideRegistration>>()
    private val pendingBundles = mutableMapOf<Locale, ArrayDeque<BundleDeclaration>>()
    private var declarationSequence = 0

    internal var revision: Long = 0
        private set

    init {
        definitions.forEach { registerDefinition(it) }
    }

    fun registerDefinition(definition: Definition) {
        val path = nextPath(definition::class.simpleName ?: if (definition is Bundle) "Bundle" else "Module")
        when (definition) {
            is Bundle -> {
                pendingBundles.getOrPut(definition.locale) { ArrayDeque() }.addLast(BundleDeclaration(definition, path))
                revision += 1
            }
            is Module -> {
                val registration = ModuleScope(path)
                definition.register(registration)
                applyRegistration(registration)
            }
        }
    }

    private fun applyRegistration(registration: ModuleScope) {
        registration.validateAgainst(mapperRegistrations, overrideRegistrations)
        registration.bundles.forEach { declaration ->
            pendingBundles.getOrPut(declaration.bundle.locale) { ArrayDeque() }.addLast(declaration)
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

    inline fun <reified T : Any> registerTranslationClass(locale: Locale, noinline registration: () -> T) {
        registerTranslationClass(locale, T::class, registration)
    }

    fun <T : Any> registerTranslationClass(locale: Locale, clazz: KClass<T>, registration: () -> T) {
        val registrationsForLocale = registrations.getOrPut(locale) {
            mutableMapOf()
        }

        registrationsForLocale[clazz] = DirectRegistration(registration, nextPath("factory"))
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

    private fun collectTranslations(bundle: Bundle): Map<KClass<*>, Any> {
        val translations = BundleScope()
        bundle.register(translations)
        return translations.instances
    }

    private fun restoreBundle(bundle: Bundle) {
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
        locale: Locale? = null,
        sourceLocale: Locale? = null,
        noinline registration: (T) -> T,
    ) {
        registerMapper(T::class, T::class, locale, sourceLocale, registration)
    }

    inline fun <reified S : Any, reified T : Any> registerMapper(
        locale: Locale? = null,
        sourceLocale: Locale? = null,
        noinline registration: (S) -> T,
    ) {
        registerMapper(S::class, T::class, locale, sourceLocale, registration)
    }

    fun <S : Any, T : Any> registerMapper(
        sourceClass: KClass<S>,
        targetClass: KClass<T>,
        locale: Locale? = null,
        sourceLocale: Locale? = null,
        registration: (S) -> T,
    ) {
        val declarations = ModuleScope(nextPath("mapper"))
        declarations.registerMapper(sourceClass, targetClass, locale, sourceLocale, registration)
        applyRegistration(declarations)
    }

    inline fun <reified S : Any, reified T : Any> registerMapper(
        locale: Locale? = null,
        sourceLocale: Locale? = null,
        noinline registration: (S, T) -> T,
    ) {
        registerMapper(S::class, T::class, locale, sourceLocale, registration)
    }

    fun <S : Any, T : Any> registerMapper(
        sourceClass: KClass<S>,
        targetClass: KClass<T>,
        locale: Locale? = null,
        sourceLocale: Locale? = null,
        registration: (S, T) -> T,
    ) {
        val declarations = ModuleScope(nextPath("mapper"))
        declarations.registerMapper(sourceClass, targetClass, locale, sourceLocale, registration)
        applyRegistration(declarations)
    }

    @Suppress("UNCHECKED_CAST")
    internal fun <T : Any> getTranslationClass(
        clazz: KClass<T>,
        locales: List<Locale>,
        trace: ResolutionTrace? = null,
    ): T {
        val fallbackLocales = locales.flatMap(::fallbackLocales).distinct()
        trace?.fallbackChain = fallbackLocales
        return resolveTranslationClassOrNull(
            clazz = clazz,
            locales = fallbackLocales,
            resolutionStack = emptyList(),
            trace = trace,
        ) as? T
            ?: error(
                "Can not find translations for locales: [${fallbackLocales.joinToString(", ") { it.languageTag }}] " +
                        "[translationClass='${clazz.simpleName}']"
            )
    }

    private fun resolveTranslationClassOrNull(
        clazz: KClass<*>,
        locales: List<Locale>,
        resolutionStack: List<ResolutionKey>,
        trace: ResolutionTrace?,
    ): Any? {
        var translation = resolveBaseTranslationClassOrNull(clazz, locales, resolutionStack, trace) ?: return null
        val resolutionKey = ResolutionKey(clazz, locales.first(), ResolutionPhase.FINAL)
        checkResolutionCycle(resolutionKey, resolutionStack)
        val nextStack = resolutionStack + resolutionKey

        overrideRegistrations[null]?.get(clazz)?.let { overrideRegistration ->
            translation = resolveOverrideOrNull(clazz, overrideRegistration, translation, locales, nextStack, trace) ?: translation
        }
        locales.forEach { locale ->
            val overrideRegistration = overrideRegistrations[locale]?.get(clazz) ?: return@forEach
            resolveOverrideOrNull(clazz, overrideRegistration, translation, locales, nextStack, trace)?.let { return it }
        }

        return translation
    }

    private fun resolveOverrideOrNull(
        clazz: KClass<*>,
        overrideRegistration: OverrideRegistration,
        translation: Any,
        locales: List<Locale>,
        resolutionStack: List<ResolutionKey>,
        trace: ResolutionTrace?,
    ): Any? {
        val sourceLocales = overrideRegistration.sourceLocale?.let(::listOf) ?: locales
        val sourceTrace = trace?.let {
            ResolutionTrace(if (overrideRegistration.sourceLocale == null) it.defaultFallback else null)
        }
        val source = resolveTranslationClassOrNull(
            overrideRegistration.sourceClass,
            sourceLocales,
            resolutionStack,
            sourceTrace,
        ) ?: return null

        val result = overrideRegistration.registration(source, translation)
        if (trace != null) {
            trace.origin = OverrideOrigin(
                translationClass = clazz,
                locale = overrideRegistration.locale,
                sourceLocale = overrideRegistration.sourceLocale,
                registrationPath = overrideRegistration.registrationPath,
                target = checkNotNull(trace.origin),
                source = checkNotNull(sourceTrace?.origin),
            )
            trace.usesEnglishFallback = trace.usesEnglishFallback || sourceTrace.usesEnglishFallback
        }
        return result
    }

    private fun resolveBaseTranslationClassOrNull(
        clazz: KClass<*>,
        locales: List<Locale>,
        resolutionStack: List<ResolutionKey>,
        trace: ResolutionTrace?,
    ): Any? {
        ensureLocalesRegistered(locales)
        locales.forEach { locale ->
            resolveMappedTranslationClassOrNull(
                clazz = clazz,
                locale = locale,
                resolutionStack = resolutionStack,
                trace = trace,
            )?.let { translation ->
                if (trace != null && locale == trace.defaultFallback) {
                    trace.usesEnglishFallback = true
                }
                return translation
            }

            registrations[locale]?.get(clazz)?.let { registration ->
                val translation = resolveRegistration(registration)
                if (trace != null) {
                    trace.origin = when (registration) {
                        is BundleRegistration -> BundleOrigin(clazz, locale, registration.bundle::class, registration.registrationPath)
                        is DirectRegistration -> FactoryOrigin(clazz, locale, registration.registrationPath)
                    }
                    trace.usesEnglishFallback = locale == trace.defaultFallback
                }
                return translation
            }
        }

        return null
    }

    private fun resolveMappedTranslationClassOrNull(
        clazz: KClass<*>,
        locale: Locale,
        resolutionStack: List<ResolutionKey>,
        trace: ResolutionTrace?,
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
            val sourceTrace = trace?.let { ResolutionTrace(null) }
            val sourceTranslation = resolveBaseTranslationClassOrNull(
                clazz = mapperRegistration.sourceClass,
                locales = listOf(sourceLocale),
                resolutionStack = resolutionStack + resolutionKey,
                trace = sourceTrace,
            ) ?: return@forEach

            val translation = mapperRegistration.registration(sourceTranslation)
            if (trace != null) {
                trace.origin = BaseMapperOrigin(
                    translationClass = clazz,
                    resolvedLocale = locale,
                    locale = mapperRegistration.locale,
                    sourceLocale = mapperRegistration.sourceLocale,
                    registrationPath = mapperRegistration.registrationPath,
                    source = checkNotNull(sourceTrace?.origin),
                )
                trace.usesEnglishFallback = sourceTrace.usesEnglishFallback
            }
            return translation
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

    fun registeredLocales(): Set<Locale> = buildSet {
        addAll(pendingBundles.keys)
        addAll(registrations.keys)
        addAll(mapperRegistrations.keys.filterNotNull())
        addAll(overrideRegistrations.keys.filterNotNull())
    }

    private fun ensureLocalesRegistered(locales: List<Locale>) {
        locales.forEach(::ensureLocaleRegistered)
    }

    private fun ensureLocaleRegistered(locale: Locale) {
        val pending = pendingBundles[locale] ?: return
        while (pending.isNotEmpty()) {
            val declaration = pending.first()
            val bundle = declaration.bundle
            val instances = collectTranslations(bundle)
            val registrationsForLocale = registrations.getOrPut(locale) { mutableMapOf() }
            instances.forEach { (clazz, instance) ->
                registrationsForLocale[clazz] = BundleRegistration(bundle, instance, declaration.registrationPath)
                revision += 1
            }
            pending.removeFirst()
        }
        pendingBundles.remove(locale)
    }

    private fun fallbackLocales(locale: Locale): List<Locale> {
        val parts = locale.languageTag.split('-')

        return (parts.size downTo 1).map { index ->
            Locale.of(parts.take(index).joinToString("-"))
        }
    }

    private sealed interface TranslationRegistration

    private class DirectRegistration(
        val registration: () -> Any,
        val registrationPath: String,
    ) : TranslationRegistration

    private class BundleRegistration(
        val bundle: Bundle,
        var instance: Any?,
        val registrationPath: String,
    ) : TranslationRegistration

    private fun nextPath(name: String): String = "Registry/$name#${++declarationSequence}"

    private data class ResolutionKey(
        val clazz: KClass<*>,
        val locale: Locale,
        val phase: ResolutionPhase,
    )

    private enum class ResolutionPhase {
        BASE,
        FINAL,
    }
}
