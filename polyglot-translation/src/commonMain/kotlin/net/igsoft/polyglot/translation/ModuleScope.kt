package net.igsoft.polyglot.translation

import kotlin.jvm.JvmName
import kotlin.reflect.KClass
import net.igsoft.polyglot.locale.Locale

class ModuleScope internal constructor(private val registrationPath: String = "Registry") {
    private var declarationSequence = 0
    internal val bundles = mutableListOf<BundleDeclaration>()
    internal val mappers = mutableMapOf<Locale?, MutableMap<KClass<*>, MapperRegistration>>()
    internal val overrides = mutableMapOf<Locale?, MutableMap<KClass<*>, OverrideRegistration>>()

    fun registerDefinition(definition: Definition) {
        val path = nextPath(definition::class.simpleName ?: if (definition is Bundle) "Bundle" else "Module")
        when (definition) {
            is Bundle -> bundles.add(BundleDeclaration(definition, path))
            is Module -> {
                val nested = ModuleScope(path)
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
        val path = nextPath("base")
        checkMapperAvailable(targetClass, locale, mappers[locale]?.get(targetClass)?.registrationPath, path, "base")
        mappers.getOrPut(locale) { mutableMapOf() }[targetClass] = MapperRegistration(
            sourceClass = sourceClass,
            sourceLocale = sourceLocale,
            locale = locale,
            registrationPath = path,
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
        val path = nextPath("override")
        checkMapperAvailable(targetClass, locale, overrides[locale]?.get(targetClass)?.registrationPath, path, "override")
        overrides.getOrPut(locale) { mutableMapOf() }[targetClass] = OverrideRegistration(
            sourceClass = sourceClass,
            sourceLocale = sourceLocale,
            locale = locale,
            registrationPath = path,
            registration = { source, target -> registration(source as S, target as T) },
        )
    }

    internal fun validateAgainst(
        registeredMappers: Map<Locale?, Map<KClass<*>, MapperRegistration>>,
        registeredOverrides: Map<Locale?, Map<KClass<*>, OverrideRegistration>>,
    ) {
        mappers.forEach { (locale, entries) ->
            entries.forEach { (clazz, registration) ->
                checkMapperAvailable(
                    clazz, locale, registeredMappers[locale]?.get(clazz)?.registrationPath,
                    registration.registrationPath, "base",
                )
            }
        }
        overrides.forEach { (locale, entries) ->
            entries.forEach { (clazz, registration) ->
                checkMapperAvailable(
                    clazz, locale, registeredOverrides[locale]?.get(clazz)?.registrationPath,
                    registration.registrationPath, "override",
                )
            }
        }
    }

    private fun checkMapperAvailable(
        targetClass: KClass<*>,
        locale: Locale?,
        existingPath: String?,
        incomingPath: String,
        role: String,
    ) {
        check(existingPath == null) {
            "Duplicate $role mapper [translationClass='${targetClass.simpleName}', locale='${locale?.languageTag ?: "global"}'] " +
                "[existing='$existingPath', incoming='$incomingPath']"
        }
    }

    private fun nextPath(name: String): String = "$registrationPath/$name#${++declarationSequence}"
}
