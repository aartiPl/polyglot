package net.igsoft.polyglot.translation.compose

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.igsoft.polyglot.locale.Locales
import net.igsoft.polyglot.translation.Bundle
import net.igsoft.polyglot.translation.BundleScope
import net.igsoft.polyglot.translation.DiagnosticResolver
import net.igsoft.polyglot.translation.Registry
import net.igsoft.polyglot.translation.Resolver

class ModuleLocalizationTest {
    @Test
    fun `Given diagnostic resolver in Compose when recomposing then share results and refresh changed sources`() {
        var creations = 0
        val registry = Registry()
        registry.registerTranslationClass<Strings>(Locales.EN) { Strings("Value ${++creations}") }
        val diagnostics = DiagnosticResolver(registry)
        val iteration = mutableStateOf(0)
        var observed: Strings? = null

        withComposition(
            content = {
                iteration.value
                AppLocalizationProvider(diagnostics.resolver, Locales.PL) {
                    observed = rememberTranslations<Strings>()
                }
            },
        ) {
            val first = diagnostics.resolve<Strings>(Locales.PL)
            assertSame(observed, first.value)
            assertTrue(first.diagnostics.cacheHit)
            assertEquals(1, creations)

            registry.registerTranslationClass<Strings>(Locales.PL) { Strings("Value ${++creations}") }
            iteration.value += 1
            recompose()

            val updated = diagnostics.resolve<Strings>(Locales.PL)
            assertSame(observed, updated.value)
            assertTrue(updated.diagnostics.cacheHit)
            assertEquals("Value 2", updated.value.label)
            assertEquals(2, creations)
        }
    }

    @Test
    fun `Given composition locale different from resolver locale when resolving then use composition locale`() {
        val registry = Registry()
        registry.registerTranslationClass(Locales.EN, Strings::class) { Strings("English") }
        registry.registerTranslationClass(Locales.PL, Strings::class) { Strings("Polski") }
        val resolver = Resolver(registry)
        val locale = mutableStateOf(Locales.PL)
        var observed: Strings? = null

        withComposition(
            content = {
                CompositionLocalProvider(LocalLocale provides locale.value) {
                    observed = rememberTranslations<Strings>(resolver)
                }
            },
        ) {
            assertEquals("Polski", observed?.label)

            resolver.setCurrentLocale(Locales.PL)
            locale.value = Locales.EN
            recompose()

            assertEquals("English", observed?.label)
        }
    }

    @Test
    fun `Given remembered results when updating registration then refresh every overload on recomposition and reuse resolver cache`() {
        val registry = Registry()
        var creations = 0
        registry.registerTranslationClass(Locales.EN, Strings::class) {
            creations += 1
            Strings("Initial")
        }
        val resolver = Resolver(registry)
        val iteration = mutableStateOf(0)
        var observed = emptyList<Strings>()

        withComposition(
            content = {
                iteration.value
                CompositionLocalProvider(LocalPolyglotResolver provides resolver) {
                    observed = listOf(
                        rememberTranslations(resolver, Strings::class),
                        rememberTranslations<Strings>(resolver),
                        rememberTranslations(Strings::class),
                        rememberTranslations<Strings>(),
                    )
                }
            },
        ) {
            assertEquals(List(4) { "Initial" }, observed.map { it.label })
            assertEquals(1, creations)

            registry.registerTranslationClass(Locales.EN, Strings::class) {
                creations += 1
                Strings("Updated")
            }
            iteration.value += 1
            recompose()

            assertEquals(List(4) { "Updated" }, observed.map { it.label })
            assertEquals(2, creations)
            val previous = observed.first()
            iteration.value += 1
            recompose()

            observed.forEach { assertSame(previous, it) }
            assertEquals(2, creations)
        }
    }

    @Test
    fun `Given mapped result when overriding its source then refresh the final translation on recomposition`() {
        val registry = Registry()
        registry.registerTranslationClass(Locales.EN, SourceStrings::class) { SourceStrings("Initial source") }
        registry.registerTranslationClass(Locales.EN, Strings::class) { Strings("Default") }
        val resolver = Resolver(registry)
        val iteration = mutableStateOf(0)
        var observed: Strings? = null

        withComposition(
            content = {
                iteration.value
                observed = rememberTranslations(resolver, Strings::class)
            },
        ) {
            assertEquals("Default", observed?.label)

            registry.registerMapper(SourceStrings::class, Strings::class) { source: SourceStrings, target: Strings ->
                target.copy(label = source.label)
            }
            iteration.value += 1
            recompose()
            assertEquals("Initial source", observed?.label)

            registry.registerTranslationClass(Locales.EN, SourceStrings::class) { SourceStrings("Updated source") }
            iteration.value += 1
            recompose()
            assertEquals("Updated source", observed?.label)
        }
    }

    @Test
    fun `Given remembered fallback when registering a locale bundle then refresh once and reuse the loaded model`() {
        val registry = Registry()
        registry.registerTranslationClass(Locales.EN, Strings::class) { Strings("Fallback") }
        val resolver = Resolver(registry)
        val iteration = mutableStateOf(0)
        var bundleCalls = 0
        var observed: Strings? = null

        withComposition(
            content = {
                iteration.value
                CompositionLocalProvider(LocalLocale provides Locales.PL) {
                    observed = rememberTranslations(resolver, Strings::class)
                }
            },
        ) {
            assertEquals("Fallback", observed?.label)

            registry.registerDefinition(object : Bundle() {
                override val locale = Locales.PL

                override fun register(translations: BundleScope) {
                    bundleCalls += 1
                    translations.add(Strings("Polski"))
                }
            })
            iteration.value += 1
            recompose()
            assertEquals("Polski", observed?.label)
            assertEquals(1, bundleCalls)

            val previous = observed
            iteration.value += 1
            recompose()
            assertSame(previous, observed)
            assertEquals(1, bundleCalls)
        }
    }

    @Test
    fun `Given nested providers sharing a resolver when reading outer translations after inner then preserve each locale`() {
        val registry = Registry()
        registry.registerTranslationClass(Locales.EN, Strings::class) { Strings("English") }
        registry.registerTranslationClass(Locales.PL, Strings::class) { Strings("Polski") }
        val resolver = Resolver(registry)
        var outer: Strings? = null
        var inner: Strings? = null

        withComposition(
            content = {
                AppLocalizationProvider(resolver, Locales.EN) {
                    AppLocalizationProvider(resolver, Locales.PL) {
                        inner = rememberTranslations<Strings>()
                    }
                    outer = rememberTranslations<Strings>()
                }
            },
        ) {
            assertEquals("Polski", inner?.label)
            assertEquals("English", outer?.label)
        }
    }

    private fun withComposition(content: @Composable () -> Unit, assertions: suspend Recomposer.() -> Unit) =
        runBlocking(ImmediateFrameClock()) {
            withTimeout(10_000.milliseconds) {
                val recomposer = Recomposer(coroutineContext)
                val composition = Composition(EmptyApplier(), recomposer)
                val runner = launch(start = CoroutineStart.UNDISPATCHED) {
                    recomposer.runRecomposeAndApplyChanges()
                }
                try {
                    composition.setContent(content)
                    recomposer.awaitIdle()
                    recomposer.assertions()
                } finally {
                    composition.dispose()
                    recomposer.close()
                    runner.join()
                }
            }
        }

    private suspend fun Recomposer.recompose() {
        Snapshot.sendApplyNotifications()
        awaitIdle()
    }

    private class ImmediateFrameClock : MonotonicFrameClock {
        private var timeNanos = 0L

        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            timeNanos += 16_666_667
            return onFrame(timeNanos)
        }
    }

    private class EmptyApplier : AbstractApplier<Unit>(Unit) {
        override fun insertTopDown(index: Int, instance: Unit) = Unit
        override fun insertBottomUp(index: Int, instance: Unit) = Unit
        override fun remove(index: Int, count: Int) = Unit
        override fun move(from: Int, to: Int, count: Int) = Unit
        override fun onClear() = Unit
    }

    private data class Strings(val label: String)

    private data class SourceStrings(val label: String)
}
