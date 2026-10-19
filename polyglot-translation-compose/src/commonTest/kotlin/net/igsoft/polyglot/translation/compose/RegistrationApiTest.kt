package net.igsoft.polyglot.translation.compose

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import net.igsoft.polyglot.locale.Locales
import net.igsoft.polyglot.translation.Bundle
import net.igsoft.polyglot.translation.BundleScope
import net.igsoft.polyglot.translation.Definition
import net.igsoft.polyglot.translation.Module
import net.igsoft.polyglot.translation.ModuleScope
import net.igsoft.polyglot.translation.Registry
import net.igsoft.polyglot.translation.Resolver

class RegistrationApiTest {
    @Test
    fun `Given declarations outside core when registering an aggregate then collect and map translations lazily`() {
        var bundleCalls = 0
        val bundle = object : Bundle() {
            override val locale = Locales.EN

            override fun register(translations: BundleScope) {
                bundleCalls += 1
                translations.add(CommonStrings("Search"))
            }
        }
        val mapper = object : Module() {
            override fun register(registration: ModuleScope) {
                registration.registerMapper<CommonStrings, SearchStrings> { common ->
                    SearchStrings(common.search)
                }
            }
        }
        val definitions: List<Definition> = listOf(bundle, mapper)
        val aggregate = object : Module() {
            override fun register(registration: ModuleScope) {
                definitions.forEach(registration::registerBundle)
            }
        }
        val resolver = Resolver(Registry(listOf(aggregate)))
        assertEquals(0, bundleCalls)

        assertEquals("Search", resolver.getTranslationClass<SearchStrings>().placeholder)
        assertSame(resolver.getTranslationClass<SearchStrings>(), resolver.getTranslationClass(SearchStrings::class))
        assertEquals(1, bundleCalls)

        resolver.setCurrentLocale(Locales.PL)
        assertEquals("Search", resolver.getTranslationClass<SearchStrings>().placeholder)
        assertEquals(2, bundleCalls)
    }

    @Test
    fun `Given generic direct registrations when resolving by declared type then load lazily and share cache with explicit API`() {
        var factoryCalls = 0
        val registry = Registry()
        registry.registerTranslationClass<SearchLabels>(Locales.EN) {
            factoryCalls += 1
            CommonStrings("Search")
        }
        registry.registerTranslationClass<SearchLabels>(Locales.PL) {
            factoryCalls += 1
            CommonStrings("Szukaj")
        }
        val resolver = Resolver(registry)
        assertEquals(0, factoryCalls)

        val english = resolver.getTranslationClass<SearchLabels>()
        val polish = resolver.getTranslationClass<SearchLabels>(Locales.PL)

        assertEquals("Search", english.search)
        assertEquals("Szukaj", polish.search)
        assertSame(english, resolver.getTranslationClass(SearchLabels::class))
        assertSame(polish, resolver.getTranslationClass(SearchLabels::class, Locales.PL))
        assertEquals(2, factoryCalls)

        registry.registerTranslationClass<SearchLabels>(Locales.EN) { CommonStrings("Updated") }
        assertEquals("Updated", resolver.getTranslationClass<SearchLabels>().search)
    }

    @Test
    fun `Given generic scoped mapper declarations when resolving then apply inheritance and overrides across locales`() {
        val mapper = object : Module() {
            override fun register(registration: ModuleScope) {
                registration.registerMapper<CommonStrings, SearchStrings>(
                    locale = Locales.EN,
                    sourceLocale = Locales.PL,
                ) { common -> SearchStrings(common.search) }
                registration.registerMapper<SearchStrings>(
                    locale = Locales.EN_US,
                    sourceLocale = Locales.EN,
                ) { search -> search.copy(placeholder = "${search.placeholder} US") }
                registration.registerMapper<CommonStrings, SearchStrings>(
                    locale = Locales.EN_US,
                    sourceLocale = Locales.PL,
                ) { common, search -> search.copy(placeholder = "${search.placeholder} / ${common.search}") }
            }
        }
        val registry = Registry(listOf(mapper))
        registry.registerTranslationClass<CommonStrings>(Locales.PL) { CommonStrings("Szukaj") }
        val resolver = Resolver(registry)

        assertEquals("Szukaj US / Szukaj", resolver.getTranslationClass<SearchStrings>(Locales.EN_US).placeholder)
        assertEquals("Szukaj", resolver.getTranslationClass<SearchStrings>(Locales.EN).placeholder)
    }

    @Test
    fun `Given generic registry mappers when resolving then apply base and override and refresh changed sources`() {
        val registry = Registry()
        registry.registerTranslationClass<CommonStrings>(Locales.EN) { CommonStrings("Search") }
        registry.registerMapper<CommonStrings, SearchStrings> { common -> SearchStrings(common.search) }
        registry.registerMapper<SearchStrings>(
            locale = Locales.EN_US,
            sourceLocale = Locales.EN,
        ) { search -> search.copy(placeholder = "${search.placeholder} US") }
        registry.registerMapper<CommonStrings, SearchStrings> { common, search ->
            search.copy(placeholder = "${search.placeholder} / ${common.search}")
        }
        val resolver = Resolver(registry)

        assertEquals("Search US / Search", resolver.getTranslationClass<SearchStrings>(Locales.EN_US).placeholder)

        registry.registerTranslationClass<CommonStrings>(Locales.EN) { CommonStrings("Updated") }

        assertEquals("Updated US / Updated", resolver.getTranslationClass<SearchStrings>(Locales.EN_US).placeholder)
    }

    private interface SearchLabels {
        val search: String
    }

    private data class CommonStrings(override val search: String) : SearchLabels

    private data class SearchStrings(val placeholder: String)
}
