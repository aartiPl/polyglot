package net.igsoft.polyglot.translation

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.messageContains
import kotlin.test.Test
import kotlin.test.assertFailsWith
import net.igsoft.polyglot.locale.Locale
import net.igsoft.polyglot.locale.Locales

class ResolverTest {

    @Test
    fun `Given Portuguese regional mapper when resolving translations then override selected fields and preserve base in either lookup order`() {
        // Given
        val portuguese = Locale.of("pt")
        val brazilianPortuguese = Locale.of("pt-BR")
        val baseBundle = object : Bundle() {
            override val locale = portuguese

            override fun register(translations: BundleScope) {
                translations.add(TestStrings(name = "Nome", surname = "Apelido"))
            }
        }
        val mapper = object : Module() {
            override fun register(registration: ModuleScope) {
                registration.registerMapper(
                    TestStrings::class,
                    TestStrings::class,
                    locale = brazilianPortuguese,
                    sourceLocale = portuguese,
                ) { it.copy(surname = "Sobrenome") }
            }
        }

        listOf(listOf(baseBundle, mapper), listOf(mapper, baseBundle)).forEach { registrationOrder ->
            listOf(
                listOf(portuguese, brazilianPortuguese),
                listOf(brazilianPortuguese, portuguese),
            ).forEach { lookupOrder ->
                val resolver = Resolver(Registry(registrationOrder))

                // When
                lookupOrder.forEach { resolver.getTranslationClass(TestStrings::class, it) }
                val base = resolver.getTranslationClass(TestStrings::class, portuguese)
                val regional = resolver.getTranslationClass(TestStrings::class, brazilianPortuguese)

                // Then
                assertThat(base.name).isEqualTo("Nome")
                assertThat(base.surname).isEqualTo("Apelido")
                assertThat(regional.name).isEqualTo("Nome")
                assertThat(regional.surname).isEqualTo("Sobrenome")
                assertThat(resolver.registeredLocales()).isEqualTo(setOf(portuguese, brazilianPortuguese))
            }
        }
    }

    @Test
    fun `Given registered bundles when resolving translations then instantiate only requested locale lazily`() {
        // Given
        val counters = TranslationCounters()
        val polyglotResolver = Resolver(
            Registry(
                listOf(
                    LazyRepositoryAggregate(counters)
                )
            )
        )

        // When
        val englishTranslation = polyglotResolver.getTranslationClass(TestStrings::class)
        val englishTranslationAgain = polyglotResolver.getTranslationClass(TestStrings::class)

        // Then
        assertThat(englishTranslation.surname).isEqualTo("Surname")
        assertThat(englishTranslationAgain.surname).isEqualTo("Surname")
        assertThat(counters.englishBundleRegistrationCount).isEqualTo(1)
        assertThat(counters.polishBundleRegistrationCount).isEqualTo(0)

        // When
        val polishTranslation = polyglotResolver.getTranslationClass(TestStrings::class, localePl)
        val polishTranslationAgain = polyglotResolver.getTranslationClass(TestStrings::class, localePl)

        // Then
        assertThat(polishTranslation.surname).isEqualTo("Nazwisko")
        assertThat(polishTranslationAgain.surname).isEqualTo("Nazwisko")
        assertThat(counters.englishBundleRegistrationCount).isEqualTo(1)
        assertThat(counters.polishBundleRegistrationCount).isEqualTo(1)
    }

    @Test
    fun `Given registered bundles when resolving translations then return requested language`() {
        // Given
        val repositoryTranslations = RepositoryAggregate()
        val polyglotRegistry = Registry()
        polyglotRegistry.registerDefinition(repositoryTranslations)
        val polyglotResolver = Resolver(polyglotRegistry)

        // When
        val englishTranslation = polyglotResolver.getTranslationClass(TestStrings::class)
        val polishTranslation = polyglotResolver.getTranslationClass(TestStrings::class, localePl)

        // Then
        assertThat(englishTranslation.surname).isEqualTo("Surname")
        assertThat(polishTranslation.surname).isEqualTo("Nazwisko")
    }

    @Test
    fun `Given registered mapper when resolving target translations then map source translations`() {
        // Given
        val polyglotResolver = Resolver(
            Registry(
                listOf(
                    MapperAggregate(
                        CommonBundlePl(),
                        CommonBundleEn(),
                        SearchFieldMapper(),
                    )
                )
            )
        )

        // When
        val englishTranslation = polyglotResolver.getTranslationClass(SearchFieldStrings::class, Locales.EN)
        val polishTranslation = polyglotResolver.getTranslationClass(SearchFieldStrings::class, localePl)

        // Then
        assertThat(englishTranslation.clear).isEqualTo("Clear")
        assertThat(englishTranslation.back).isEqualTo("Back")
        assertThat(polishTranslation.clear).isEqualTo("Wyczyść")
        assertThat(polishTranslation.back).isEqualTo("Wróć")
    }

    @Test
    fun `Given direct target bundle and mapper when resolving target translations then mapper has priority`() {
        // Given
        val polyglotResolver = Resolver(
            Registry(
                listOf(
                    MapperAggregate(
                        CommonBundleEn(),
                        SearchFieldMapper(),
                        SearchFieldBundleEn(),
                    )
                )
            )
        )

        // When
        val englishTranslation = polyglotResolver.getTranslationClass(SearchFieldStrings::class, Locales.EN)

        // Then
        assertThat(englishTranslation.clear).isEqualTo("Clear")
        assertThat(englishTranslation.back).isEqualTo("Back")
    }

    @Test
    fun `Given mapper with fallback source language when resolving current translation then fallback mapper is used`() {
        // Given
        val polyglotResolver = Resolver(
            Registry(
                listOf(
                    MapperAggregate(
                        CommonBundleEn(),
                        SearchFieldMapper(),
                    )
                )
            )
        )
        polyglotResolver.setCurrentLocale(localePl)

        // When
        val translation = polyglotResolver.getTranslationClass(SearchFieldStrings::class)

        // Then
        assertThat(translation.clear).isEqualTo("Clear")
        assertThat(translation.back).isEqualTo("Back")
    }

    @Test
    fun `Given language bundle when resolving regional locale then fallback to language locale`() {
        // Given
        val polyglotResolver = Resolver(
            Registry(
                listOf(
                    MapperAggregate(
                        CommonBundlePl(),
                        SearchFieldMapper(),
                    )
                )
            )
        )

        // When
        val translation = polyglotResolver.getTranslationClass(SearchFieldStrings::class, localePlPl)

        // Then
        assertThat(translation.clear).isEqualTo("Wyczyść")
        assertThat(translation.back).isEqualTo("Wróć")
    }

    @Test
    fun `Given exact locale bundle when resolving regional locale then prefer exact locale over language fallback`() {
        // Given
        val polyglotResolver = Resolver(
            Registry(
                listOf(
                    MapperAggregate(
                        CommonBundlePl(),
                        SearchFieldMapper(),
                        SearchFieldBundlePlPl(),
                    )
                )
            )
        )

        // When
        val translation = polyglotResolver.getTranslationClass(SearchFieldStrings::class, localePlPl)

        // Then
        assertThat(translation.clear).isEqualTo("Wyczyść (Polska)")
        assertThat(translation.back).isEqualTo("Wróć (Polska)")
    }

    @Test
    fun `Given registered bundles when listing supported locales then return normalized locales`() {
        // Given
        val polyglotResolver = Resolver(
            Registry(
                listOf(
                    RepositoryAggregate(),
                    RegionalRepositoryAggregate(),
                )
            )
        )

        // When
        val locales = polyglotResolver.registeredLocales()

        // Then
        assertThat(locales).isEqualTo(
            linkedSetOf(
                Locales.PL,
                Locales.EN,
                Locales.EN_US,
            )
        )
    }

    @Test
    fun `Given non standard locale tag when resolving translations then fallback to raw trimmed locale tag`() {
        // Given
        val locale = Locale.of("qaa-QM")
        val polyglotResolver = Resolver(
            Registry(
                listOf(
                    NonStandardLocaleAggregate(locale),
                )
            )
        )

        // When
        val translation = polyglotResolver.getTranslationClass(TestStrings::class, locale)
        val locales = polyglotResolver.registeredLocales()

        // Then
        assertThat(translation.surname).isEqualTo("Non standard")
        assertThat(locales).isEqualTo(setOf(Locale.of("qaa-QM")))
    }

    @Test
    fun `Given missing bundle when resolving translation then throw detailed error`() {
        // Given
        val polyglotResolver = Resolver(Registry())

        // When
        val exception = assertFailsWith<IllegalStateException> {
            polyglotResolver.getTranslationClass(TestStrings::class, localePl)
        }

        // Then
        assertThat(exception).messageContains("Can not find translations for locales: [pl, en]")
        assertThat(exception).messageContains("[translationClass='TestStrings']")
    }

    @Test
    fun `Given cyclic mappers when resolving translation then report dependency path`() {
        // Given
        val polyglotResolver = Resolver(
            Registry(
                listOf(
                    CyclicMapperAggregate(),
                )
            )
        )

        // When
        val exception = assertFailsWith<IllegalStateException> {
            polyglotResolver.getTranslationClass(RecursiveTargetStrings::class, Locales.EN)
        }

        // Then
        assertThat(exception).messageContains("Cyclic translation dependency")
        assertThat(exception).messageContains("RecursiveTargetStrings[en, BASE]")
        assertThat(exception).messageContains("RecursiveSourceStrings[en, BASE]")
    }
}

private data class TestStrings(
    val name: String,
    val surname: String,
)

private data class SearchFieldStrings(
    val clear: String,
    val back: String,
)

private data class CommonStrings(
    val clear: String,
    val back: String,
)

private data class SecondaryStrings(
    val label: String,
)

private data class RecursiveSourceStrings(
    val value: String,
)

private data class RecursiveTargetStrings(
    val value: String,
)

private class RepositoryAggregate : Module() {
    override fun register(registration: ModuleScope) {
        registration.registerDefinition(RepositoryBundlePl())
        registration.registerDefinition(RepositoryBundleEn())
    }
}

private class RegionalRepositoryAggregate : Module() {
    override fun register(registration: ModuleScope) {
        registration.registerDefinition(RegionalRepositoryBundleUs())
    }
}

private class NonStandardLocaleAggregate(
    private val locale: Locale,
) : Module() {
    override fun register(registration: ModuleScope) {
        registration.registerDefinition(NonStandardLocaleBundle(locale))
    }
}

private class MapperAggregate(
    private vararg val definitions: Definition,
) : Module() {
    override fun register(registration: ModuleScope) {
        definitions.forEach(registration::registerDefinition)
    }
}

private class RepositoryBundlePl : Bundle() {
    override val locale: Locale = localePl

    override fun register(translations: BundleScope) {
        translations.add(
            TestStrings(
                name = "Imię",
                surname = "Nazwisko",
            )
        )
    }
}

private class RepositoryBundleEn : Bundle() {
    override val locale: Locale = Locales.EN

    override fun register(translations: BundleScope) {
        translations.add(
            TestStrings(
                name = "Name",
                surname = "Surname",
            )
        )
    }
}

private class RegionalRepositoryBundleUs : Bundle() {
    override val locale: Locale = Locales.EN_US

    override fun register(translations: BundleScope) {
        translations.add(
            SecondaryStrings(
                label = "Secondary US",
            )
        )
    }
}

private class NonStandardLocaleBundle(
    override val locale: Locale,
) : Bundle() {
    override fun register(translations: BundleScope) {
        translations.add(
            TestStrings(
                name = "Non standard",
                surname = "Non standard",
            )
        )
    }
}

private class SearchFieldBundleEn : Bundle() {
    override val locale: Locale = Locales.EN

    override fun register(translations: BundleScope) {
        translations.add(
            SearchFieldStrings(
                clear = "Dismiss",
                back = "Navigate up",
            )
        )
    }
}

private class CommonBundlePl : Bundle() {
    override val locale: Locale = localePl

    override fun register(translations: BundleScope) {
        translations.add(
            CommonStrings(
                clear = "Wyczyść",
                back = "Wróć",
            )
        )
    }
}

private class CommonBundleEn : Bundle() {
    override val locale: Locale = Locales.EN

    override fun register(translations: BundleScope) {
        translations.add(
            CommonStrings(
                clear = "Clear",
                back = "Back",
            )
        )
    }
}

private class SearchFieldMapper : Module() {
    override fun register(registration: ModuleScope) {
        registration.registerMapper(CommonStrings::class, SearchFieldStrings::class) { strings ->
            SearchFieldStrings(
                clear = strings.clear,
                back = strings.back,
            )
        }
    }
}

private class CyclicMapperAggregate : Module() {
    override fun register(registration: ModuleScope) {
        registration.registerDefinition(RecursiveSourceMapper())
        registration.registerDefinition(RecursiveTargetMapper())
    }
}

private class RecursiveSourceMapper : Module() {
    override fun register(registration: ModuleScope) {
        registration.registerMapper(RecursiveTargetStrings::class, RecursiveSourceStrings::class) { strings ->
            RecursiveSourceStrings(strings.value)
        }
    }
}

private class RecursiveTargetMapper : Module() {
    override fun register(registration: ModuleScope) {
        registration.registerMapper(RecursiveSourceStrings::class, RecursiveTargetStrings::class) { strings ->
            RecursiveTargetStrings(strings.value)
        }
    }
}

private class SearchFieldBundlePlPl : Bundle() {
    override val locale: Locale = localePlPl

    override fun register(translations: BundleScope) {
        translations.add(
            SearchFieldStrings(
                clear = "Wyczyść (Polska)",
                back = "Wróć (Polska)",
            )
        )
    }
}

private class LazyRepositoryAggregate(
    private val counters: TranslationCounters,
) : Module() {
    override fun register(registration: ModuleScope) {
        registration.registerDefinition(LazyRepositoryBundleEn(counters))
        registration.registerDefinition(LazyRepositoryBundlePl(counters))
    }
}

private class LazyRepositoryBundleEn(
    private val counters: TranslationCounters,
) : Bundle() {
    override val locale: Locale = Locales.EN

    override fun register(translations: BundleScope) {
        counters.englishBundleRegistrationCount += 1
        translations.add(
            TestStrings(
                name = "Name",
                surname = "Surname",
            )
        )
        translations.add(
            SecondaryStrings(label = "Secondary")
        )
    }
}

private class LazyRepositoryBundlePl(
    private val counters: TranslationCounters,
) : Bundle() {
    override val locale: Locale = localePl

    override fun register(translations: BundleScope) {
        counters.polishBundleRegistrationCount += 1
        translations.add(
            TestStrings(
                name = "Imię",
                surname = "Nazwisko",
            )
        )
        translations.add(
            SecondaryStrings(label = "Pomocniczy")
        )
    }
}

private class TranslationCounters(
    var englishBundleRegistrationCount: Int = 0,
    var polishBundleRegistrationCount: Int = 0,
)

private val localePl: Locale = Locales.PL
private val localePlPl: Locale = Locales.PL_PL
