package net.igsoft.polyglot.translation

import kotlin.reflect.KClass
import net.igsoft.polyglot.locale.Locale

data class BundleOrigin(
    override val translationClass: KClass<*>,
    val locale: Locale,
    val bundleClass: KClass<out Bundle>,
    override val registrationPath: String,
) : TranslationOrigin
