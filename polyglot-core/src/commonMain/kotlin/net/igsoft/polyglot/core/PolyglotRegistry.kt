package net.igsoft.polyglot.core

import kotlin.reflect.KClass

class PolyglotRegistry(msgAggregates: List<MsgAggregate> = emptyList()) {
    private val registrations = mutableMapOf<MsgLocale, MutableMap<KClass<*>, () -> Any>>()
    private val mapperRegistrations = mutableMapOf<KClass<*>, MapperRegistration>()
    private val pendingBundles = mutableMapOf<MsgLocale, MutableList<MsgBundle>>()

    init {
        msgAggregates.forEach { registerBundle(it) }
    }

    fun registerBundle(msgAggregate: MsgAggregate) {
        if (msgAggregate is MsgBundle) {
            pendingBundles.getOrPut(msgAggregate.locale) { mutableListOf() }.add(msgAggregate)
            return
        }

        msgAggregate.register(this)
    }

    fun <T : Any> registerTranslationClass(locale: MsgLocale, clazz: KClass<T>, registration: () -> T) {
        val registrationsForLocale = registrations.getOrPut(locale) {
            mutableMapOf()
        }

        registrationsForLocale[clazz] = registration
    }

    @Suppress("UNCHECKED_CAST")
    fun <S : Any, T : Any> registerMapper(
        sourceClass: KClass<S>,
        targetClass: KClass<T>,
        registration: (S) -> T,
    ) {
        mapperRegistrations[targetClass] = MapperRegistration(
            sourceClass = sourceClass,
            registration = { source -> registration(source as S) },
        )
    }

    @Suppress("UNCHECKED_CAST")
    internal fun <T : Any> getTranslationClass(clazz: KClass<T>, locales: List<MsgLocale>): T {
        val fallbackLocales = locales.flatMap(::fallbackLocales).distinct()
        ensureLocalesRegistered(fallbackLocales)
        return resolveTranslationClassOrNull(
            clazz = clazz,
            locales = fallbackLocales,
            resolutionStack = emptySet(),
        ) as? T
            ?: error(
                "Can not find translations for locales: [${fallbackLocales.joinToString(", ") { it.languageTag }}] " +
                        "[translationClass='${clazz.simpleName}']"
            )
    }

    private fun resolveTranslationClassOrNull(
        clazz: KClass<*>,
        locales: List<MsgLocale>,
        resolutionStack: Set<ResolutionKey>,
    ): Any? {
        locales.forEach { locale ->
            resolveMappedTranslationClassOrNull(
                clazz = clazz,
                locale = locale,
                resolutionStack = resolutionStack,
            )?.let { translation ->
                return translation
            }

            registrations[locale]?.get(clazz)?.let { registration ->
                return registration()
            }
        }

        return null
    }

    private fun resolveMappedTranslationClassOrNull(
        clazz: KClass<*>,
        locale: MsgLocale,
        resolutionStack: Set<ResolutionKey>,
    ): Any? {
        val mapperRegistration = mapperRegistrations[clazz] ?: return null
        val resolutionKey = ResolutionKey(clazz = clazz, locale = locale)

        if (resolutionKey in resolutionStack) {
            return null
        }

        val sourceTranslation = resolveTranslationClassOrNull(
            clazz = mapperRegistration.sourceClass,
            locales = listOf(locale),
            resolutionStack = resolutionStack + resolutionKey,
        ) ?: return null

        return mapperRegistration.registration(sourceTranslation)
    }

    fun registeredLocales(): Set<MsgLocale> = buildSet {
        addAll(pendingBundles.keys)
        addAll(registrations.keys)
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

    private data class MapperRegistration(
        val sourceClass: KClass<*>,
        val registration: (Any) -> Any,
    )

    private data class ResolutionKey(
        val clazz: KClass<*>,
        val locale: MsgLocale,
    )
}
