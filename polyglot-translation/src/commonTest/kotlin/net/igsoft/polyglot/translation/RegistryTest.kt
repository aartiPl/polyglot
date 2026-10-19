package net.igsoft.polyglot.translation

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.messageContains
import kotlin.test.Test
import kotlin.test.assertFailsWith
import net.igsoft.polyglot.locale.Locale
import net.igsoft.polyglot.locale.Locales

class RegistryTest {

    @Test
    fun `Given aggregate with locale bundles when resolving translation then register only requested locale lazily`() {
        // Given
        val counters = RegistryTranslationCounters()
        val registry = Registry()
        registry.registerDefinition(RegistryLazyAggregate(counters))

        // When
        val englishTranslation = registry.getTranslationClass(RegistryStrings::class, listOf(Locales.EN))

        // Then
        assertThat(englishTranslation.label).isEqualTo("English")
        assertThat(counters.englishBundleRegistrationCount).isEqualTo(1)
        assertThat(counters.polishBundleRegistrationCount).isEqualTo(0)
    }

    @Test
    fun `Given bundle registered after registry creation when resolving translation then resolve incrementally registered bundle`() {
        // Given
        val registry = Registry()
        registry.registerDefinition(RegistryBundleEn())

        // When
        val translation = registry.getTranslationClass(RegistryStrings::class, listOf(Locales.EN))

        // Then
        assertThat(translation.label).isEqualTo("English")
    }

    @Test
    fun `Given direct target bundle and mapper when resolving target translations then mapper has priority`() {
        // Given
        val registry = Registry(
            listOf(
                RegistryMapperAggregate(
                    RegistryCommonBundleEn(),
                    RegistryTargetBundleEn(),
                    RegistryTargetMapper(),
                )
            )
        )

        // When
        val translation = registry.getTranslationClass(RegistryTargetStrings::class, listOf(Locales.EN))

        // Then
        assertThat(translation.clear).isEqualTo("Clear")
        assertThat(translation.back).isEqualTo("Back")
    }

    @Test
    fun `Given exact and fallback locale bundles when resolving translation then prefer exact locale`() {
        // Given
        val registry = Registry(
            listOf(
                RegistryMapperAggregate(
                    RegistryCommonBundlePl(),
                    RegistryTargetMapper(),
                    RegistryTargetBundlePlPl(),
                )
            )
        )

        // When
        val translation = registry.getTranslationClass(RegistryTargetStrings::class, listOf(Locales.PL_PL))

        // Then
        assertThat(translation.clear).isEqualTo("Wyczyść (Polska)")
        assertThat(translation.back).isEqualTo("Wróć (Polska)")
    }

    @Test
    fun `Given pending bundles and direct registrations when listing locales then return all registered locales`() {
        // Given
        val registry = Registry()
        registry.registerDefinition(RegistryBundleAggregate())
        registry.registerTranslationClass(Locales.EN_US, RegistryAuxiliaryStrings::class) {
            RegistryAuxiliaryStrings("Auxiliary US")
        }

        // When
        val locales = registry.registeredLocales()

        // Then
        assertThat(locales).isEqualTo(
            setOf(
                Locales.EN,
                Locales.PL,
                Locales.EN_US,
            )
        )
    }

    @Test
    fun `Given localized and global mappers when resolving translation then localized mapper wins regardless of registration order`() {
        // Given
        val localizedMapper = object : Module() {
            override fun register(registration: ModuleScope) {
                registration.registerMapper(
                    RegistryCommonStrings::class,
                    RegistryTargetStrings::class,
                    locale = Locales.EN,
                    sourceLocale = Locales.PL,
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
            val registry = Registry(registrationOrder)

            // When
            val translation = registry.getTranslationClass(RegistryTargetStrings::class, listOf(Locales.EN))

            // Then
            assertThat(translation.clear).isEqualTo("Wyczyść")
            assertThat(translation.back).isEqualTo("Wróć")
        }
    }

    @Test
    fun `Given mappers for multiple locales when resolving translations then retain each localized mapper`() {
        // Given
        val registry = Registry(listOf(RegistryBundleEn(), RegistryBundlePl()))
        registry.registerMapper(
            sourceClass = RegistryStrings::class,
            targetClass = RegistryStrings::class,
            locale = Locales.EN_US,
            sourceLocale = Locales.EN,
        ) { it.copy(label = "US ${it.label}") }
        registry.registerMapper(
            sourceClass = RegistryStrings::class,
            targetClass = RegistryStrings::class,
            locale = Locales.PL_PL,
            sourceLocale = Locales.PL,
        ) { it.copy(label = "PL ${it.label}") }

        // When
        val english = registry.getTranslationClass(RegistryStrings::class, listOf(Locales.EN_US))
        val polish = registry.getTranslationClass(RegistryStrings::class, listOf(Locales.PL_PL))

        // Then
        assertThat(english.label).isEqualTo("US English")
        assertThat(polish.label).isEqualTo("PL Polish")
    }

    @Test
    fun `Given localized mapper without source when resolving translation then use global mapper`() {
        // Given
        val registry = Registry(listOf(RegistryCommonBundleEn(), RegistryTargetMapper()))
        registry.registerMapper(
            sourceClass = RegistryCommonStrings::class,
            targetClass = RegistryTargetStrings::class,
            locale = Locales.EN,
            sourceLocale = Locales.PL,
        ) { RegistryTargetStrings("Unavailable", "Unavailable") }

        // When
        val translation = registry.getTranslationClass(RegistryTargetStrings::class, listOf(Locales.EN))

        // Then
        assertThat(translation.clear).isEqualTo("Clear")
        assertThat(translation.back).isEqualTo("Back")
    }

    @Test
    fun `Given localized mapper without source when resolving translation then use direct translation`() {
        // Given
        val registry = Registry(listOf(RegistryTargetBundleEn()))
        registry.registerMapper(
            sourceClass = RegistryCommonStrings::class,
            targetClass = RegistryTargetStrings::class,
            locale = Locales.EN,
            sourceLocale = Locales.PL,
        ) { RegistryTargetStrings("Unavailable", "Unavailable") }

        // When
        val translation = registry.getTranslationClass(RegistryTargetStrings::class, listOf(Locales.EN))

        // Then
        assertThat(translation.clear).isEqualTo("Dismiss")
        assertThat(translation.back).isEqualTo("Navigate up")
    }

    @Test
    fun `Given localized mapper without source when resolving regional translation then use language fallback`() {
        // Given
        val registry = Registry(listOf(RegistryBundleEn()))
        registry.registerMapper(
            sourceClass = RegistryStrings::class,
            targetClass = RegistryStrings::class,
            locale = Locales.EN_US,
            sourceLocale = Locales.PL,
        ) { it.copy(label = "Unavailable") }

        // When
        val translation = registry.getTranslationClass(RegistryStrings::class, listOf(Locales.EN_US))

        // Then
        assertThat(translation.label).isEqualTo("English")
    }

    @Test
    fun `Given localized mapper with implicit source locale when resolving translation then use target locale source`() {
        // Given
        val registry = Registry(listOf(RegistryCommonBundleEn()))
        registry.registerMapper(
            sourceClass = RegistryCommonStrings::class,
            targetClass = RegistryTargetStrings::class,
            locale = Locales.EN,
        ) { RegistryTargetStrings(it.clear, it.back) }

        // When
        val translation = registry.getTranslationClass(RegistryTargetStrings::class, listOf(Locales.EN))

        // Then
        assertThat(translation.clear).isEqualTo("Clear")
        assertThat(translation.back).isEqualTo("Back")
    }

    @Test
    fun `Given localized mapper when listing locales then include target locale without adding source locale`() {
        // Given
        val registry = Registry()
        registry.registerMapper(
            sourceClass = RegistryStrings::class,
            targetClass = RegistryStrings::class,
            locale = Locales.EN_US,
            sourceLocale = Locales.PL,
        ) { it }

        // When
        val locales = registry.registeredLocales()

        // Then
        assertThat(locales).isEqualTo(setOf(Locales.EN_US))
    }

    @Test
    fun `Given chained localized mappers when resolving translation then load source bundles outside requested fallback chain`() {
        // Given
        val counters = RegistryTranslationCounters()
        val registry = Registry(listOf(RegistryLazyAggregate(counters)))
        registry.registerMapper(
            sourceClass = RegistryStrings::class,
            targetClass = RegistryStrings::class,
            locale = Locales.EN_US,
            sourceLocale = Locales.PL_PL,
        ) { it.copy(label = "US ${it.label}") }
        registry.registerMapper(
            sourceClass = RegistryStrings::class,
            targetClass = RegistryStrings::class,
            locale = Locales.PL_PL,
            sourceLocale = Locales.PL,
        ) { it.copy(label = "PL ${it.label}") }

        // When
        val translation = registry.getTranslationClass(RegistryStrings::class, listOf(Locales.EN_US))

        // Then
        assertThat(translation.label).isEqualTo("US PL Polish")
        assertThat(counters.polishBundleRegistrationCount).isEqualTo(1)
    }

    @Test
    fun `Given localized mapper based on global mapper when resolving translation then map the base before overriding`() {
        // Given
        val registry = Registry(listOf(RegistryCommonBundlePl(), RegistryTargetMapper()))
        registry.registerMapper(
            sourceClass = RegistryTargetStrings::class,
            targetClass = RegistryTargetStrings::class,
            locale = Locales.PL_PL,
            sourceLocale = Locales.PL,
        ) { it.copy(clear = "Regional") }

        // When
        val translation = registry.getTranslationClass(RegistryTargetStrings::class, listOf(Locales.PL_PL))

        // Then
        assertThat(translation.clear).isEqualTo("Regional")
        assertThat(translation.back).isEqualTo("Wróć")
    }

    @Test
    fun `Given cyclic cross locale mappers when resolving translation then report dependency path`() {
        // Given
        val registry = Registry()
        registry.registerMapper(
            sourceClass = RegistryStrings::class,
            targetClass = RegistryStrings::class,
            locale = Locales.EN_US,
            sourceLocale = Locales.PL_PL,
        ) { it }
        registry.registerMapper(
            sourceClass = RegistryStrings::class,
            targetClass = RegistryStrings::class,
            locale = Locales.PL_PL,
            sourceLocale = Locales.EN_US,
        ) { it }

        // When
        val exception = assertFailsWith<IllegalStateException> {
            registry.getTranslationClass(RegistryStrings::class, listOf(Locales.EN_US))
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

private class RegistryBundleAggregate : Module() {
    override fun register(registration: ModuleScope) {
        registration.registerDefinition(RegistryBundleEn())
        registration.registerDefinition(RegistryBundlePl())
    }
}

private class RegistryLazyAggregate(
    private val counters: RegistryTranslationCounters,
) : Module() {
    override fun register(registration: ModuleScope) {
        registration.registerDefinition(RegistryLazyBundleEn(counters))
        registration.registerDefinition(RegistryLazyBundlePl(counters))
    }
}

private class RegistryMapperAggregate(
    private vararg val definitions: Definition,
) : Module() {
    override fun register(registration: ModuleScope) {
        definitions.forEach(registration::registerDefinition)
    }
}

private class RegistryBundleEn : Bundle() {
    override val locale: Locale = Locales.EN

    override fun register(translations: BundleScope) {
        translations.add(RegistryStrings(label = "English"))
    }
}

private class RegistryBundlePl : Bundle() {
    override val locale: Locale = Locales.PL

    override fun register(translations: BundleScope) {
        translations.add(RegistryStrings(label = "Polish"))
    }
}

private class RegistryLazyBundleEn(
    private val counters: RegistryTranslationCounters,
) : Bundle() {
    override val locale: Locale = Locales.EN

    override fun register(translations: BundleScope) {
        counters.englishBundleRegistrationCount += 1
        translations.add(RegistryStrings(label = "English"))
    }
}

private class RegistryLazyBundlePl(
    private val counters: RegistryTranslationCounters,
) : Bundle() {
    override val locale: Locale = Locales.PL

    override fun register(translations: BundleScope) {
        counters.polishBundleRegistrationCount += 1
        translations.add(RegistryStrings(label = "Polish"))
    }
}

private class RegistryCommonBundleEn : Bundle() {
    override val locale: Locale = Locales.EN

    override fun register(translations: BundleScope) {
        translations.add(
            RegistryCommonStrings(
                clear = "Clear",
                back = "Back",
            )
        )
    }
}

private class RegistryCommonBundlePl : Bundle() {
    override val locale: Locale = Locales.PL

    override fun register(translations: BundleScope) {
        translations.add(
            RegistryCommonStrings(
                clear = "Wyczyść",
                back = "Wróć",
            )
        )
    }
}

private class RegistryTargetBundleEn : Bundle() {
    override val locale: Locale = Locales.EN

    override fun register(translations: BundleScope) {
        translations.add(
            RegistryTargetStrings(
                clear = "Dismiss",
                back = "Navigate up",
            )
        )
    }
}

private class RegistryTargetBundlePlPl : Bundle() {
    override val locale: Locale = Locales.PL_PL

    override fun register(translations: BundleScope) {
        translations.add(
            RegistryTargetStrings(
                clear = "Wyczyść (Polska)",
                back = "Wróć (Polska)",
            )
        )
    }
}

private class RegistryTargetMapper : Module() {
    override fun register(registration: ModuleScope) {
        registration.registerMapper(RegistryCommonStrings::class, RegistryTargetStrings::class) { strings ->
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
