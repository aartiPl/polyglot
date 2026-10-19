package net.igsoft.polyglot.translation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import net.igsoft.polyglot.locale.Locales

class MapperProvenanceTest {
    @Test
    fun `Given duplicate in one module then report distinct declarations and apply nothing`() {
        val registry = Registry()
        val module = object : Module() {
            override fun register(registration: ModuleScope) {
                registration.registerMapper<Source, Target> { Target(it.value) }
                registration.registerMapper<Source, Target> { Target(it.value) }
            }
        }

        val error = assertFailsWith<IllegalStateException> { registry.registerDefinition(module) }

        assertTrue(error.message.orEmpty().contains("Duplicate base mapper [translationClass='Target', locale='global']"))
        assertTrue(error.message.orEmpty().contains("/base#1"))
        assertTrue(error.message.orEmpty().contains("/base#2"))
        assertEquals(0L, registry.revision)
        registry.registerMapper<Source, Target> { Target(it.value) }
    }

    @Test
    fun `Given module registered directly and through parent then identify both complete paths`() {
        val registry = Registry(listOf(SharedModule()))

        val error = assertFailsWith<IllegalStateException> { registry.registerDefinition(ParentModule()) }

        assertTrue(error.message.orEmpty().contains("existing='Registry/SharedModule#1/base#1'"))
        assertTrue(error.message.orEmpty().contains("incoming='Registry/ParentModule#2/SharedModule#1/base#1'"))
    }

    @Test
    fun `Given conflicting sibling modules then report both paths before applying bundles`() {
        val registry = Registry()
        val parent = object : Module() {
            override fun register(registration: ModuleScope) {
                registration.registerDefinition(SharedModule())
                registration.registerDefinition(SharedModule())
            }
        }

        val error = assertFailsWith<IllegalStateException> { registry.registerDefinition(parent) }

        assertTrue(error.message.orEmpty().contains("/SharedModule#1/base#1"))
        assertTrue(error.message.orEmpty().contains("/SharedModule#2/base#1"))
        assertEquals(0L, registry.revision)
    }

    @Test
    fun `Given conflicting regional override then preserve role locale revision and cached diagnostics`() {
        val registry = Registry()
        registry.registerTranslationClass<Source>(Locales.EN) { Source("Source") }
        registry.registerTranslationClass<Target>(Locales.EN) { Target("Target") }
        registry.registerMapper<Source, Target>(Locales.PT_BR) { source, target -> target.copy(value = source.value) }
        val diagnostics = DiagnosticResolver(registry)
        val cached = diagnostics.resolve<Target>(Locales.PT_BR)
        val revision = registry.revision

        val error = assertFailsWith<IllegalStateException> {
            registry.registerMapper<Source, Target>(Locales.PT_BR) { _, target -> target }
        }

        assertTrue(error.message.orEmpty().contains("Duplicate override mapper [translationClass='Target', locale='pt-BR']"))
        assertTrue(error.message.orEmpty().contains("existing='Registry/mapper#3/override#1'"))
        assertTrue(error.message.orEmpty().contains("incoming='Registry/mapper#4/override#1'"))
        assertEquals(revision, registry.revision)
        val result = diagnostics.resolve<Target>(Locales.PT_BR)
        assertSame(cached.value, result.value)
        assertSame(cached.diagnostics.origin, result.diagnostics.origin)
        assertTrue(result.diagnostics.cacheHit)
    }

    @Test
    fun `Given different mapper roles and target locales then allow every distinct key`() {
        val registry = Registry()
        registry.registerTranslationClass<Source>(Locales.EN) { Source("Source") }
        listOf(null, Locales.EN, Locales.PT_BR).forEach { locale ->
            registry.registerMapper<Source, Target>(locale, Locales.EN) { Target(it.value) }
            registry.registerMapper<Source, Target>(locale, Locales.EN) { source, target ->
                target.copy(value = "${target.value} ${source.value}")
            }
        }

        val diagnostics = DiagnosticResolver(registry)
        assertEquals("Source Source Source", diagnostics.resolve<Target>(Locales.PT_BR).value.value)
        assertEquals("Source Source Source", diagnostics.resolve<Target>(Locales.EN).value.value)
    }

    private class SharedModule : Module() {
        override fun register(registration: ModuleScope) {
            registration.registerMapper<Source, Target> { Target(it.value) }
        }
    }

    private class ParentModule : Module() {
        override fun register(registration: ModuleScope) {
            registration.registerDefinition(SharedModule())
        }
    }

    private data class Source(val value: String)

    private data class Target(val value: String)
}
