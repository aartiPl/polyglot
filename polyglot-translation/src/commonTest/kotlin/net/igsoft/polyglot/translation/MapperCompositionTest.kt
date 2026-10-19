package net.igsoft.polyglot.translation

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.messageContains
import kotlin.test.Test
import kotlin.test.assertFailsWith
import net.igsoft.polyglot.locale.Locale
import net.igsoft.polyglot.locale.Locales

class MapperCompositionTest {
    private val portuguese = Locale.of("pt")
    private val brazilianPortuguese = Locale.of("pt-BR")

    @Test
    fun `Given regional component and language common strings when resolving then override only selected fields`() {
        val aggregates = listOf(
            componentBundle(brazilianPortuguese, phone = "Celular"),
            commonBundle(portuguese, "OK"),
            applicationMapper(),
        )

        listOf(aggregates, aggregates.reversed()).forEach { registrationOrder ->
            val resolver = Resolver(Registry(registrationOrder))

            val strings = resolver.getTranslationClass(ComponentStrings::class, brazilianPortuguese)

            assertThat(strings).isEqualTo(ComponentStrings("OK", "Celular", "Cancelar"))
        }
    }

    @Test
    fun `Given regional inheritance for component and common strings when resolving then compose both before application override`() {
        val mapper = object : Module() {
            override fun register(registration: ModuleScope) {
                registration.registerMapper(
                    ComponentStrings::class,
                    ComponentStrings::class,
                    locale = brazilianPortuguese,
                    sourceLocale = portuguese,
                ) {
                    it.copy(confirm = "Regional default", phone = "Celular")
                }
                registration.registerMapper(
                    CommonStrings::class,
                    CommonStrings::class,
                    locale = brazilianPortuguese,
                    sourceLocale = portuguese,
                ) {
                    it.copy(ok = "OK Brasil")
                }
            }
        }
        val aggregates = listOf(
            componentBundle(portuguese),
            commonBundle(portuguese, "OK Portugal"),
            mapper,
            applicationMapper(),
        )

        listOf(aggregates, aggregates.reversed()).forEach { registrationOrder ->
            listOf(
                listOf(portuguese, brazilianPortuguese),
                listOf(brazilianPortuguese, portuguese),
            ).forEach { lookupOrder ->
                val resolver = Resolver(Registry(registrationOrder))
                lookupOrder.forEach { resolver.getTranslationClass(ComponentStrings::class, it) }

                val regional = resolver.getTranslationClass(ComponentStrings::class, brazilianPortuguese)
                val base = resolver.getTranslationClass(ComponentStrings::class, portuguese)

                assertThat(regional).isEqualTo(ComponentStrings("OK Brasil", "Celular", "Cancelar"))
                assertThat(base).isEqualTo(ComponentStrings("OK Portugal", "Telemóvel", "Cancelar"))
            }
        }
    }

    @Test
    fun `Given component from default fallback when overriding then resolve common strings using original locale`() {
        val registry = Registry(
            listOf(componentBundle(Locales.EN), commonBundle(brazilianPortuguese, "OK Brasil"), applicationMapper())
        )

        val strings = Resolver(registry).getTranslationClass(ComponentStrings::class, brazilianPortuguese)

        assertThat(strings.confirm).isEqualTo("OK Brasil")
    }

    @Test
    fun `Given global and localized overrides when resolving then compose global with most specific applicable override once`() {
        val registry = Registry(
            listOf(componentBundle(portuguese), commonBundle(portuguese, "OK"), applicationMapper())
        )
        var regionalCalls = 0
        registry.registerMapper(
            CommonStrings::class, ComponentStrings::class, locale = brazilianPortuguese,
        ) { _: CommonStrings, component: ComponentStrings ->
            regionalCalls += 1
            component.copy(confirm = "${component.confirm} Brasil")
        }
        registry.registerMapper(
            CommonStrings::class, ComponentStrings::class, locale = portuguese,
        ) { _: CommonStrings, component: ComponentStrings -> component.copy(confirm = "Wrong language override") }
        val resolver = Resolver(registry)

        val strings = resolver.getTranslationClass(ComponentStrings::class, brazilianPortuguese)
        resolver.getTranslationClass(ComponentStrings::class, brazilianPortuguese)

        assertThat(strings.confirm).isEqualTo("OK Brasil")
        assertThat(regionalCalls).isEqualTo(1)
    }

    @Test
    fun `Given localized override without source when resolving then try less specific override`() {
        val registry = Registry(listOf(componentBundle(portuguese), commonBundle(portuguese, "OK")))
        registry.registerMapper(
            BrandStrings::class, ComponentStrings::class, locale = brazilianPortuguese,
        ) { _: BrandStrings, component: ComponentStrings -> component.copy(confirm = "Unavailable") }
        registry.registerMapper(
            CommonStrings::class, ComponentStrings::class, locale = portuguese,
        ) { common: CommonStrings, component: ComponentStrings -> component.copy(confirm = common.ok) }

        val strings = Resolver(registry).getTranslationClass(ComponentStrings::class, brazilianPortuguese)

        assertThat(strings.confirm).isEqualTo("OK")
    }

    @Test
    fun `Given localized override at language fallback when resolving then use regional source strings`() {
        val registry = Registry(
            listOf(componentBundle(portuguese), commonBundle(brazilianPortuguese, "OK Brasil"))
        )
        registry.registerMapper(
            CommonStrings::class, ComponentStrings::class, locale = portuguese,
        ) { common: CommonStrings, component: ComponentStrings -> component.copy(confirm = common.ok) }

        val strings = Resolver(registry).getTranslationClass(ComponentStrings::class, brazilianPortuguese)

        assertThat(strings.confirm).isEqualTo("OK Brasil")
    }

    @Test
    fun `Given override without source when resolving then preserve component defaults`() {
        val registry = Registry(listOf(componentBundle(portuguese), applicationMapper()))

        val strings = Resolver(registry).getTranslationClass(ComponentStrings::class, brazilianPortuguese)

        assertThat(strings).isEqualTo(ComponentStrings("Confirmar", "Telemóvel", "Cancelar"))
    }

    @Test
    fun `Given override without component base when resolving then report missing translation`() {
        val registry = Registry(listOf(commonBundle(portuguese, "OK"), applicationMapper()))

        val exception = assertFailsWith<IllegalStateException> {
            Resolver(registry).getTranslationClass(ComponentStrings::class, brazilianPortuguese)
        }

        assertThat(exception).messageContains("Can not find translations")
        assertThat(exception).messageContains("ComponentStrings")
    }

    @Test
    fun `Given override with exact source locale when source only exists in language fallback then preserve base`() {
        val registry = Registry(listOf(componentBundle(portuguese), commonBundle(portuguese, "OK")))
        registry.registerMapper(
            CommonStrings::class, ComponentStrings::class, sourceLocale = brazilianPortuguese,
        ) { common: CommonStrings, component: ComponentStrings -> component.copy(confirm = common.ok) }

        val strings = Resolver(registry).getTranslationClass(ComponentStrings::class, brazilianPortuguese)

        assertThat(strings.confirm).isEqualTo("Confirmar")
    }

    @Test
    fun `Given same type override with missing exact source when target uses fallback then skip override`() {
        // Given
        val registry = Registry(listOf(componentBundle(Locales.EN)))
        registry.registerMapper(
            ComponentStrings::class, ComponentStrings::class, sourceLocale = portuguese,
        ) { source: ComponentStrings, component: ComponentStrings -> component.copy(confirm = source.confirm) }

        // When
        val strings = Resolver(registry).getTranslationClass(ComponentStrings::class, portuguese)

        // Then
        assertThat(strings.confirm).isEqualTo("Confirmar")
    }

    @Test
    fun `Given implicit override source only in default locale when resolving then use default source fallback`() {
        // Given
        val registry = Registry(
            listOf(componentBundle(brazilianPortuguese), commonBundle(Locales.EN, "English OK"), applicationMapper())
        )

        // When
        val strings = Resolver(registry).getTranslationClass(ComponentStrings::class, brazilianPortuguese)

        // Then
        assertThat(strings.confirm).isEqualTo("English OK")
    }

    @Test
    fun `Given explicit source outside requested chain when overriding then load source lazily`() {
        var registrations = 0
        val sourceBundle = object : Bundle() {
            override val locale = Locales.PL

            override fun register(translations: BundleScope) {
                registrations += 1
                translations.add(CommonStrings("OK Polska"))
            }
        }
        val registry = Registry(listOf(componentBundle(portuguese), sourceBundle))
        registry.registerMapper(
            CommonStrings::class, ComponentStrings::class, sourceLocale = Locales.PL,
        ) { common: CommonStrings, component: ComponentStrings -> component.copy(confirm = common.ok) }
        val resolver = Resolver(registry)
        assertThat(registrations).isEqualTo(0)

        val strings = resolver.getTranslationClass(ComponentStrings::class, brazilianPortuguese)
        resolver.getTranslationClass(ComponentStrings::class, brazilianPortuguese)

        assertThat(strings.confirm).isEqualTo("OK Polska")
        assertThat(registrations).isEqualTo(1)
    }

    @Test
    fun `Given overridden common strings when overriding component then use final common translation`() {
        val registry = Registry(
            listOf(componentBundle(portuguese), commonBundle(portuguese, "OK"), applicationMapper())
        )
        registry.registerTranslationClass(portuguese, BrandStrings::class) { BrandStrings("Branded OK") }
        registry.registerMapper(BrandStrings::class, CommonStrings::class) { brand: BrandStrings, common: CommonStrings ->
            common.copy(ok = brand.ok)
        }

        val strings = Resolver(registry).getTranslationClass(ComponentStrings::class, brazilianPortuguese)

        assertThat(strings.confirm).isEqualTo("Branded OK")
    }

    @Test
    fun `Given base conversion and source override when building base then use source base without application override`() {
        val registry = Registry(listOf(commonBundle(portuguese, "Base OK")))
        registry.registerTranslationClass(portuguese, BrandStrings::class) { BrandStrings("Branded OK") }
        registry.registerMapper(CommonStrings::class, ComponentStrings::class) {
            ComponentStrings(it.ok, "Telemóvel", "Cancelar")
        }
        registry.registerMapper(BrandStrings::class, CommonStrings::class) { brand: BrandStrings, common: CommonStrings ->
            common.copy(ok = brand.ok)
        }
        val resolver = Resolver(registry)

        val component = resolver.getTranslationClass(ComponentStrings::class, portuguese)
        val common = resolver.getTranslationClass(CommonStrings::class, portuguese)

        assertThat(component.confirm).isEqualTo("Base OK")
        assertThat(common.ok).isEqualTo("Branded OK")
    }

    @Test
    fun `Given inherited base and application override when resolving then apply override only after inheritance`() {
        var calls = 0
        val mapper = object : Module() {
            override fun register(registration: ModuleScope) {
                registration.registerMapper(
                    ComponentStrings::class,
                    ComponentStrings::class,
                    locale = brazilianPortuguese,
                    sourceLocale = portuguese,
                ) {
                    it.copy(phone = "Celular")
                }
                registration.registerMapper(CommonStrings::class, ComponentStrings::class) { common, component ->
                    calls += 1
                    component.copy(confirm = "${component.confirm} ${common.ok}")
                }
            }
        }
        val resolver = Resolver(
            Registry(listOf(componentBundle(portuguese), commonBundle(portuguese, "OK"), mapper))
        )

        val strings = resolver.getTranslationClass(ComponentStrings::class, brazilianPortuguese)
        resolver.getTranslationClass(ComponentStrings::class, brazilianPortuguese)

        assertThat(strings.confirm).isEqualTo("Confirmar OK")
        assertThat(calls).isEqualTo(1)
    }

    @Test
    fun `Given cyclic overrides with available bases when resolving then report dependency path`() {
        val registry = Registry(listOf(componentBundle(portuguese), commonBundle(portuguese, "OK")))
        registry.registerMapper(CommonStrings::class, ComponentStrings::class) { common: CommonStrings, component: ComponentStrings ->
            component.copy(confirm = common.ok)
        }
        registry.registerMapper(ComponentStrings::class, CommonStrings::class) { component: ComponentStrings, common: CommonStrings ->
            common.copy(ok = component.confirm)
        }

        val exception = assertFailsWith<IllegalStateException> {
            Resolver(registry).getTranslationClass(ComponentStrings::class, portuguese)
        }

        assertThat(exception).messageContains("Cyclic translation dependency")
        assertThat(exception).messageContains("ComponentStrings[pt, FINAL]")
        assertThat(exception).messageContains("CommonStrings[pt, FINAL]")
    }

    @Test
    fun `Given duplicate mapper for same role type and locale when registering then reject duplicate`() {
        val registry = Registry()
        registry.registerMapper(CommonStrings::class, ComponentStrings::class) {
            ComponentStrings(it.ok, "Telemóvel", "Cancelar")
        }
        registry.registerMapper(CommonStrings::class, ComponentStrings::class) { common: CommonStrings, component: ComponentStrings ->
            component.copy(confirm = common.ok)
        }

        val baseException = assertFailsWith<IllegalStateException> {
            registry.registerMapper(BrandStrings::class, ComponentStrings::class) {
                ComponentStrings(it.ok, "Celular", "Cancelar")
            }
        }
        val overrideException = assertFailsWith<IllegalStateException> {
            registry.registerMapper(BrandStrings::class, ComponentStrings::class) { brand: BrandStrings, component: ComponentStrings ->
                component.copy(confirm = brand.ok)
            }
        }

        assertThat(baseException).messageContains("Duplicate base mapper")
        assertThat(overrideException).messageContains("Duplicate override mapper")
    }

    @Test
    fun `Given cached defaults when registering override and updating its source then refresh cached dependent translation`() {
        val registry = Registry(listOf(componentBundle(portuguese), commonBundle(portuguese, "OK")))
        val resolver = Resolver(registry)
        assertThat(resolver.getTranslationClass(ComponentStrings::class, portuguese).confirm).isEqualTo("Confirmar")

        registry.registerDefinition(applicationMapper())
        assertThat(resolver.getTranslationClass(ComponentStrings::class, portuguese).confirm).isEqualTo("OK")

        registry.registerTranslationClass(portuguese, CommonStrings::class) { CommonStrings("Updated OK") }
        assertThat(resolver.getTranslationClass(ComponentStrings::class, portuguese).confirm).isEqualTo("Updated OK")
    }

    @Test
    fun `Given cached fallback when registering regional bundle then refresh cached translation`() {
        val registry = Registry(listOf(componentBundle(portuguese)))
        val resolver = Resolver(registry)
        assertThat(resolver.getTranslationClass(ComponentStrings::class, brazilianPortuguese).phone).isEqualTo("Telemóvel")

        registry.registerDefinition(componentBundle(brazilianPortuguese, phone = "Celular"))

        assertThat(resolver.getTranslationClass(ComponentStrings::class, brazilianPortuguese).phone).isEqualTo("Celular")
    }

    @Test
    fun `Given skipped override cached when registering source bundle then apply override on next lookup`() {
        val registry = Registry(listOf(componentBundle(portuguese), applicationMapper()))
        val resolver = Resolver(registry)
        assertThat(resolver.getTranslationClass(ComponentStrings::class, brazilianPortuguese).confirm).isEqualTo("Confirmar")

        registry.registerDefinition(commonBundle(portuguese, "OK"))

        assertThat(resolver.getTranslationClass(ComponentStrings::class, brazilianPortuguese).confirm).isEqualTo("OK")
    }

    @Test
    fun `Given localized override when listing locales then include target locale without source locale`() {
        val registry = Registry()
        registry.registerMapper(
            CommonStrings::class, ComponentStrings::class,
            locale = brazilianPortuguese, sourceLocale = portuguese,
        ) { common: CommonStrings, component: ComponentStrings -> component.copy(confirm = common.ok) }

        assertThat(registry.registeredLocales()).isEqualTo(setOf(brazilianPortuguese))
    }

    private fun componentBundle(locale: Locale, phone: String = "Telemóvel") = object : Bundle() {
        override val locale = locale

        override fun register(translations: BundleScope) {
            translations.add(ComponentStrings("Confirmar", phone, "Cancelar"))
        }
    }

    private fun commonBundle(locale: Locale, ok: String) = object : Bundle() {
        override val locale = locale

        override fun register(translations: BundleScope) {
            translations.add(CommonStrings(ok))
        }
    }

    private fun applicationMapper() = object : Module() {
        override fun register(registration: ModuleScope) {
            registration.registerMapper(CommonStrings::class, ComponentStrings::class) { common, component ->
                component.copy(confirm = common.ok)
            }
        }
    }

    private data class ComponentStrings(
        val confirm: String,
        val phone: String,
        val cancel: String,
    )

    private data class CommonStrings(
        val ok: String,
    )

    private data class BrandStrings(
        val ok: String,
    )
}
