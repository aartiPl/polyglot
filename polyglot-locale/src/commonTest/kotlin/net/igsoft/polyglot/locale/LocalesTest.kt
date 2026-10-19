package net.igsoft.polyglot.locale

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class LocalesTest {
    @Test
    fun `Given predefined locale when reading repeatedly then reuse normalized language tag`() {
        val firstTag = Locales.EN_US.languageTag

        val secondTag = Locales.EN_US.languageTag

        assertSame(firstTag, secondTag)
    }

    @Test
    fun `Given numeric region constant when looking up parsed map key then match normalized locale`() {
        val translations = mapOf(Locales.ES_419 to "Latin America")

        val translation = translations[Locale.of(" ES-419 ")]

        assertEquals("Latin America", translation)
    }
}
