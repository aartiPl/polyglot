package net.igsoft.polyglot.translation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import net.igsoft.polyglot.locale.Locale
import net.igsoft.polyglot.locale.Locales

class ModuleScopeTest {
    @Test
    fun `Given aggregate throwing after declarations when registering then discard all declarations`() {
        val registry = Registry()
        val aggregate = object : Module() {
            override fun register(registration: ModuleScope) {
                registration.registerDefinition(bundle())
                registration.registerMapper(Source::class, Target::class) { source -> Target(source.value) }
                registration.registerMapper(Source::class, Target::class) { source: Source, target: Target ->
                    target.copy(value = source.value)
                }
                error("Registration failed")
            }
        }

        assertFailsWith<IllegalStateException> { registry.registerDefinition(aggregate) }

        assertEquals(emptySet(), registry.registeredLocales())
        assertEquals(0L, registry.revision)
        registry.registerMapper(Source::class, Target::class) { source -> Target(source.value) }
        registry.registerMapper(Source::class, Target::class) { _: Source, target: Target -> target }
    }

    @Test
    fun `Given nested aggregate throwing when registering then discard parent and child declarations`() {
        val registry = Registry()
        val child = object : Module() {
            override fun register(registration: ModuleScope) {
                registration.registerMapper(Source::class, Target::class) { source -> Target(source.value) }
                error("Child failed")
            }
        }
        val parent = object : Module() {
            override fun register(registration: ModuleScope) {
                registration.registerDefinition(bundle())
                registration.registerDefinition(child)
            }
        }

        assertFailsWith<IllegalStateException> { registry.registerDefinition(parent) }

        assertEquals(emptySet(), registry.registeredLocales())
        assertEquals(0L, registry.revision)
    }

    @Test
    fun `Given duplicate mapper within aggregate when registering then discard earlier declarations`() {
        val registry = Registry()
        val aggregate = object : Module() {
            override fun register(registration: ModuleScope) {
                registration.registerDefinition(bundle())
                repeat(2) {
                    registration.registerMapper(Source::class, Target::class) { source -> Target(source.value) }
                }
            }
        }

        val exception = assertFailsWith<IllegalStateException> { registry.registerDefinition(aggregate) }

        assertEquals(
            "Duplicate base mapper [translationClass='Target', locale='global'] " +
                "[existing='Registry/Module#1/base#2', incoming='Registry/Module#1/base#3']",
            exception.message,
        )
        assertEquals(emptySet(), registry.registeredLocales())
        assertEquals(0L, registry.revision)
    }

    @Test
    fun `Given conflict with existing override when registering aggregate then preserve registry and cached results`() {
        val registry = Registry(listOf(bundle()))
        registry.registerMapper(Source::class, Target::class) { source -> Target(source.value) }
        registry.registerMapper(Source::class, Target::class) { _: Source, target: Target -> target }
        val resolver = Resolver(registry)
        val cached = resolver.getTranslationClass(Target::class)
        val revision = registry.revision
        val aggregate = object : Module() {
            override fun register(registration: ModuleScope) {
                registration.registerDefinition(bundle(Locales.PL))
                registration.registerMapper(Target::class, Source::class) { target -> Source(target.value) }
                registration.registerMapper(Source::class, Target::class) { _: Source, target: Target -> target }
            }
        }

        assertFailsWith<IllegalStateException> { registry.registerDefinition(aggregate) }

        assertEquals(setOf(Locales.EN), registry.registeredLocales())
        assertEquals(revision, registry.revision)
        assertSame(cached, resolver.getTranslationClass(Target::class))
        registry.registerMapper(Target::class, Source::class) { target -> Source(target.value) }
    }

    @Test
    fun `Given caught nested conflict when registering then discard every declaration from conflicting child`() {
        val registry = Registry()
        val child = object : Module() {
            override fun register(registration: ModuleScope) {
                registration.registerDefinition(bundle(Locales.PL))
                registration.registerMapper(Target::class, Source::class) { target -> Source(target.value) }
                registration.registerMapper(Source::class, Target::class) { source -> Target(source.value) }
            }
        }
        val parent = object : Module() {
            override fun register(registration: ModuleScope) {
                registration.registerMapper(Source::class, Target::class) { source -> Target(source.value) }
                assertFailsWith<IllegalStateException> { registration.registerDefinition(child) }
                registration.registerDefinition(bundle())
            }
        }

        registry.registerDefinition(parent)

        assertEquals(setOf(Locales.EN), registry.registeredLocales())
        assertEquals("Source", Resolver(registry).getTranslationClass(Target::class).value)
        registry.registerMapper(Target::class, Source::class) { target -> Source(target.value) }
    }

    @Test
    fun `Given retained collector when adding later declarations then registry keeps the applied snapshot`() {
        lateinit var collector: ModuleScope
        val aggregate = object : Module() {
            override fun register(registration: ModuleScope) {
                collector = registration
                registration.registerDefinition(bundle())
                registration.registerMapper(Source::class, Target::class, locale = Locales.EN) { source ->
                    Target(source.value)
                }
            }
        }
        val registry = Registry(listOf(aggregate))
        val revision = registry.revision

        collector.registerDefinition(bundle(Locales.PL))
        collector.registerMapper(Target::class, Source::class, locale = Locales.EN) { target -> Source(target.value) }

        assertEquals(setOf(Locales.EN), registry.registeredLocales())
        assertEquals(revision, registry.revision)
        assertEquals("Source", Resolver(registry).getTranslationClass(Target::class).value)
        registry.registerMapper(Target::class, Source::class, locale = Locales.EN) { target -> Source(target.value) }
    }

    private fun bundle(locale: Locale = Locales.EN) = object : Bundle() {
        override val locale = locale

        override fun register(translations: BundleScope) {
            translations.add(Source("Source"))
        }
    }

    private data class Source(val value: String)

    private data class Target(val value: String)
}
