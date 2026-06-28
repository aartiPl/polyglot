package net.igsoft.polyglot.core

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.messageContains
import kotlin.test.Test
import kotlin.test.assertFailsWith

class PolyglotResolverTest {

    @Test
    fun `Given registered bundles when resolving translations then instantiate only requested locale lazily`() {
        // Given
        val counters = TranslationCounters()
        val polyglotResolver = PolyglotResolver(
            PolyglotRegistry(
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
        val polyglotRegistry = PolyglotRegistry()
        polyglotRegistry.registerBundle(repositoryTranslations)
        val polyglotResolver = PolyglotResolver(polyglotRegistry)

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
        val polyglotResolver = PolyglotResolver(
            PolyglotRegistry(
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
        val englishTranslation = polyglotResolver.getTranslationClass(SearchFieldStrings::class, MsgLocales.EN)
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
        val polyglotResolver = PolyglotResolver(
            PolyglotRegistry(
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
        val englishTranslation = polyglotResolver.getTranslationClass(SearchFieldStrings::class, MsgLocales.EN)

        // Then
        assertThat(englishTranslation.clear).isEqualTo("Clear")
        assertThat(englishTranslation.back).isEqualTo("Back")
    }

    @Test
    fun `Given mapper with fallback source language when resolving current translation then fallback mapper is used`() {
        // Given
        val polyglotResolver = PolyglotResolver(
            PolyglotRegistry(
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
        val polyglotResolver = PolyglotResolver(
            PolyglotRegistry(
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
        val polyglotResolver = PolyglotResolver(
            PolyglotRegistry(
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
        val polyglotResolver = PolyglotResolver(
            PolyglotRegistry(
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
                MsgLocales.PL,
                MsgLocales.EN,
                MsgLocales.EN_US,
            )
        )
    }

    @Test
    fun `Given non standard locale tag when resolving translations then fallback to raw trimmed locale tag`() {
        // Given
        val locale = MsgLocale.of("qaa-QM")
        val polyglotResolver = PolyglotResolver(
            PolyglotRegistry(
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
        assertThat(locales).isEqualTo(setOf(MsgLocale.of("qaa-QM")))
    }

    @Test
    fun `Given missing bundle when resolving translation then throw detailed error`() {
        // Given
        val polyglotResolver = PolyglotResolver(PolyglotRegistry())

        // When
        val exception = assertFailsWith<IllegalStateException> {
            polyglotResolver.getTranslationClass(TestStrings::class, localePl)
        }

        // Then
        assertThat(exception).messageContains("Can not find translations for locales: [pl, en]")
        assertThat(exception).messageContains("[translationClass='TestStrings']")
    }

    @Test
    fun `Given cyclic mappers when resolving translation then stop recursion and report missing translation`() {
        // Given
        val polyglotResolver = PolyglotResolver(
            PolyglotRegistry(
                listOf(
                    CyclicMapperAggregate(),
                )
            )
        )

        // When
        val exception = assertFailsWith<IllegalStateException> {
            polyglotResolver.getTranslationClass(RecursiveTargetStrings::class, MsgLocales.EN)
        }

        // Then
        assertThat(exception).messageContains("Can not find translations for locales: [en]")
        assertThat(exception).messageContains("[translationClass='RecursiveTargetStrings']")
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

private class RepositoryAggregate : MsgAggregate() {
    override fun register() {
        addBundle(RepositoryBundlePl())
        addBundle(RepositoryBundleEn())
    }
}

private class RegionalRepositoryAggregate : MsgAggregate() {
    override fun register() {
        addBundle(RegionalRepositoryBundleUs())
    }
}

private class NonStandardLocaleAggregate(
    private val locale: MsgLocale,
) : MsgAggregate() {
    override fun register() {
        addBundle(NonStandardLocaleBundle(locale))
    }
}

private class MapperAggregate(
    private vararg val msgAggregates: MsgAggregate,
) : MsgAggregate() {
    override fun register() {
        msgAggregates.forEach(::addBundle)
    }
}

private class RepositoryBundlePl : MsgBundle() {
    override val locale: MsgLocale = localePl

    override fun register() {
        add(
            TestStrings(
                name = "Imię",
                surname = "Nazwisko",
            )
        )
    }
}

private class RepositoryBundleEn : MsgBundle() {
    override val locale: MsgLocale = MsgLocales.EN

    override fun register() {
        add(
            TestStrings(
                name = "Name",
                surname = "Surname",
            )
        )
    }
}

private class RegionalRepositoryBundleUs : MsgBundle() {
    override val locale: MsgLocale = MsgLocales.EN_US

    override fun register() {
        add(
            SecondaryStrings(
                label = "Secondary US",
            )
        )
    }
}

private class NonStandardLocaleBundle(
    override val locale: MsgLocale,
) : MsgBundle() {
    override fun register() {
        add(
            TestStrings(
                name = "Non standard",
                surname = "Non standard",
            )
        )
    }
}

private class SearchFieldBundleEn : MsgBundle() {
    override val locale: MsgLocale = MsgLocales.EN

    override fun register() {
        add(
            SearchFieldStrings(
                clear = "Dismiss",
                back = "Navigate up",
            )
        )
    }
}

private class CommonBundlePl : MsgBundle() {
    override val locale: MsgLocale = localePl

    override fun register() {
        add(
            CommonStrings(
                clear = "Wyczyść",
                back = "Wróć",
            )
        )
    }
}

private class CommonBundleEn : MsgBundle() {
    override val locale: MsgLocale = MsgLocales.EN

    override fun register() {
        add(
            CommonStrings(
                clear = "Clear",
                back = "Back",
            )
        )
    }
}

private class SearchFieldMapper : MsgMapper() {
    override fun register() {
        add<CommonStrings, SearchFieldStrings> { strings ->
            SearchFieldStrings(
                clear = strings.clear,
                back = strings.back,
            )
        }
    }
}

private class CyclicMapperAggregate : MsgAggregate() {
    override fun register() {
        addBundle(RecursiveSourceMapper())
        addBundle(RecursiveTargetMapper())
    }
}

private class RecursiveSourceMapper : MsgMapper() {
    override fun register() {
        add<RecursiveTargetStrings, RecursiveSourceStrings> { strings ->
            RecursiveSourceStrings(strings.value)
        }
    }
}

private class RecursiveTargetMapper : MsgMapper() {
    override fun register() {
        add<RecursiveSourceStrings, RecursiveTargetStrings> { strings ->
            RecursiveTargetStrings(strings.value)
        }
    }
}

private class SearchFieldBundlePlPl : MsgBundle() {
    override val locale: MsgLocale = localePlPl

    override fun register() {
        add(
            SearchFieldStrings(
                clear = "Wyczyść (Polska)",
                back = "Wróć (Polska)",
            )
        )
    }
}

private class LazyRepositoryAggregate(
    private val counters: TranslationCounters,
) : MsgAggregate() {
    override fun register() {
        addBundle(LazyRepositoryBundleEn(counters))
        addBundle(LazyRepositoryBundlePl(counters))
    }
}

private class LazyRepositoryBundleEn(
    private val counters: TranslationCounters,
) : MsgBundle() {
    override val locale: MsgLocale = MsgLocales.EN

    override fun register() {
        counters.englishBundleRegistrationCount += 1
        add(
            TestStrings(
                name = "Name",
                surname = "Surname",
            )
        )
        add(
            SecondaryStrings(label = "Secondary")
        )
    }
}

private class LazyRepositoryBundlePl(
    private val counters: TranslationCounters,
) : MsgBundle() {
    override val locale: MsgLocale = localePl

    override fun register() {
        counters.polishBundleRegistrationCount += 1
        add(
            TestStrings(
                name = "Imię",
                surname = "Nazwisko",
            )
        )
        add(
            SecondaryStrings(label = "Pomocniczy")
        )
    }
}

private class TranslationCounters(
    var englishBundleRegistrationCount: Int = 0,
    var polishBundleRegistrationCount: Int = 0,
)

private val localePl: MsgLocale = MsgLocales.PL
private val localePlPl: MsgLocale = MsgLocales.PL_PL

