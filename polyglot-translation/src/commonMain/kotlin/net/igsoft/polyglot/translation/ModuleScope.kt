package net.igsoft.polyglot.translation

import kotlin.jvm.JvmName
import kotlin.reflect.KClass
import net.igsoft.polyglot.locale.Locale

class ModuleScope internal constructor() {
    internal val bundles = mutableListOf<Bundle>()
    internal val mappers = mutableMapOf<Locale?, MutableMap<KClass<*>, MapperRegistration>>()
    internal val overrides = mutableMapOf<Locale?, MutableMap<KClass<*>, OverrideRegistration>>()

    fun registerBundle(definition: Definition) {
        when (definition) {
            is Bundle -> bundles.add(definition)
            is Module -> {
                val nested = ModuleScope()
                definition.register(nested)
                nested.validateAgainst(mappers, overrides)
                bundles.addAll(nested.bundles)
                nested.mappers.forEach { (locale, entries) ->
                    mappers.getOrPut(locale) { mutableMapOf() }.putAll(entries)
                }
                nested.overrides.forEach { (locale, entries) ->
                    overrides.getOrPut(locale) { mutableMapOf() }.putAll(entries)
                }
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

    @Suppress("UNCHECKED_CAST")
    fun <S : Any, T : Any> registerMapper(
        sourceClass: KClass<S>,
        targetClass: KClass<T>,
        locale: Locale? = null,
        sourceLocale: Locale? = null,
        registration: (S) -> T,
    ) {
        checkMapperAvailable(targetClass, locale, mappers, "base")
        mappers.getOrPut(locale) { mutableMapOf() }[targetClass] = MapperRegistration(
            sourceClass = sourceClass,
            sourceLocale = sourceLocale,
            registration = { source -> registration(source as S) },
        )
    }

    inline fun <reified S : Any, reified T : Any> registerMapper(
        locale: Locale? = null,
        sourceLocale: Locale? = null,
        noinline registration: (S, T) -> T,
    ) {
        registerMapper(S::class, T::class, locale, sourceLocale, registration)
    }

    @Suppress("UNCHECKED_CAST")
    fun <S : Any, T : Any> registerMapper(
        sourceClass: KClass<S>,
        targetClass: KClass<T>,
        locale: Locale? = null,
        sourceLocale: Locale? = null,
        registration: (S, T) -> T,
    ) {
        checkMapperAvailable(targetClass, locale, overrides, "override")
        overrides.getOrPut(locale) { mutableMapOf() }[targetClass] = OverrideRegistration(
            sourceClass = sourceClass,
            sourceLocale = sourceLocale,
            registration = { source, target -> registration(source as S, target as T) },
        )
    }

    internal fun validateAgainst(
        registeredMappers: Map<Locale?, Map<KClass<*>, MapperRegistration>>,
        registeredOverrides: Map<Locale?, Map<KClass<*>, OverrideRegistration>>,
    ) {
        mappers.forEach { (locale, entries) ->
            entries.keys.forEach { checkMapperAvailable(it, locale, registeredMappers, "base") }
        }
        overrides.forEach { (locale, entries) ->
            entries.keys.forEach { checkMapperAvailable(it, locale, registeredOverrides, "override") }
        }
    }

    private fun checkMapperAvailable(
        targetClass: KClass<*>,
        locale: Locale?,
        registrations: Map<Locale?, Map<KClass<*>, *>>,
        role: String,
    ) {
        check(registrations[locale]?.containsKey(targetClass) != true) {
            "Duplicate $role mapper [translationClass='${targetClass.simpleName}', locale='${locale?.languageTag ?: "global"}']"
        }
    }
}
