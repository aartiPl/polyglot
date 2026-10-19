package net.igsoft.polyglot.translation

import kotlin.reflect.KClass

/** Provenance of a resolved model, without retaining model instances or registration callbacks. */
sealed interface TranslationOrigin {
    val translationClass: KClass<*>
    val registrationPath: String
}
