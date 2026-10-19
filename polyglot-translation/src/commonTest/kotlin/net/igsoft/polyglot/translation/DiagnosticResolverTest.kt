package net.igsoft.polyglot.translation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import net.igsoft.polyglot.locale.Locale
import net.igsoft.polyglot.locale.Locales

class DiagnosticResolverTest {
    @Test
    fun `Given bundle when resolving direct and fallback locales then report the selected bundle`() {
        listOf(Locales.PT, Locales.PT_BR, Locales.PL).forEach { requested ->
            val diagnostics = DiagnosticResolver(Registry(listOf(StringsBundle(Locales.PT), StringsBundle(Locales.EN))))

            val result = diagnostics.resolve<Strings>(requested)

            assertEquals(Strings::class, result.diagnostics.requestedClass)
            assertEquals(requested, result.diagnostics.requestedLocale)
            assertEquals(
                when (requested) {
                    Locales.PT_BR -> listOf(Locales.PT_BR, Locales.PT, Locales.EN)
                    Locales.PT -> listOf(Locales.PT, Locales.EN)
                    else -> listOf(Locales.PL, Locales.EN)
                },
                result.diagnostics.fallbackChain,
            )
            val origin = assertIs<BundleOrigin>(result.diagnostics.origin)
            assertEquals(if (requested == Locales.PL) Locales.EN else Locales.PT, origin.locale)
            assertEquals(StringsBundle::class, origin.bundleClass)
            assertTrue(origin.registrationPath.contains("StringsBundle"))
            assertEquals(requested == Locales.PL, result.diagnostics.usesEnglishFallback)
            assertFalse(result.diagnostics.cacheHit)
            assertSame(result.value, diagnostics.resolver.getTranslationClass<Strings>(requested))
        }
    }

    @Test
    fun `Given regional inheritance conversion and overrides then preserve every source branch`() {
        val registry = Registry(listOf(StringsBundle(Locales.PT)))
        registry.registerTranslationClass<Common>(Locales.PL) { Common("Polski") }
        registry.registerTranslationClass<Common>(Locales.EN) { Common("English") }
        registry.registerMapper<Strings>(Locales.PT_BR, Locales.PT) { it.copy(label = "Regional ${it.label}") }
        registry.registerMapper<Strings, Target> { Target(it.label) }
        registry.registerMapper<Common, Target> { common, target -> target.copy(label = "${target.label} ${common.label}") }
        registry.registerMapper<Common, Target>(Locales.PT_BR, Locales.PL) { common, target ->
            target.copy(label = "${target.label} ${common.label}")
        }
        val diagnostics = DiagnosticResolver(registry)

        val result = diagnostics.resolve<Target>(Locales.PT_BR)

        assertEquals("Regional pt English Polski", result.value.label)
        val regionalOverride = assertIs<OverrideOrigin>(result.diagnostics.origin)
        assertEquals(Locales.PT_BR, regionalOverride.locale)
        assertEquals(Locales.PL, regionalOverride.sourceLocale)
        assertEquals(Locales.PL, assertIs<FactoryOrigin>(regionalOverride.source).locale)
        val globalOverride = assertIs<OverrideOrigin>(regionalOverride.target)
        assertEquals(null, globalOverride.locale)
        assertEquals(null, globalOverride.sourceLocale)
        assertEquals(Locales.EN, assertIs<FactoryOrigin>(globalOverride.source).locale)
        val conversion = assertIs<BaseMapperOrigin>(globalOverride.target)
        assertEquals(Target::class, conversion.translationClass)
        assertEquals(null, conversion.locale)
        assertEquals(Locales.PT_BR, conversion.resolvedLocale)
        val inheritance = assertIs<BaseMapperOrigin>(conversion.source)
        assertEquals(Locales.PT_BR, inheritance.locale)
        assertEquals(Locales.PT, inheritance.sourceLocale)
        assertEquals(Locales.PT, assertIs<BundleOrigin>(inheritance.source).locale)
        assertTrue(result.diagnostics.usesEnglishFallback)
    }

    @Test
    fun `Given explicit English source or English language request then distinguish it from default fallback`() {
        val registry = Registry(listOf(StringsBundle(Locales.EN)))
        registry.registerMapper<Strings, Target>(sourceLocale = Locales.EN) { Target(it.label) }
        val diagnostics = DiagnosticResolver(registry)

        assertFalse(diagnostics.resolve<Target>(Locales.PL).diagnostics.usesEnglishFallback)
        assertFalse(diagnostics.resolve<Strings>(Locales.EN_US).diagnostics.usesEnglishFallback)
        assertFalse(diagnostics.resolve<Strings>(Locales.EN).diagnostics.usesEnglishFallback)
    }

    @Test
    fun `Given ordinary lookup first when diagnosing cache then reuse its actual value and origin without executing factories`() {
        var calls = 0
        val registry = Registry()
        registry.registerTranslationClass<Strings>(Locales.EN) { Strings("Factory ${++calls}") }
        val diagnostics = DiagnosticResolver(registry)
        val cached = diagnostics.resolver.getTranslationClass<Strings>(Locales.PL)

        val result = diagnostics.resolve(Strings::class, Locales.PL)
        val again = diagnostics.resolve<Strings>(Locales.PL)

        assertSame(cached, result.value)
        assertTrue(result.diagnostics.cacheHit)
        assertSame(result.diagnostics.origin, again.diagnostics.origin)
        assertEquals(1, calls)
        assertTrue(result.diagnostics.usesEnglishFallback)
    }

    @Test
    fun `Given registry mutation when diagnosing then invalidate value and provenance together`() {
        val registry = Registry(listOf(StringsBundle(Locales.EN)))
        val diagnostics = DiagnosticResolver(registry)
        val original = diagnostics.resolve<Strings>(Locales.PT_BR)
        registry.registerDefinition(StringsBundle(Locales.PT))

        val updated = diagnostics.resolve<Strings>(Locales.PT_BR)

        assertFalse(updated.diagnostics.cacheHit)
        assertEquals("pt", updated.value.label)
        assertEquals(Locales.PT, assertIs<BundleOrigin>(updated.diagnostics.origin).locale)
        assertFalse(updated.diagnostics.usesEnglishFallback)
        assertEquals(Locales.EN, assertIs<BundleOrigin>(original.diagnostics.origin).locale)
        assertTrue(original.diagnostics.usesEnglishFallback)
        registry.registerTranslationClass<Strings>(Locales.PT) { Strings("Direct") }
        assertIs<FactoryOrigin>(diagnostics.resolve<Strings>(Locales.PT_BR).diagnostics.origin)
    }

    @Test
    fun `Given diagnostic resolver when switching locale then preserve the existing model lifecycle`() {
        var bundles = 0
        var mappings = 0
        val registry = Registry(listOf(object : Bundle() {
            override val locale = Locales.PT

            override fun register(translations: BundleScope) {
                bundles += 1
                translations.add(Strings("pt"))
            }
        }))
        registry.registerMapper<Strings, Target> { mappings += 1; Target(it.label) }
        val diagnostics = DiagnosticResolver(registry)
        val resolver = diagnostics.resolver
        resolver.setCurrentLocale(Locales.PT_BR)
        val first = diagnostics.resolve<Target>()
        resolver.setCurrentLocale(Locales.PT_BR)
        assertSame(first.value, diagnostics.resolve(Target::class).value)
        assertEquals(1, bundles)
        assertEquals(1, mappings)

        resolver.setCurrentLocale(Locales.PL)
        resolver.setCurrentLocale(Locales.PT_BR)
        val rebuilt = diagnostics.resolve<Target>()

        assertNotSame(first.value, rebuilt.value)
        assertEquals(first.value, rebuilt.value)
        assertFalse(rebuilt.diagnostics.cacheHit)
        assertEquals(first.diagnostics.origin, rebuilt.diagnostics.origin)
        assertEquals(2, bundles)
        assertEquals(2, mappings)
    }

    @Test
    fun `Given shared registry when another resolver releases models then retain the cached provenance`() {
        val registry = Registry(listOf(StringsBundle(Locales.EN)))
        val diagnostics = DiagnosticResolver(registry)
        val first = diagnostics.resolve<Strings>()

        Resolver(registry).setCurrentLocale(Locales.PL)
        val cached = diagnostics.resolve<Strings>()

        assertTrue(cached.diagnostics.cacheHit)
        assertSame(first.value, cached.value)
        assertSame(first.diagnostics.origin, cached.diagnostics.origin)
    }

    @Test
    fun `Given unavailable mapper sources then report only transformations that actually ran`() {
        val registry = Registry(listOf(StringsBundle(Locales.PT)))
        registry.registerMapper<Common, Strings>(Locales.PT_BR, Locales.PL) { Strings(it.label) }
        registry.registerMapper<Common, Strings> { common, strings -> strings.copy(label = common.label) }

        val result = DiagnosticResolver(registry).resolve<Strings>(Locales.PT_BR)

        assertEquals("pt", result.value.label)
        assertIs<BundleOrigin>(result.diagnostics.origin)
        assertFalse(result.diagnostics.usesEnglishFallback)
    }

    @Test
    fun `Given source override and base conversion then diagnose the base phase without applying source overrides`() {
        var overrides = 0
        val registry = Registry(listOf(StringsBundle(Locales.PT)))
        registry.registerTranslationClass<Common>(Locales.PT) { Common("Overridden") }
        registry.registerMapper<Common, Strings> { common, strings ->
            overrides += 1
            strings.copy(label = common.label)
        }
        registry.registerMapper<Strings, Target> { Target(it.label) }
        val diagnostics = DiagnosticResolver(registry)

        val target = diagnostics.resolve<Target>(Locales.PT_BR)
        val strings = diagnostics.resolve<Strings>(Locales.PT_BR)

        assertEquals("pt", target.value.label)
        assertIs<BundleOrigin>(assertIs<BaseMapperOrigin>(target.diagnostics.origin).source)
        assertEquals("Overridden", strings.value.label)
        assertIs<OverrideOrigin>(strings.diagnostics.origin)
        assertEquals(1, overrides)
    }

    @Test
    fun `Given cached conversion when adding and replacing an external source then refresh value and origin`() {
        val registry = Registry(listOf(StringsBundle(Locales.PT)))
        registry.registerMapper<Strings, Target> { Target(it.label) }
        registry.registerMapper<Common, Target>(sourceLocale = Locales.PL) { common, target ->
            target.copy(label = common.label)
        }
        val diagnostics = DiagnosticResolver(registry)
        assertIs<BaseMapperOrigin>(diagnostics.resolve<Target>(Locales.PT_BR).diagnostics.origin)
        registry.registerTranslationClass<Common>(Locales.PL) { Common("First") }
        val first = diagnostics.resolve<Target>(Locales.PT_BR)
        assertEquals("First", first.value.label)
        val firstSource = assertIs<FactoryOrigin>(assertIs<OverrideOrigin>(first.diagnostics.origin).source)
        registry.registerTranslationClass<Common>(Locales.PL) { Common("Second") }

        val second = diagnostics.resolve<Target>(Locales.PT_BR)

        assertFalse(second.diagnostics.cacheHit)
        assertEquals("Second", second.value.label)
        val secondSource = assertIs<FactoryOrigin>(assertIs<OverrideOrigin>(second.diagnostics.origin).source)
        assertEquals(Locales.PL, secondSource.locale)
        assertTrue(firstSource.registrationPath != secondSource.registrationPath)
        assertSame(second.value, diagnostics.resolver.getTranslationClass<Target>(Locales.PT_BR))
    }

    @Test
    fun `Given overlapping bundles when restoring in either order then preserve the actual bundle owner`() {
        listOf(true, false).forEach { secondaryFirst ->
            val registry = Registry(listOf(object : Bundle() {
                override val locale = Locales.PT

                override fun register(translations: BundleScope) {
                    translations.add(Strings("Original"))
                    translations.add(Common("Common"))
                }
            }))
            val diagnostics = DiagnosticResolver(registry)
            diagnostics.resolve<Strings>(Locales.PT)
            registry.registerDefinition(StringsBundle(Locales.PT))
            val latest = diagnostics.resolve<Strings>(Locales.PT)
            diagnostics.resolver.setCurrentLocale(Locales.PL)
            if (secondaryFirst) diagnostics.resolve<Common>(Locales.PT)

            val restored = diagnostics.resolve<Strings>(Locales.PT)
            diagnostics.resolve<Common>(Locales.PT)

            assertEquals("pt", restored.value.label)
            assertNotSame(latest.value, restored.value)
            assertEquals(latest.diagnostics.origin, restored.diagnostics.origin)
            assertEquals(StringsBundle::class, assertIs<BundleOrigin>(restored.diagnostics.origin).bundleClass)
        }
    }

    @Test
    fun `Given explicit regional source when only its language exists then skip it without expanding its chain`() {
        val registry = Registry(listOf(StringsBundle(Locales.PT)))
        registry.registerTranslationClass<Common>(Locales.PL) { Common("Unavailable") }
        registry.registerMapper<Common, Strings>(sourceLocale = Locales.PL_PL) { common, strings ->
            strings.copy(label = common.label)
        }

        val result = DiagnosticResolver(registry).resolve<Strings>(Locales.PT_BR)

        assertEquals("pt", result.value.label)
        assertIs<BundleOrigin>(result.diagnostics.origin)
    }

    private class StringsBundle(override val locale: Locale) : Bundle() {
        override fun register(translations: BundleScope) {
            translations.add(Strings(locale.languageTag))
        }
    }

    private data class Strings(val label: String)

    private data class Common(val label: String)

    private data class Target(val label: String)
}
