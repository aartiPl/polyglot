package net.igsoft.polyglot.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class MsgTranslationsTest {
    @Test
    fun `Given explicit model type when collecting translations then resolve under that type only`() {
        val instance = DefaultStrings("English")
        val bundle = object : MsgBundle() {
            override val locale = MsgLocales.EN

            override fun register(translations: MsgTranslations) {
                translations.add(instance, Strings::class)
            }
        }
        val resolver = PolyglotResolver(PolyglotRegistry(listOf(bundle)))

        assertSame(instance, resolver.getTranslationClass(Strings::class))
        assertFailsWith<IllegalStateException> { resolver.getTranslationClass(DefaultStrings::class) }
    }

    @Test
    fun `Given repeated model type in a bundle when collecting then publish the last model`() {
        val bundle = object : MsgBundle() {
            override val locale = MsgLocales.EN

            override fun register(translations: MsgTranslations) {
                translations.add(DefaultStrings("First"))
                translations.add(DefaultStrings("Last"))
            }
        }
        val resolver = PolyglotResolver(PolyglotRegistry(listOf(bundle)))

        assertEquals("Last", resolver.getTranslationClass(DefaultStrings::class).label)
        resolver.setCurrentLocale(MsgLocales.PL)
        assertEquals("Last", resolver.getTranslationClass(DefaultStrings::class).label)
    }

    private interface Strings {
        val label: String
    }

    private data class DefaultStrings(override val label: String) : Strings
}
