package net.igsoft.polyglot.core

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlin.test.assertSame

class MsgLocalesTest {

    @Test
    fun `Given predefined locale when reading repeatedly then reuse normalized language tag`() {
        // Given
        val firstTag = MsgLocales.EN_US.languageTag

        // When
        val secondTag = MsgLocales.EN_US.languageTag

        // Then
        assertSame(firstTag, secondTag)
    }

    @Test
    fun `Given predefined registration when resolving with parsed locale then share registry key and cached result`() {
        // Given
        var creations = 0
        val registry = PolyglotRegistry()
        registry.registerTranslationClass(MsgLocales.EN_US, Strings::class) {
            creations += 1
            Strings("Hello")
        }
        val resolver = PolyglotResolver(registry)

        // When
        val predefined = resolver.getTranslationClass(Strings::class, MsgLocales.EN_US)
        val parsed = resolver.getTranslationClass(Strings::class, MsgLocale.of(" EN-us "))

        // Then
        assertSame(predefined, parsed)
        assertThat(creations).isEqualTo(1)
        assertThat(resolver.registeredLocales()).isEqualTo(setOf(MsgLocale.of("en-US")))
    }

    @Test
    fun `Given predefined regional mapper when resolving parsed locale then preserve base and regional keys`() {
        // Given
        val bundle = object : MsgBundle() {
            override val locale = MsgLocales.PT

            override fun register() {
                add(Strings("Guardar"))
            }
        }
        val mapper = object : MsgMapper() {
            override fun register() {
                add<Strings>(locale = MsgLocales.PT_BR, sourceLocale = MsgLocales.PT) {
                    it.copy(label = "Salvar")
                }
            }
        }
        val resolver = PolyglotResolver(PolyglotRegistry(listOf(bundle, mapper)))

        // When
        val regional = resolver.getTranslationClass(Strings::class, MsgLocale.of(" PT-br "))
        val base = resolver.getTranslationClass(Strings::class, MsgLocale.of("pt"))

        // Then
        assertThat(regional.label).isEqualTo("Salvar")
        assertThat(base.label).isEqualTo("Guardar")
        assertThat(resolver.registeredLocales()).isEqualTo(setOf(MsgLocales.PT, MsgLocales.PT_BR))
    }

    @Test
    fun `Given predefined script locale when resolving region then fallback to matching script`() {
        // Given
        val registry = PolyglotRegistry()
        registry.registerTranslationClass(MsgLocales.ZH_HANS, Strings::class) { Strings("Simplified") }
        registry.registerTranslationClass(MsgLocales.ZH_HANT, Strings::class) { Strings("Traditional") }
        val resolver = PolyglotResolver(registry)

        // When
        val simplified = resolver.getTranslationClass(Strings::class, MsgLocales.ZH_HANS_CN)
        val traditional = resolver.getTranslationClass(Strings::class, MsgLocale.of("zh-hant-tw"))

        // Then
        assertThat(simplified.label).isEqualTo("Simplified")
        assertThat(traditional.label).isEqualTo("Traditional")
        assertThat(resolver.registeredLocales()).isEqualTo(setOf(MsgLocales.ZH_HANS, MsgLocales.ZH_HANT))
    }

    @Test
    fun `Given numeric region constant when looking up parsed map key then match normalized locale`() {
        // Given
        val translations = mapOf(MsgLocales.ES_419 to "Latin America")

        // When
        val translation = translations[MsgLocale.of(" ES-419 ")]

        // Then
        assertThat(translation).isEqualTo("Latin America")
    }

    private data class Strings(
        val label: String,
    )
}
