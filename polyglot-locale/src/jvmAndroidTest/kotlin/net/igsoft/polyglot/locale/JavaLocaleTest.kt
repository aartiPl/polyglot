package net.igsoft.polyglot.locale

import java.util.Locale as JavaLocale
import kotlin.test.Test
import kotlin.test.assertEquals

class JavaLocaleTest {

    @Test
    fun `Given French locale when converting to message locale then preserve language tag`() {
        assertEquals(Locale.of("fr-FR"), JavaLocale.forLanguageTag("fr-FR").toLocale())
    }

    @Test
    fun `Given French message locale when converting to Java locale then preserve language tag`() {
        assertEquals(JavaLocale.forLanguageTag("fr-FR"), Locale.of("fr-FR").toJavaLocale())
    }

    @Test
    fun `Given German locale when creating message locale then preserve language tag`() {
        assertEquals(Locale.of("de"), Locale.of(JavaLocale.GERMAN))
    }

    @Test
    fun `Given java locale when converting to message locale then normalize language tag`() {
        val javaLocale = JavaLocale.forLanguageTag("pl-PL-u-ca-gregory")

        assertEquals("pl-PL", javaLocale.toLocale().languageTag)
    }

    @Test
    @Suppress("DEPRECATION")
    fun `Given empty java locale when converting to message locale then return undetermined tag`() {
        val javaLocale = JavaLocale("", "")

        assertEquals("und", Locale.of(javaLocale).languageTag)
    }

    @Test
    fun `Given message locale when converting to java locale then preserve language tag`() {
        assertEquals("en-US", Locales.EN_US.toJavaLocale().toLanguageTag())
    }
}
