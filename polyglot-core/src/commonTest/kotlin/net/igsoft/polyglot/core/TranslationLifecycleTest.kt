package net.igsoft.polyglot.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class TranslationLifecycleTest {
    @Test
    fun `Given loaded bundle when changing locale then release every model and rebuild the bundle once on demand`() {
        val calls = mutableMapOf<MsgLocale, Int>()
        val registry = PolyglotRegistry(listOf(bundle(MsgLocales.EN, calls), bundle(MsgLocales.PL, calls)))
        val resolver = PolyglotResolver(registry)
        val first = resolver.getTranslationClass(Strings::class)
        val secondary = registry.getTranslationClass(SecondaryStrings::class, listOf(MsgLocales.EN))

        resolver.setCurrentLocale(MsgLocales.PL)

        assertEquals(mapOf(MsgLocales.EN to 1), calls)
        assertEquals(setOf(MsgLocales.EN, MsgLocales.PL), resolver.registeredLocales())
        val rebuilt = registry.getTranslationClass(SecondaryStrings::class, listOf(MsgLocales.EN))
        assertNotSame(secondary, rebuilt)
        assertEquals(2, calls[MsgLocales.EN])
        assertNotSame(first, resolver.getTranslationClass(Strings::class, MsgLocales.EN))
        assertEquals(2, calls[MsgLocales.EN])

        resolver.getTranslationClass(Strings::class)
        resolver.setCurrentLocale(MsgLocales.EN)
        val returned = resolver.getTranslationClass(Strings::class)

        assertNotSame(first, returned)
        assertEquals(3, calls[MsgLocales.EN])
        assertEquals(1, calls[MsgLocales.PL])
        assertSame(returned, resolver.getTranslationClass(Strings::class))
    }

    @Test
    fun `Given cached translation when setting the same locale then preserve cache`() {
        var creations = 0
        val registry = PolyglotRegistry()
        registry.registerTranslationClass(MsgLocales.EN, Strings::class) {
            creations += 1
            Strings("English")
        }
        val resolver = PolyglotResolver(registry)
        val first = resolver.getTranslationClass(Strings::class)

        resolver.setCurrentLocale(MsgLocale.of("en"))

        assertSame(first, resolver.getTranslationClass(Strings::class))
        assertEquals(1, creations)
    }

    @Test
    fun `Given two resolvers sharing a registry when one changes locale then preserve the other cached translation`() {
        val calls = mutableMapOf<MsgLocale, Int>()
        val registry = PolyglotRegistry(listOf(bundle(MsgLocales.EN, calls), bundle(MsgLocales.PL, calls)))
        val firstResolver = PolyglotResolver(registry)
        val secondResolver = PolyglotResolver(registry)
        registry.getTranslationClass(Strings::class, listOf(MsgLocales.PL))
        val original = secondResolver.getTranslationClass(Strings::class)
        firstResolver.getTranslationClass(Strings::class)

        firstResolver.setCurrentLocale(MsgLocales.PL)
        firstResolver.getTranslationClass(Strings::class)

        assertSame(original, secondResolver.getTranslationClass(Strings::class))
        assertEquals("en", original.label)
        secondResolver.getTranslationClass(SecondaryStrings::class)
        assertEquals(2, calls[MsgLocales.EN])
    }

    @Test
    fun `Given direct registration before a pending bundle when reloading then preserve the bundle precedence`() {
        val calls = mutableMapOf<MsgLocale, Int>()
        val registry = PolyglotRegistry(listOf(bundle(MsgLocales.EN, calls)))
        registry.registerTranslationClass(MsgLocales.EN, Strings::class) { Strings("Direct") }
        val resolver = PolyglotResolver(registry)
        assertEquals("en", resolver.getTranslationClass(Strings::class).label)

        resolver.setCurrentLocale(MsgLocales.PL)

        assertEquals("en", resolver.getTranslationClass(Strings::class).label)
        assertEquals(2, calls[MsgLocales.EN])
    }

    @Test
    fun `Given direct override after bundle loading when reloading another type then preserve the override`() {
        val calls = mutableMapOf<MsgLocale, Int>()
        val registry = PolyglotRegistry(listOf(bundle(MsgLocales.EN, calls)))
        val resolver = PolyglotResolver(registry)
        resolver.getTranslationClass(Strings::class)
        registry.registerTranslationClass(MsgLocales.EN, Strings::class) { Strings("Direct") }

        resolver.setCurrentLocale(MsgLocales.PL)
        resolver.getTranslationClass(SecondaryStrings::class)

        assertEquals("Direct", resolver.getTranslationClass(Strings::class).label)
        assertEquals(2, calls[MsgLocales.EN])
    }

    @Test
    fun `Given overlapping bundles when reloading in either lookup order then preserve the latest bundle precedence`() {
        listOf(true, false).forEach { secondaryFirst ->
            val calls = mutableMapOf<MsgLocale, Int>()
            val registry = PolyglotRegistry(listOf(bundle(MsgLocales.EN, calls)))
            val resolver = PolyglotResolver(registry)
            resolver.getTranslationClass(Strings::class)
            registry.registerBundle(object : MsgBundle() {
                override val locale = MsgLocales.EN

                override fun register() {
                    add(Strings("Latest"))
                }
            })
            assertEquals("Latest", resolver.getTranslationClass(Strings::class).label)

            resolver.setCurrentLocale(MsgLocales.PL)
            if (secondaryFirst) resolver.getTranslationClass(SecondaryStrings::class)
            assertEquals("Latest", resolver.getTranslationClass(Strings::class).label)
            resolver.getTranslationClass(SecondaryStrings::class)
            assertEquals("Latest", resolver.getTranslationClass(Strings::class).label)
            assertEquals(2, calls[MsgLocales.EN])
        }
    }

    @Test
    fun `Given cross locale inheritance and overrides when changing locale then rebuild sources and preserve mapping phases`() {
        val calls = mutableMapOf<MsgLocale, Int>()
        val portuguese = MsgLocale.of("pt")
        val brazilian = MsgLocale.of("pt-BR")
        val registry = PolyglotRegistry(
            listOf(bundle(portuguese, calls), bundle(MsgLocales.PL, calls))
        )
        registry.registerMapper(Strings::class, Strings::class, locale = brazilian, sourceLocale = portuguese) {
            it.copy(label = "Regional ${it.label}")
        }
        registry.registerMapper(Strings::class, SecondaryStrings::class, sourceLocale = portuguese) {
            source: Strings, target: SecondaryStrings -> target.copy(label = "${target.label} ${source.label}")
        }
        registry.registerMapper(SecondaryStrings::class, Strings::class, locale = brazilian, sourceLocale = MsgLocales.PL) {
            source: SecondaryStrings, target: Strings -> target.copy(label = "${target.label} ${source.label}")
        }
        val resolver = PolyglotResolver(registry)
        resolver.setCurrentLocale(brazilian)
        val first = resolver.getTranslationClass(Strings::class)
        assertEquals("Regional pt pl pt", first.label)

        resolver.setCurrentLocale(MsgLocales.EN)
        resolver.setCurrentLocale(brazilian)
        val second = resolver.getTranslationClass(Strings::class)

        assertEquals(first, second)
        assertNotSame(first, second)
        assertEquals(2, calls[portuguese])
        assertEquals(2, calls[MsgLocales.PL])
        assertSame(second, resolver.getTranslationClass(Strings::class))
    }

    @Test
    fun `Given released models when registering a new source then invalidate cached mapper results`() {
        val calls = mutableMapOf<MsgLocale, Int>()
        val registry = PolyglotRegistry(listOf(bundle(MsgLocales.EN, calls)))
        registry.registerMapper(SecondaryStrings::class, Strings::class) {
            source: SecondaryStrings, target: Strings -> target.copy(label = source.label)
        }
        val resolver = PolyglotResolver(registry)
        resolver.getTranslationClass(Strings::class)
        resolver.setCurrentLocale(MsgLocales.PL)
        resolver.getTranslationClass(Strings::class)

        registry.registerTranslationClass(MsgLocales.EN, SecondaryStrings::class) { SecondaryStrings("Updated") }

        assertEquals("Updated", resolver.getTranslationClass(Strings::class).label)
    }

    @Test
    fun `Given retained bundle when registering and reloading then bind the registry only during registration`() {
        val bundle = object : MsgBundle() {
            override val locale = MsgLocales.EN

            fun boundRegistry() = registry

            override fun register() {
                add(Strings("English"))
            }
        }
        val firstRegistry = PolyglotRegistry(listOf(bundle))
        val secondRegistry = PolyglotRegistry(listOf(bundle))
        val resolver = PolyglotResolver(firstRegistry)
        resolver.getTranslationClass(Strings::class)
        assertFailsWith<IllegalStateException> { bundle.boundRegistry() }
        secondRegistry.getTranslationClass(Strings::class, listOf(MsgLocales.EN))
        assertFailsWith<IllegalStateException> { bundle.boundRegistry() }

        resolver.setCurrentLocale(MsgLocales.PL)
        assertEquals("English", resolver.getTranslationClass(Strings::class).label)
        assertFailsWith<IllegalStateException> { bundle.boundRegistry() }
    }

    private fun bundle(locale: MsgLocale, calls: MutableMap<MsgLocale, Int>) = object : MsgBundle() {
        override val locale = locale

        override fun register() {
            calls[locale] = (calls[locale] ?: 0) + 1
            add(Strings(locale.languageTag))
            add(SecondaryStrings(locale.languageTag))
        }
    }

    private data class Strings(val label: String)

    private data class SecondaryStrings(val label: String)
}
