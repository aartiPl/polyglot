package net.igsoft.polyglot.core

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class PolyglotRegistryTest {

    @Test
    fun `Given aggregate with locale bundles when resolving translation then register only requested locale lazily`() {
        // Given
        val counters = RegistryTranslationCounters()
        val registry = PolyglotRegistry()
        registry.registerBundle(RegistryLazyAggregate(counters))

        // When
        val englishTranslation = registry.getTranslationClass(RegistryStrings::class, listOf(MsgLocales.EN))

        // Then
        assertThat(englishTranslation.label).isEqualTo("English")
        assertThat(counters.englishBundleRegistrationCount).isEqualTo(1)
        assertThat(counters.polishBundleRegistrationCount).isEqualTo(0)
    }

    @Test
    fun `Given bundle registered after registry creation when resolving translation then resolve incrementally registered bundle`() {
        // Given
        val registry = PolyglotRegistry()
        registry.registerBundle(RegistryBundleEn())

        // When
        val translation = registry.getTranslationClass(RegistryStrings::class, listOf(MsgLocales.EN))

        // Then
        assertThat(translation.label).isEqualTo("English")
    }

    @Test
    fun `Given direct target bundle and mapper when resolving target translations then mapper has priority`() {
        // Given
        val registry = PolyglotRegistry(
            listOf(
                RegistryMapperAggregate(
                    RegistryCommonBundleEn(),
                    RegistryTargetBundleEn(),
                    RegistryTargetMapper(),
                )
            )
        )

        // When
        val translation = registry.getTranslationClass(RegistryTargetStrings::class, listOf(MsgLocales.EN))

        // Then
        assertThat(translation.clear).isEqualTo("Clear")
        assertThat(translation.back).isEqualTo("Back")
    }

    @Test
    fun `Given exact and fallback locale bundles when resolving translation then prefer exact locale`() {
        // Given
        val registry = PolyglotRegistry(
            listOf(
                RegistryMapperAggregate(
                    RegistryCommonBundlePl(),
                    RegistryTargetMapper(),
                    RegistryTargetBundlePlPl(),
                )
            )
        )

        // When
        val translation = registry.getTranslationClass(RegistryTargetStrings::class, listOf(MsgLocales.PL_PL))

        // Then
        assertThat(translation.clear).isEqualTo("Wyczyść (Polska)")
        assertThat(translation.back).isEqualTo("Wróć (Polska)")
    }

    @Test
    fun `Given pending bundles and direct registrations when listing locales then return all registered locales`() {
        // Given
        val registry = PolyglotRegistry()
        registry.registerBundle(RegistryBundleAggregate())
        registry.registerTranslationClass(MsgLocales.EN_US, RegistryAuxiliaryStrings::class) {
            RegistryAuxiliaryStrings("Auxiliary US")
        }

        // When
        val locales = registry.registeredLocales()

        // Then
        assertThat(locales).isEqualTo(
            setOf(
                MsgLocales.EN,
                MsgLocales.PL,
                MsgLocales.EN_US,
            )
        )
    }
}

private data class RegistryStrings(
    val label: String,
)

private data class RegistryCommonStrings(
    val clear: String,
    val back: String,
)

private data class RegistryTargetStrings(
    val clear: String,
    val back: String,
)

private data class RegistryAuxiliaryStrings(
    val label: String,
)

private class RegistryBundleAggregate : MsgAggregate() {
    override fun register() {
        addBundle(RegistryBundleEn())
        addBundle(RegistryBundlePl())
    }
}

private class RegistryLazyAggregate(
    private val counters: RegistryTranslationCounters,
) : MsgAggregate() {
    override fun register() {
        addBundle(RegistryLazyBundleEn(counters))
        addBundle(RegistryLazyBundlePl(counters))
    }
}

private class RegistryMapperAggregate(
    private vararg val msgAggregates: MsgAggregate,
) : MsgAggregate() {
    override fun register() {
        msgAggregates.forEach(::addBundle)
    }
}

private class RegistryBundleEn : MsgBundle() {
    override val locale: MsgLocale = MsgLocales.EN

    override fun register() {
        add(RegistryStrings(label = "English"))
    }
}

private class RegistryBundlePl : MsgBundle() {
    override val locale: MsgLocale = MsgLocales.PL

    override fun register() {
        add(RegistryStrings(label = "Polish"))
    }
}

private class RegistryLazyBundleEn(
    private val counters: RegistryTranslationCounters,
) : MsgBundle() {
    override val locale: MsgLocale = MsgLocales.EN

    override fun register() {
        counters.englishBundleRegistrationCount += 1
        add(RegistryStrings(label = "English"))
    }
}

private class RegistryLazyBundlePl(
    private val counters: RegistryTranslationCounters,
) : MsgBundle() {
    override val locale: MsgLocale = MsgLocales.PL

    override fun register() {
        counters.polishBundleRegistrationCount += 1
        add(RegistryStrings(label = "Polish"))
    }
}

private class RegistryCommonBundleEn : MsgBundle() {
    override val locale: MsgLocale = MsgLocales.EN

    override fun register() {
        add(
            RegistryCommonStrings(
                clear = "Clear",
                back = "Back",
            )
        )
    }
}

private class RegistryCommonBundlePl : MsgBundle() {
    override val locale: MsgLocale = MsgLocales.PL

    override fun register() {
        add(
            RegistryCommonStrings(
                clear = "Wyczyść",
                back = "Wróć",
            )
        )
    }
}

private class RegistryTargetBundleEn : MsgBundle() {
    override val locale: MsgLocale = MsgLocales.EN

    override fun register() {
        add(
            RegistryTargetStrings(
                clear = "Dismiss",
                back = "Navigate up",
            )
        )
    }
}

private class RegistryTargetBundlePlPl : MsgBundle() {
    override val locale: MsgLocale = MsgLocales.PL_PL

    override fun register() {
        add(
            RegistryTargetStrings(
                clear = "Wyczyść (Polska)",
                back = "Wróć (Polska)",
            )
        )
    }
}

private class RegistryTargetMapper : MsgMapper() {
    override fun register() {
        add<RegistryCommonStrings, RegistryTargetStrings> { strings ->
            RegistryTargetStrings(
                clear = strings.clear,
                back = strings.back,
            )
        }
    }
}

private class RegistryTranslationCounters(
    var englishBundleRegistrationCount: Int = 0,
    var polishBundleRegistrationCount: Int = 0,
)

