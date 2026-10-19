package net.igsoft.polyglot.translation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import net.igsoft.polyglot.locale.Locale
import net.igsoft.polyglot.locale.Locales

class TranslationLifecycleTest {
    @Test
    fun `Given loaded bundle when changing locale then release every model and rebuild the bundle once on demand`() {
        val calls = mutableMapOf<Locale, Int>()
        val registry = Registry(listOf(bundle(Locales.EN, calls), bundle(Locales.PL, calls)))
        val resolver = Resolver(registry)
        val first = resolver.getTranslationClass(Strings::class)
        val secondary = registry.getTranslationClass(SecondaryStrings::class, listOf(Locales.EN))

        resolver.setCurrentLocale(Locales.PL)

        assertEquals(mapOf(Locales.EN to 1), calls)
        assertEquals(setOf(Locales.EN, Locales.PL), resolver.registeredLocales())
        val rebuilt = registry.getTranslationClass(SecondaryStrings::class, listOf(Locales.EN))
        assertNotSame(secondary, rebuilt)
        assertEquals(2, calls[Locales.EN])
        assertNotSame(first, resolver.getTranslationClass(Strings::class, Locales.EN))
        assertEquals(2, calls[Locales.EN])

        resolver.getTranslationClass(Strings::class)
        resolver.setCurrentLocale(Locales.EN)
        val returned = resolver.getTranslationClass(Strings::class)

        assertNotSame(first, returned)
        assertEquals(3, calls[Locales.EN])
        assertEquals(1, calls[Locales.PL])
        assertSame(returned, resolver.getTranslationClass(Strings::class))
    }

    @Test
    fun `Given cached translation when setting the same locale then preserve cache`() {
        var creations = 0
        val registry = Registry()
        registry.registerTranslationClass(Locales.EN, Strings::class) {
            creations += 1
            Strings("English")
        }
        val resolver = Resolver(registry)
        val first = resolver.getTranslationClass(Strings::class)

        resolver.setCurrentLocale(Locale.of("en"))

        assertSame(first, resolver.getTranslationClass(Strings::class))
        assertEquals(1, creations)
    }

    @Test
    fun `Given two resolvers sharing a registry when one changes locale then preserve the other cached translation`() {
        val calls = mutableMapOf<Locale, Int>()
        val registry = Registry(listOf(bundle(Locales.EN, calls), bundle(Locales.PL, calls)))
        val firstResolver = Resolver(registry)
        val secondResolver = Resolver(registry)
        registry.getTranslationClass(Strings::class, listOf(Locales.PL))
        val original = secondResolver.getTranslationClass(Strings::class)
        firstResolver.getTranslationClass(Strings::class)

        firstResolver.setCurrentLocale(Locales.PL)
        firstResolver.getTranslationClass(Strings::class)

        assertSame(original, secondResolver.getTranslationClass(Strings::class))
        assertEquals("en", original.label)
        secondResolver.getTranslationClass(SecondaryStrings::class)
        assertEquals(2, calls[Locales.EN])
    }

    @Test
    fun `Given direct registration before a pending bundle when reloading then preserve the bundle precedence`() {
        val calls = mutableMapOf<Locale, Int>()
        val registry = Registry(listOf(bundle(Locales.EN, calls)))
        registry.registerTranslationClass(Locales.EN, Strings::class) { Strings("Direct") }
        val resolver = Resolver(registry)
        assertEquals("en", resolver.getTranslationClass(Strings::class).label)

        resolver.setCurrentLocale(Locales.PL)

        assertEquals("en", resolver.getTranslationClass(Strings::class).label)
        assertEquals(2, calls[Locales.EN])
    }

    @Test
    fun `Given direct override after bundle loading when reloading another type then preserve the override`() {
        val calls = mutableMapOf<Locale, Int>()
        val registry = Registry(listOf(bundle(Locales.EN, calls)))
        val resolver = Resolver(registry)
        resolver.getTranslationClass(Strings::class)
        registry.registerTranslationClass(Locales.EN, Strings::class) { Strings("Direct") }

        resolver.setCurrentLocale(Locales.PL)
        resolver.getTranslationClass(SecondaryStrings::class)

        assertEquals("Direct", resolver.getTranslationClass(Strings::class).label)
        assertEquals(2, calls[Locales.EN])
    }

    @Test
    fun `Given overlapping bundles when reloading in either lookup order then preserve the latest bundle precedence`() {
        listOf(true, false).forEach { secondaryFirst ->
            val calls = mutableMapOf<Locale, Int>()
            val registry = Registry(listOf(bundle(Locales.EN, calls)))
            val resolver = Resolver(registry)
            resolver.getTranslationClass(Strings::class)
            registry.registerDefinition(object : Bundle() {
                override val locale = Locales.EN

                override fun register(translations: BundleScope) {
                    translations.add(Strings("Latest"))
                }
            })
            assertEquals("Latest", resolver.getTranslationClass(Strings::class).label)

            resolver.setCurrentLocale(Locales.PL)
            if (secondaryFirst) resolver.getTranslationClass(SecondaryStrings::class)
            assertEquals("Latest", resolver.getTranslationClass(Strings::class).label)
            resolver.getTranslationClass(SecondaryStrings::class)
            assertEquals("Latest", resolver.getTranslationClass(Strings::class).label)
            assertEquals(2, calls[Locales.EN])
        }
    }

    @Test
    fun `Given cross locale inheritance and overrides when changing locale then rebuild sources and preserve mapping phases`() {
        val calls = mutableMapOf<Locale, Int>()
        val portuguese = Locale.of("pt")
        val brazilian = Locale.of("pt-BR")
        val registry = Registry(
            listOf(bundle(portuguese, calls), bundle(Locales.PL, calls))
        )
        registry.registerMapper(Strings::class, Strings::class, locale = brazilian, sourceLocale = portuguese) {
            it.copy(label = "Regional ${it.label}")
        }
        registry.registerMapper(Strings::class, SecondaryStrings::class, sourceLocale = portuguese) {
            source: Strings, target: SecondaryStrings -> target.copy(label = "${target.label} ${source.label}")
        }
        registry.registerMapper(SecondaryStrings::class, Strings::class, locale = brazilian, sourceLocale = Locales.PL) {
            source: SecondaryStrings, target: Strings -> target.copy(label = "${target.label} ${source.label}")
        }
        val resolver = Resolver(registry)
        resolver.setCurrentLocale(brazilian)
        val first = resolver.getTranslationClass(Strings::class)
        assertEquals("Regional pt pl pt", first.label)

        resolver.setCurrentLocale(Locales.EN)
        resolver.setCurrentLocale(brazilian)
        val second = resolver.getTranslationClass(Strings::class)

        assertEquals(first, second)
        assertNotSame(first, second)
        assertEquals(2, calls[portuguese])
        assertEquals(2, calls[Locales.PL])
        assertSame(second, resolver.getTranslationClass(Strings::class))
    }

    @Test
    fun `Given released models when registering a new source then invalidate cached mapper results`() {
        val calls = mutableMapOf<Locale, Int>()
        val registry = Registry(listOf(bundle(Locales.EN, calls)))
        registry.registerMapper(SecondaryStrings::class, Strings::class) {
            source: SecondaryStrings, target: Strings -> target.copy(label = source.label)
        }
        val resolver = Resolver(registry)
        resolver.getTranslationClass(Strings::class)
        resolver.setCurrentLocale(Locales.PL)
        resolver.getTranslationClass(Strings::class)

        registry.registerTranslationClass(Locales.EN, SecondaryStrings::class) { SecondaryStrings("Updated") }

        assertEquals("Updated", resolver.getTranslationClass(Strings::class).label)
    }

    @Test
    fun `Given failed initial bundle loading when retrying then publish only a complete set of models`() {
        var attempts = 0
        val calls = mutableMapOf<Locale, Int>()
        val firstBundle = bundle(Locales.EN, calls)
        val bundle = object : Bundle() {
            override val locale = Locales.EN

            override fun register(translations: BundleScope) {
                attempts += 1
                translations.add(Strings("Attempt $attempts"))
                check(attempts != 1) { "Loading failed" }
                translations.add(SecondaryStrings("Complete"))
            }
        }
        val resolver = Resolver(Registry(listOf(firstBundle, bundle)))

        assertFailsWith<IllegalStateException> { resolver.getTranslationClass(Strings::class) }

        assertEquals("Complete", resolver.getTranslationClass(SecondaryStrings::class).label)
        assertEquals("Attempt 2", resolver.getTranslationClass(Strings::class).label)
        assertEquals(2, attempts)
        assertEquals(1, calls[Locales.EN])
    }

    @Test
    fun `Given failed bundle restoration when retrying another type then discard partially created models`() {
        var attempts = 0
        val bundle = object : Bundle() {
            override val locale = Locales.EN

            override fun register(translations: BundleScope) {
                attempts += 1
                translations.add(Strings("Attempt $attempts"))
                check(attempts != 2) { "Restoration failed" }
                translations.add(SecondaryStrings("Complete"))
            }
        }
        val resolver = Resolver(Registry(listOf(bundle)))
        resolver.getTranslationClass(Strings::class)
        resolver.setCurrentLocale(Locales.PL)

        assertFailsWith<IllegalStateException> { resolver.getTranslationClass(SecondaryStrings::class) }

        assertEquals("Attempt 3", resolver.getTranslationClass(Strings::class).label)
        assertEquals("Complete", resolver.getTranslationClass(SecondaryStrings::class).label)
        assertEquals(3, attempts)
    }

    @Test
    fun `Given missing model during restoration when retrying then keep all owned models released`() {
        var attempts = 0
        val bundle = object : Bundle() {
            override val locale = Locales.EN

            override fun register(translations: BundleScope) {
                attempts += 1
                translations.add(Strings("Attempt $attempts"))
                if (attempts != 2) {
                    translations.add(SecondaryStrings("Complete"))
                }
            }
        }
        val resolver = Resolver(Registry(listOf(bundle)))
        resolver.getTranslationClass(Strings::class)
        resolver.setCurrentLocale(Locales.PL)

        assertFailsWith<IllegalStateException> { resolver.getTranslationClass(Strings::class) }

        assertEquals("Attempt 3", resolver.getTranslationClass(Strings::class).label)
        assertEquals("Complete", resolver.getTranslationClass(SecondaryStrings::class).label)
        assertEquals(3, attempts)
    }

    @Test
    fun `Given shared bundle when loading and reloading in separate registries then create independent models`() {
        val bundle = object : Bundle() {
            override val locale = Locales.EN

            override fun register(translations: BundleScope) {
                translations.add(Strings("English"))
            }
        }
        val firstRegistry = Registry(listOf(bundle))
        val secondRegistry = Registry(listOf(bundle))
        val resolver = Resolver(firstRegistry)
        val first = resolver.getTranslationClass(Strings::class)
        val second = secondRegistry.getTranslationClass(Strings::class, listOf(Locales.EN))
        assertNotSame(first, second)

        resolver.setCurrentLocale(Locales.PL)
        val restored = resolver.getTranslationClass(Strings::class)
        assertEquals("English", restored.label)
        assertNotSame(first, restored)
        assertSame(second, secondRegistry.getTranslationClass(Strings::class, listOf(Locales.EN)))
    }

    private fun bundle(locale: Locale, calls: MutableMap<Locale, Int>) = object : Bundle() {
        override val locale = locale

        override fun register(translations: BundleScope) {
            calls[locale] = (calls[locale] ?: 0) + 1
            translations.add(Strings(locale.languageTag))
            translations.add(SecondaryStrings(locale.languageTag))
        }
    }

    private data class Strings(val label: String)

    private data class SecondaryStrings(val label: String)
}
