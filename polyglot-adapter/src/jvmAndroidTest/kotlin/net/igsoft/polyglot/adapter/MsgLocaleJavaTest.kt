package net.igsoft.polyglot.adapter

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import net.igsoft.polyglot.core.MsgLocale
import net.igsoft.polyglot.core.MsgLocales

class MsgLocaleJavaTest {

    @Test
    fun `Given French locale when converting to message locale then preserve language tag`() {
        assertEquals(MsgLocale.of("fr-FR"), Locale.forLanguageTag("fr-FR").toMsgLocale())
    }

    @Test
    fun `Given French message locale when converting to Java locale then preserve language tag`() {
        assertEquals(Locale.forLanguageTag("fr-FR"), MsgLocale.of("fr-FR").toJavaLocale())
    }

    @Test
    fun `Given German locale when creating message locale then preserve language tag`() {
        assertEquals(MsgLocale.of("de"), MsgLocale.of(Locale.GERMAN))
    }

    @Test
    fun `Given java locale when converting to message locale then normalize language tag`() {
        val javaLocale = Locale.forLanguageTag("pl-PL-u-ca-gregory")

        assertEquals("pl-PL", javaLocale.toMsgLocale().languageTag)
    }

    @Test
    @Suppress("DEPRECATION")
    fun `Given empty java locale when converting to message locale then return undetermined tag`() {
        val javaLocale = Locale("", "")

        assertEquals("und", MsgLocale.of(javaLocale).languageTag)
    }

    @Test
    fun `Given message locale when converting to java locale then preserve language tag`() {
        assertEquals("en-US", MsgLocales.EN_US.toJavaLocale().toLanguageTag())
    }
}
