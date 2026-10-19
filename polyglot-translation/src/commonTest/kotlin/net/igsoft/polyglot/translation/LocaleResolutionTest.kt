package net.igsoft.polyglot.translation

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlin.test.assertSame
import net.igsoft.polyglot.locale.Locale
import net.igsoft.polyglot.locale.Locales

class LocaleResolutionTest {

    @Test
    fun `Given predefined registration when resolving with parsed locale then share registry key and cached result`() {
        // Given
        var creations = 0
        val registry = Registry()
        registry.registerTranslationClass(Locales.EN_US, Strings::class) {
            creations += 1
            Strings("Hello")
        }
        val resolver = Resolver(registry)

        // When
        val predefined = resolver.getTranslationClass(Strings::class, Locales.EN_US)
        val parsed = resolver.getTranslationClass(Strings::class, Locale.of(" EN-us "))

        // Then
        assertSame(predefined, parsed)
        assertThat(creations).isEqualTo(1)
        assertThat(resolver.registeredLocales()).isEqualTo(setOf(Locale.of("en-US")))
    }

    @Test
    fun `Given predefined regional mapper when resolving parsed locale then preserve base and regional keys`() {
        // Given
        val bundle = object : Bundle() {
            override val locale = Locales.PT

            override fun register(translations: BundleScope) {
                translations.add(Strings("Guardar"))
            }
        }
        val mapper = object : Module() {
            override fun register(registration: ModuleScope) {
                registration.registerMapper(
                    Strings::class,
                    Strings::class,
                    locale = Locales.PT_BR,
                    sourceLocale = Locales.PT,
                ) {
                    it.copy(label = "Salvar")
                }
            }
        }
        val resolver = Resolver(Registry(listOf(bundle, mapper)))

        // When
        val regional = resolver.getTranslationClass(Strings::class, Locale.of(" PT-br "))
        val base = resolver.getTranslationClass(Strings::class, Locale.of("pt"))

        // Then
        assertThat(regional.label).isEqualTo("Salvar")
        assertThat(base.label).isEqualTo("Guardar")
        assertThat(resolver.registeredLocales()).isEqualTo(setOf(Locales.PT, Locales.PT_BR))
    }

    @Test
    fun `Given predefined script locale when resolving region then fallback to matching script`() {
        // Given
        val registry = Registry()
        registry.registerTranslationClass(Locales.ZH_HANS, Strings::class) { Strings("Simplified") }
        registry.registerTranslationClass(Locales.ZH_HANT, Strings::class) { Strings("Traditional") }
        val resolver = Resolver(registry)

        // When
        val simplified = resolver.getTranslationClass(Strings::class, Locales.ZH_HANS_CN)
        val traditional = resolver.getTranslationClass(Strings::class, Locale.of("zh-hant-tw"))

        // Then
        assertThat(simplified.label).isEqualTo("Simplified")
        assertThat(traditional.label).isEqualTo("Traditional")
        assertThat(resolver.registeredLocales()).isEqualTo(setOf(Locales.ZH_HANS, Locales.ZH_HANT))
    }

    private data class Strings(
        val label: String,
    )
}
