package net.igsoft.polyglot.core

import kotlin.jvm.JvmName
import kotlin.reflect.KClass

class MsgRegistration internal constructor() {
    internal val bundles = mutableListOf<MsgBundle>()
    internal val mappers = mutableMapOf<MsgLocale?, MutableMap<KClass<*>, MapperRegistration>>()
    internal val overrides = mutableMapOf<MsgLocale?, MutableMap<KClass<*>, OverrideRegistration>>()

    fun registerBundle(definition: MsgDefinition) {
        when (definition) {
            is MsgBundle -> bundles.add(definition)
            is MsgAggregate -> {
                val nested = MsgRegistration()
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

    @Suppress("UNCHECKED_CAST")
    fun <S : Any, T : Any> registerMapper(
        sourceClass: KClass<S>,
        targetClass: KClass<T>,
        locale: MsgLocale? = null,
        sourceLocale: MsgLocale? = null,
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
        locale: MsgLocale? = null,
        sourceLocale: MsgLocale? = null,
        noinline registration: (S, T) -> T,
    ) {
        registerMapper(S::class, T::class, locale, sourceLocale, registration)
    }

    @Suppress("UNCHECKED_CAST")
    fun <S : Any, T : Any> registerMapper(
        sourceClass: KClass<S>,
        targetClass: KClass<T>,
        locale: MsgLocale? = null,
        sourceLocale: MsgLocale? = null,
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
        registeredMappers: Map<MsgLocale?, Map<KClass<*>, MapperRegistration>>,
        registeredOverrides: Map<MsgLocale?, Map<KClass<*>, OverrideRegistration>>,
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
        locale: MsgLocale?,
        registrations: Map<MsgLocale?, Map<KClass<*>, *>>,
        role: String,
    ) {
        check(registrations[locale]?.containsKey(targetClass) != true) {
            "Duplicate $role mapper [translationClass='${targetClass.simpleName}', locale='${locale?.languageTag ?: "global"}']"
        }
    }
}
