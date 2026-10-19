package net.igsoft.polyglot.translation

import net.igsoft.polyglot.locale.Locale

abstract class Bundle : Definition {
    abstract val locale: Locale

    abstract fun register(translations: BundleScope)
}
