package net.igsoft.polyglot.translation.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import kotlin.reflect.KClass
import net.igsoft.polyglot.translation.Resolver

@Composable
@ReadOnlyComposable
fun <S : Any> rememberTranslations(polyglotResolver: Resolver, clazz: KClass<S>): S =
    polyglotResolver.getTranslationClass(clazz, LocalLocale.current)

@Composable
@ReadOnlyComposable
fun <S : Any> rememberTranslations(clazz: KClass<S>): S = rememberTranslations(LocalPolyglotResolver.current, clazz)

@Composable
@ReadOnlyComposable
inline fun <reified S : Any> rememberTranslations(polyglotResolver: Resolver): S =
    rememberTranslations(polyglotResolver, S::class)

@Composable
@ReadOnlyComposable
inline fun <reified S : Any> rememberTranslations(): S = rememberTranslations(LocalPolyglotResolver.current, S::class)
