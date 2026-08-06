package net.igsoft.polyglot.core

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class MsgLocaleAndroidTest {

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
}
