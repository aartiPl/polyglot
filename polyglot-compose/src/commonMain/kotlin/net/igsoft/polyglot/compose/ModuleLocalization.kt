package net.igsoft.polyglot.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import net.igsoft.polyglot.core.PolyglotResolver
import kotlin.reflect.KClass

@Composable
fun <S : Any> rememberTranslations(polyglotResolver: PolyglotResolver, clazz: KClass<S>): S =
    remember(polyglotResolver, LocalLocale.current, clazz) { polyglotResolver.getTranslationClass(clazz) }

@Composable
fun <S : Any> rememberTranslations(clazz: KClass<S>): S = rememberTranslations(LocalPolyglotResolver.current, clazz)

@Composable
inline fun <reified S : Any> rememberTranslations(): S = rememberTranslations(LocalPolyglotResolver.current, S::class)

