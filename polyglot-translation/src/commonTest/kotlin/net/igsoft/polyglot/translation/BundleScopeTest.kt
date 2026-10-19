package net.igsoft.polyglot.translation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import net.igsoft.polyglot.locale.Locales

class BundleScopeTest {
    @Test
    fun `Given explicit model type when collecting translations then resolve under that type only`() {
        val instance = DefaultStrings("English")
        val bundle = object : Bundle() {
            override val locale = Locales.EN

            override fun register(translations: BundleScope) {
                translations.add(instance, Strings::class)
            }
        }
        val resolver = Resolver(Registry(listOf(bundle)))

        assertSame(instance, resolver.getTranslationClass(Strings::class))
        assertFailsWith<IllegalStateException> { resolver.getTranslationClass(DefaultStrings::class) }
    }

    @Test
    fun `Given repeated model type in a bundle when collecting then publish the last model`() {
        val bundle = object : Bundle() {
            override val locale = Locales.EN

            override fun register(translations: BundleScope) {
                translations.add(DefaultStrings("First"))
                translations.add(DefaultStrings("Last"))
            }
        }
        val resolver = Resolver(Registry(listOf(bundle)))

        assertEquals("Last", resolver.getTranslationClass(DefaultStrings::class).label)
        resolver.setCurrentLocale(Locales.PL)
        assertEquals("Last", resolver.getTranslationClass(DefaultStrings::class).label)
    }

    private interface Strings {
        val label: String
    }

    private data class DefaultStrings(override val label: String) : Strings
}
