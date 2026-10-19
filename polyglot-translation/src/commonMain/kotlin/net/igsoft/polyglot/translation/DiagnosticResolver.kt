package net.igsoft.polyglot.translation

import kotlin.reflect.KClass
import net.igsoft.polyglot.locale.Locale

/** Owns a resolver whose ordinary and diagnostic lookups share cached values and provenance. */
class DiagnosticResolver(registry: Registry) {
    val resolver: Resolver = Resolver(registry, mutableMapOf())

    inline fun <reified T : Any> resolve(): TranslationResolution<T> = resolve(T::class)

    inline fun <reified T : Any> resolve(locale: Locale): TranslationResolution<T> = resolve(T::class, locale)

    fun <T : Any> resolve(clazz: KClass<T>): TranslationResolution<T> = resolver.resolveWithDiagnostics(clazz)

    fun <T : Any> resolve(clazz: KClass<T>, locale: Locale): TranslationResolution<T> =
        resolver.resolveWithDiagnostics(clazz, locale)
}
