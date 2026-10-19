package net.igsoft.polyglot.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class MsgRegistrationTest {
    @Test
    fun `Given aggregate throwing after declarations when registering then discard all declarations`() {
        val registry = PolyglotRegistry()
        val aggregate = object : MsgAggregate() {
            override fun register(registration: MsgRegistration) {
                registration.registerBundle(bundle())
                registration.registerMapper(Source::class, Target::class) { source -> Target(source.value) }
                registration.registerMapper(Source::class, Target::class) { source: Source, target: Target ->
                    target.copy(value = source.value)
                }
                error("Registration failed")
            }
        }

        assertFailsWith<IllegalStateException> { registry.registerBundle(aggregate) }

        assertEquals(emptySet(), registry.registeredLocales())
        assertEquals(0L, registry.revision)
        registry.registerMapper(Source::class, Target::class) { source -> Target(source.value) }
        registry.registerMapper(Source::class, Target::class) { _: Source, target: Target -> target }
    }

    @Test
    fun `Given nested aggregate throwing when registering then discard parent and child declarations`() {
        val registry = PolyglotRegistry()
        val child = object : MsgAggregate() {
            override fun register(registration: MsgRegistration) {
                registration.registerMapper(Source::class, Target::class) { source -> Target(source.value) }
                error("Child failed")
            }
        }
        val parent = object : MsgAggregate() {
            override fun register(registration: MsgRegistration) {
                registration.registerBundle(bundle())
                registration.registerBundle(child)
            }
        }

        assertFailsWith<IllegalStateException> { registry.registerBundle(parent) }

        assertEquals(emptySet(), registry.registeredLocales())
        assertEquals(0L, registry.revision)
    }

    @Test
    fun `Given duplicate mapper within aggregate when registering then discard earlier declarations`() {
        val registry = PolyglotRegistry()
        val aggregate = object : MsgAggregate() {
            override fun register(registration: MsgRegistration) {
                registration.registerBundle(bundle())
                repeat(2) {
                    registration.registerMapper(Source::class, Target::class) { source -> Target(source.value) }
                }
            }
        }

        val exception = assertFailsWith<IllegalStateException> { registry.registerBundle(aggregate) }

        assertEquals("Duplicate base mapper [translationClass='Target', locale='global']", exception.message)
        assertEquals(emptySet(), registry.registeredLocales())
        assertEquals(0L, registry.revision)
    }

    @Test
    fun `Given conflict with existing override when registering aggregate then preserve registry and cached results`() {
        val registry = PolyglotRegistry(listOf(bundle()))
        registry.registerMapper(Source::class, Target::class) { source -> Target(source.value) }
        registry.registerMapper(Source::class, Target::class) { _: Source, target: Target -> target }
        val resolver = PolyglotResolver(registry)
        val cached = resolver.getTranslationClass(Target::class)
        val revision = registry.revision
        val aggregate = object : MsgAggregate() {
            override fun register(registration: MsgRegistration) {
                registration.registerBundle(bundle(MsgLocales.PL))
                registration.registerMapper(Target::class, Source::class) { target -> Source(target.value) }
                registration.registerMapper(Source::class, Target::class) { _: Source, target: Target -> target }
            }
        }

        assertFailsWith<IllegalStateException> { registry.registerBundle(aggregate) }

        assertEquals(setOf(MsgLocales.EN), registry.registeredLocales())
        assertEquals(revision, registry.revision)
        assertSame(cached, resolver.getTranslationClass(Target::class))
        registry.registerMapper(Target::class, Source::class) { target -> Source(target.value) }
    }

    @Test
    fun `Given caught nested conflict when registering then discard every declaration from conflicting child`() {
        val registry = PolyglotRegistry()
        val child = object : MsgAggregate() {
            override fun register(registration: MsgRegistration) {
                registration.registerBundle(bundle(MsgLocales.PL))
                registration.registerMapper(Target::class, Source::class) { target -> Source(target.value) }
                registration.registerMapper(Source::class, Target::class) { source -> Target(source.value) }
            }
        }
        val parent = object : MsgAggregate() {
            override fun register(registration: MsgRegistration) {
                registration.registerMapper(Source::class, Target::class) { source -> Target(source.value) }
                assertFailsWith<IllegalStateException> { registration.registerBundle(child) }
                registration.registerBundle(bundle())
            }
        }

        registry.registerBundle(parent)

        assertEquals(setOf(MsgLocales.EN), registry.registeredLocales())
        assertEquals("Source", PolyglotResolver(registry).getTranslationClass(Target::class).value)
        registry.registerMapper(Target::class, Source::class) { target -> Source(target.value) }
    }

    @Test
    fun `Given retained collector when adding later declarations then registry keeps the applied snapshot`() {
        lateinit var collector: MsgRegistration
        val aggregate = object : MsgAggregate() {
            override fun register(registration: MsgRegistration) {
                collector = registration
                registration.registerBundle(bundle())
                registration.registerMapper(Source::class, Target::class, locale = MsgLocales.EN) { source ->
                    Target(source.value)
                }
            }
        }
        val registry = PolyglotRegistry(listOf(aggregate))
        val revision = registry.revision

        collector.registerBundle(bundle(MsgLocales.PL))
        collector.registerMapper(Target::class, Source::class, locale = MsgLocales.EN) { target -> Source(target.value) }

        assertEquals(setOf(MsgLocales.EN), registry.registeredLocales())
        assertEquals(revision, registry.revision)
        assertEquals("Source", PolyglotResolver(registry).getTranslationClass(Target::class).value)
        registry.registerMapper(Target::class, Source::class, locale = MsgLocales.EN) { target -> Source(target.value) }
    }

    private fun bundle(locale: MsgLocale = MsgLocales.EN) = object : MsgBundle() {
        override val locale = locale

        override fun register(translations: MsgTranslations) {
            translations.add(Source("Source"))
        }
    }

    private data class Source(val value: String)

    private data class Target(val value: String)
}
