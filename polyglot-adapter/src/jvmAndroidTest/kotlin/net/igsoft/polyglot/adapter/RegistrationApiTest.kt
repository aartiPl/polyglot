package net.igsoft.polyglot.adapter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import net.igsoft.polyglot.core.MsgAggregate
import net.igsoft.polyglot.core.MsgBundle
import net.igsoft.polyglot.core.MsgDefinition
import net.igsoft.polyglot.core.MsgLocales
import net.igsoft.polyglot.core.MsgRegistration
import net.igsoft.polyglot.core.MsgTranslations
import net.igsoft.polyglot.core.PolyglotRegistry
import net.igsoft.polyglot.core.PolyglotResolver

class RegistrationApiTest {
    @Test
    fun `Given declarations outside core when registering an aggregate then collect and map translations lazily`() {
        var bundleCalls = 0
        val bundle = object : MsgBundle() {
            override val locale = MsgLocales.EN

            override fun register(translations: MsgTranslations) {
                bundleCalls += 1
                translations.add(CommonStrings("Search"))
            }
        }
        val mapper = object : MsgAggregate() {
            override fun register(registration: MsgRegistration) {
                registration.registerMapper<CommonStrings, SearchStrings> { common ->
                    SearchStrings(common.search)
                }
            }
        }
        val definitions: List<MsgDefinition> = listOf(bundle, mapper)
        val aggregate = object : MsgAggregate() {
            override fun register(registration: MsgRegistration) {
                definitions.forEach(registration::registerBundle)
            }
        }
        val resolver = PolyglotResolver(PolyglotRegistry(listOf(aggregate)))
        assertEquals(0, bundleCalls)

        assertEquals("Search", resolver.getTranslationClass<SearchStrings>().placeholder)
        assertSame(resolver.getTranslationClass<SearchStrings>(), resolver.getTranslationClass(SearchStrings::class))
        assertEquals(1, bundleCalls)

        resolver.setCurrentLocale(MsgLocales.PL)
        assertEquals("Search", resolver.getTranslationClass<SearchStrings>().placeholder)
        assertEquals(2, bundleCalls)
    }

    @Test
    fun `Given generic direct registrations when resolving by declared type then load lazily and share cache with explicit API`() {
        var factoryCalls = 0
        val registry = PolyglotRegistry()
        registry.registerTranslationClass<SearchLabels>(MsgLocales.EN) {
            factoryCalls += 1
            CommonStrings("Search")
        }
        registry.registerTranslationClass<SearchLabels>(MsgLocales.PL) {
            factoryCalls += 1
            CommonStrings("Szukaj")
        }
        val resolver = PolyglotResolver(registry)
        assertEquals(0, factoryCalls)

        val english = resolver.getTranslationClass<SearchLabels>()
        val polish = resolver.getTranslationClass<SearchLabels>(MsgLocales.PL)

        assertEquals("Search", english.search)
        assertEquals("Szukaj", polish.search)
        assertSame(english, resolver.getTranslationClass(SearchLabels::class))
        assertSame(polish, resolver.getTranslationClass(SearchLabels::class, MsgLocales.PL))
        assertEquals(2, factoryCalls)

        registry.registerTranslationClass<SearchLabels>(MsgLocales.EN) { CommonStrings("Updated") }
        assertEquals("Updated", resolver.getTranslationClass<SearchLabels>().search)
    }

    @Test
    fun `Given generic scoped mapper declarations when resolving then apply inheritance and overrides across locales`() {
        val mapper = object : MsgAggregate() {
            override fun register(registration: MsgRegistration) {
                registration.registerMapper<CommonStrings, SearchStrings>(
                    locale = MsgLocales.EN,
                    sourceLocale = MsgLocales.PL,
                ) { common -> SearchStrings(common.search) }
                registration.registerMapper<SearchStrings>(
                    locale = MsgLocales.EN_US,
                    sourceLocale = MsgLocales.EN,
                ) { search -> search.copy(placeholder = "${search.placeholder} US") }
                registration.registerMapper<CommonStrings, SearchStrings>(
                    locale = MsgLocales.EN_US,
                    sourceLocale = MsgLocales.PL,
                ) { common, search -> search.copy(placeholder = "${search.placeholder} / ${common.search}") }
            }
        }
        val registry = PolyglotRegistry(listOf(mapper))
        registry.registerTranslationClass<CommonStrings>(MsgLocales.PL) { CommonStrings("Szukaj") }
        val resolver = PolyglotResolver(registry)

        assertEquals("Szukaj US / Szukaj", resolver.getTranslationClass<SearchStrings>(MsgLocales.EN_US).placeholder)
        assertEquals("Szukaj", resolver.getTranslationClass<SearchStrings>(MsgLocales.EN).placeholder)
    }

    @Test
    fun `Given generic registry mappers when resolving then apply base and override and refresh changed sources`() {
        val registry = PolyglotRegistry()
        registry.registerTranslationClass<CommonStrings>(MsgLocales.EN) { CommonStrings("Search") }
        registry.registerMapper<CommonStrings, SearchStrings> { common -> SearchStrings(common.search) }
        registry.registerMapper<SearchStrings>(
            locale = MsgLocales.EN_US,
            sourceLocale = MsgLocales.EN,
        ) { search -> search.copy(placeholder = "${search.placeholder} US") }
        registry.registerMapper<CommonStrings, SearchStrings> { common, search ->
            search.copy(placeholder = "${search.placeholder} / ${common.search}")
        }
        val resolver = PolyglotResolver(registry)

        assertEquals("Search US / Search", resolver.getTranslationClass<SearchStrings>(MsgLocales.EN_US).placeholder)

        registry.registerTranslationClass<CommonStrings>(MsgLocales.EN) { CommonStrings("Updated") }

        assertEquals("Updated US / Updated", resolver.getTranslationClass<SearchStrings>(MsgLocales.EN_US).placeholder)
    }

    private interface SearchLabels {
        val search: String
    }

    private data class CommonStrings(override val search: String) : SearchLabels

    private data class SearchStrings(val placeholder: String)
}
