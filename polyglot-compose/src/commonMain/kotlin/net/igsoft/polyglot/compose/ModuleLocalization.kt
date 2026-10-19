package net.igsoft.polyglot.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import net.igsoft.polyglot.core.PolyglotResolver
import kotlin.reflect.KClass

@Composable
@ReadOnlyComposable
fun <S : Any> rememberTranslations(polyglotResolver: PolyglotResolver, clazz: KClass<S>): S =
    polyglotResolver.getTranslationClass(clazz, LocalLocale.current)

@Composable
@ReadOnlyComposable
fun <S : Any> rememberTranslations(clazz: KClass<S>): S = rememberTranslations(LocalPolyglotResolver.current, clazz)

@Composable
@ReadOnlyComposable
inline fun <reified S : Any> rememberTranslations(polyglotResolver: PolyglotResolver): S =
    rememberTranslations(polyglotResolver, S::class)

@Composable
@ReadOnlyComposable
inline fun <reified S : Any> rememberTranslations(): S = rememberTranslations(LocalPolyglotResolver.current, S::class)

