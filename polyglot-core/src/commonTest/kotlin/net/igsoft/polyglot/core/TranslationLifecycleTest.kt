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

                override fun register(translations: MsgTranslations) {
                    translations.add(Strings("Latest"))
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
    fun `Given failed initial bundle loading when retrying then publish only a complete set of models`() {
        var attempts = 0
        val calls = mutableMapOf<MsgLocale, Int>()
        val firstBundle = bundle(MsgLocales.EN, calls)
        val bundle = object : MsgBundle() {
            override val locale = MsgLocales.EN

            override fun register(translations: MsgTranslations) {
                attempts += 1
                translations.add(Strings("Attempt $attempts"))
                check(attempts != 1) { "Loading failed" }
                translations.add(SecondaryStrings("Complete"))
            }
        }
        val resolver = PolyglotResolver(PolyglotRegistry(listOf(firstBundle, bundle)))

        assertFailsWith<IllegalStateException> { resolver.getTranslationClass(Strings::class) }

        assertEquals("Complete", resolver.getTranslationClass(SecondaryStrings::class).label)
        assertEquals("Attempt 2", resolver.getTranslationClass(Strings::class).label)
        assertEquals(2, attempts)
        assertEquals(1, calls[MsgLocales.EN])
    }

    @Test
    fun `Given failed bundle restoration when retrying another type then discard partially created models`() {
        var attempts = 0
        val bundle = object : MsgBundle() {
            override val locale = MsgLocales.EN

            override fun register(translations: MsgTranslations) {
                attempts += 1
                translations.add(Strings("Attempt $attempts"))
                check(attempts != 2) { "Restoration failed" }
                translations.add(SecondaryStrings("Complete"))
            }
        }
        val resolver = PolyglotResolver(PolyglotRegistry(listOf(bundle)))
        resolver.getTranslationClass(Strings::class)
        resolver.setCurrentLocale(MsgLocales.PL)

        assertFailsWith<IllegalStateException> { resolver.getTranslationClass(SecondaryStrings::class) }

        assertEquals("Attempt 3", resolver.getTranslationClass(Strings::class).label)
        assertEquals("Complete", resolver.getTranslationClass(SecondaryStrings::class).label)
        assertEquals(3, attempts)
    }

    @Test
    fun `Given missing model during restoration when retrying then keep all owned models released`() {
        var attempts = 0
        val bundle = object : MsgBundle() {
            override val locale = MsgLocales.EN

            override fun register(translations: MsgTranslations) {
                attempts += 1
                translations.add(Strings("Attempt $attempts"))
                if (attempts != 2) {
                    translations.add(SecondaryStrings("Complete"))
                }
            }
        }
        val resolver = PolyglotResolver(PolyglotRegistry(listOf(bundle)))
        resolver.getTranslationClass(Strings::class)
        resolver.setCurrentLocale(MsgLocales.PL)

        assertFailsWith<IllegalStateException> { resolver.getTranslationClass(Strings::class) }

        assertEquals("Attempt 3", resolver.getTranslationClass(Strings::class).label)
        assertEquals("Complete", resolver.getTranslationClass(SecondaryStrings::class).label)
        assertEquals(3, attempts)
    }

    @Test
    fun `Given shared bundle when loading and reloading in separate registries then create independent models`() {
        val bundle = object : MsgBundle() {
            override val locale = MsgLocales.EN

            override fun register(translations: MsgTranslations) {
                translations.add(Strings("English"))
            }
        }
        val firstRegistry = PolyglotRegistry(listOf(bundle))
        val secondRegistry = PolyglotRegistry(listOf(bundle))
        val resolver = PolyglotResolver(firstRegistry)
        val first = resolver.getTranslationClass(Strings::class)
        val second = secondRegistry.getTranslationClass(Strings::class, listOf(MsgLocales.EN))
        assertNotSame(first, second)

        resolver.setCurrentLocale(MsgLocales.PL)
        val restored = resolver.getTranslationClass(Strings::class)
        assertEquals("English", restored.label)
        assertNotSame(first, restored)
        assertSame(second, secondRegistry.getTranslationClass(Strings::class, listOf(MsgLocales.EN)))
    }

    private fun bundle(locale: MsgLocale, calls: MutableMap<MsgLocale, Int>) = object : MsgBundle() {
        override val locale = locale

        override fun register(translations: MsgTranslations) {
            calls[locale] = (calls[locale] ?: 0) + 1
            translations.add(Strings(locale.languageTag))
            translations.add(SecondaryStrings(locale.languageTag))
        }
    }

    private data class Strings(val label: String)

    private data class SecondaryStrings(val label: String)
}
