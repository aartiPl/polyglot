package net.igsoft.polyglot.core

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.messageContains
import kotlin.test.Test
import kotlin.test.assertFailsWith

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

    @Test
    fun `Given localized and global mappers when resolving translation then localized mapper wins regardless of registration order`() {
        // Given
        val localizedMapper = object : MsgMapper() {
            override fun register() {
                add<RegistryCommonStrings, RegistryTargetStrings>(
                    locale = MsgLocales.EN,
                    sourceLocale = MsgLocales.PL,
                ) { strings ->
                    RegistryTargetStrings(strings.clear, strings.back)
                }
            }
        }
        val aggregates = listOf(
            RegistryCommonBundleEn(),
            RegistryCommonBundlePl(),
            RegistryTargetBundleEn(),
            RegistryTargetMapper(),
            localizedMapper,
        )

        listOf(aggregates, aggregates.reversed()).forEach { registrationOrder ->
            val registry = PolyglotRegistry(registrationOrder)

            // When
            val translation = registry.getTranslationClass(RegistryTargetStrings::class, listOf(MsgLocales.EN))

            // Then
            assertThat(translation.clear).isEqualTo("Wyczyść")
            assertThat(translation.back).isEqualTo("Wróć")
        }
    }

    @Test
    fun `Given mappers for multiple locales when resolving translations then retain each localized mapper`() {
        // Given
        val registry = PolyglotRegistry(listOf(RegistryBundleEn(), RegistryBundlePl()))
        registry.registerMapper(
            sourceClass = RegistryStrings::class,
            targetClass = RegistryStrings::class,
            locale = MsgLocales.EN_US,
            sourceLocale = MsgLocales.EN,
        ) { it.copy(label = "US ${it.label}") }
        registry.registerMapper(
            sourceClass = RegistryStrings::class,
            targetClass = RegistryStrings::class,
            locale = MsgLocales.PL_PL,
            sourceLocale = MsgLocales.PL,
        ) { it.copy(label = "PL ${it.label}") }

        // When
        val english = registry.getTranslationClass(RegistryStrings::class, listOf(MsgLocales.EN_US))
        val polish = registry.getTranslationClass(RegistryStrings::class, listOf(MsgLocales.PL_PL))

        // Then
        assertThat(english.label).isEqualTo("US English")
        assertThat(polish.label).isEqualTo("PL Polish")
    }

    @Test
    fun `Given localized mapper without source when resolving translation then use global mapper`() {
        // Given
        val registry = PolyglotRegistry(listOf(RegistryCommonBundleEn(), RegistryTargetMapper()))
        registry.registerMapper(
            sourceClass = RegistryCommonStrings::class,
            targetClass = RegistryTargetStrings::class,
            locale = MsgLocales.EN,
            sourceLocale = MsgLocales.PL,
        ) { RegistryTargetStrings("Unavailable", "Unavailable") }

        // When
        val translation = registry.getTranslationClass(RegistryTargetStrings::class, listOf(MsgLocales.EN))

        // Then
        assertThat(translation.clear).isEqualTo("Clear")
        assertThat(translation.back).isEqualTo("Back")
    }

    @Test
    fun `Given localized mapper without source when resolving translation then use direct translation`() {
        // Given
        val registry = PolyglotRegistry(listOf(RegistryTargetBundleEn()))
        registry.registerMapper(
            sourceClass = RegistryCommonStrings::class,
            targetClass = RegistryTargetStrings::class,
            locale = MsgLocales.EN,
            sourceLocale = MsgLocales.PL,
        ) { RegistryTargetStrings("Unavailable", "Unavailable") }

        // When
        val translation = registry.getTranslationClass(RegistryTargetStrings::class, listOf(MsgLocales.EN))

        // Then
        assertThat(translation.clear).isEqualTo("Dismiss")
        assertThat(translation.back).isEqualTo("Navigate up")
    }

    @Test
    fun `Given localized mapper without source when resolving regional translation then use language fallback`() {
        // Given
        val registry = PolyglotRegistry(listOf(RegistryBundleEn()))
        registry.registerMapper(
            sourceClass = RegistryStrings::class,
            targetClass = RegistryStrings::class,
            locale = MsgLocales.EN_US,
            sourceLocale = MsgLocales.PL,
        ) { it.copy(label = "Unavailable") }

        // When
        val translation = registry.getTranslationClass(RegistryStrings::class, listOf(MsgLocales.EN_US))

        // Then
        assertThat(translation.label).isEqualTo("English")
    }

    @Test
    fun `Given localized mapper with implicit source locale when resolving translation then use target locale source`() {
        // Given
        val registry = PolyglotRegistry(listOf(RegistryCommonBundleEn()))
        registry.registerMapper(
            sourceClass = RegistryCommonStrings::class,
            targetClass = RegistryTargetStrings::class,
            locale = MsgLocales.EN,
        ) { RegistryTargetStrings(it.clear, it.back) }

        // When
        val translation = registry.getTranslationClass(RegistryTargetStrings::class, listOf(MsgLocales.EN))

        // Then
        assertThat(translation.clear).isEqualTo("Clear")
        assertThat(translation.back).isEqualTo("Back")
    }

    @Test
    fun `Given localized mapper when listing locales then include target locale without adding source locale`() {
        // Given
        val registry = PolyglotRegistry()
        registry.registerMapper(
            sourceClass = RegistryStrings::class,
            targetClass = RegistryStrings::class,
            locale = MsgLocales.EN_US,
            sourceLocale = MsgLocales.PL,
        ) { it }

        // When
        val locales = registry.registeredLocales()

        // Then
        assertThat(locales).isEqualTo(setOf(MsgLocales.EN_US))
    }

    @Test
    fun `Given chained localized mappers when resolving translation then load source bundles outside requested fallback chain`() {
        // Given
        val counters = RegistryTranslationCounters()
        val registry = PolyglotRegistry(listOf(RegistryLazyAggregate(counters)))
        registry.registerMapper(
            sourceClass = RegistryStrings::class,
            targetClass = RegistryStrings::class,
            locale = MsgLocales.EN_US,
            sourceLocale = MsgLocales.PL_PL,
        ) { it.copy(label = "US ${it.label}") }
        registry.registerMapper(
            sourceClass = RegistryStrings::class,
            targetClass = RegistryStrings::class,
            locale = MsgLocales.PL_PL,
            sourceLocale = MsgLocales.PL,
        ) { it.copy(label = "PL ${it.label}") }

        // When
        val translation = registry.getTranslationClass(RegistryStrings::class, listOf(MsgLocales.EN_US))

        // Then
        assertThat(translation.label).isEqualTo("US PL Polish")
        assertThat(counters.polishBundleRegistrationCount).isEqualTo(1)
    }

    @Test
    fun `Given localized mapper based on global mapper when resolving translation then map the base before overriding`() {
        // Given
        val registry = PolyglotRegistry(listOf(RegistryCommonBundlePl(), RegistryTargetMapper()))
        registry.registerMapper(
            sourceClass = RegistryTargetStrings::class,
            targetClass = RegistryTargetStrings::class,
            locale = MsgLocales.PL_PL,
            sourceLocale = MsgLocales.PL,
        ) { it.copy(clear = "Regional") }

        // When
        val translation = registry.getTranslationClass(RegistryTargetStrings::class, listOf(MsgLocales.PL_PL))

        // Then
        assertThat(translation.clear).isEqualTo("Regional")
        assertThat(translation.back).isEqualTo("Wróć")
    }

    @Test
    fun `Given cyclic cross locale mappers when resolving translation then report dependency path`() {
        // Given
        val registry = PolyglotRegistry()
        registry.registerMapper(
            sourceClass = RegistryStrings::class,
            targetClass = RegistryStrings::class,
            locale = MsgLocales.EN_US,
            sourceLocale = MsgLocales.PL_PL,
        ) { it }
        registry.registerMapper(
            sourceClass = RegistryStrings::class,
            targetClass = RegistryStrings::class,
            locale = MsgLocales.PL_PL,
            sourceLocale = MsgLocales.EN_US,
        ) { it }

        // When
        val exception = assertFailsWith<IllegalStateException> {
            registry.getTranslationClass(RegistryStrings::class, listOf(MsgLocales.EN_US))
        }

        // Then
        assertThat(exception).messageContains("Cyclic translation dependency")
        assertThat(exception).messageContains("RegistryStrings[en-US, BASE]")
        assertThat(exception).messageContains("RegistryStrings[pl-PL, BASE]")
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

