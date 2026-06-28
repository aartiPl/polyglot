package net.igsoft.polyglot.core

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import java.util.Locale
import kotlin.test.Test

class MsgLocaleJvmTest {

    @Test
    fun `Given java locale when converting to message locale then normalize language tag`() {
        // Given
        val javaLocale = Locale.forLanguageTag("pl-PL-u-ca-gregory")

        // When
        val msgLocale = javaLocale.toMsgLocale()

        // Then
        assertThat(msgLocale).prop(MsgLocale::languageTag).isEqualTo("pl-PL")
    }

    @Test
    @Suppress("DEPRECATION")
    fun `Given empty java locale when converting to message locale then return undetermined tag`() {
        // Given
        val javaLocale = Locale("", "")

        // When
        val msgLocale = MsgLocale.of(javaLocale)

        // Then
        assertThat(msgLocale).prop(MsgLocale::languageTag).isEqualTo("und")
    }

    @Test
    fun `Given message locale when converting to java locale then preserve language tag`() {
        // Given
        val msgLocale = MsgLocales.EN_US

        // When
        val javaLocale = msgLocale.toJavaLocale()

        // Then
        assertThat(javaLocale.toLanguageTag()).isEqualTo("en-US")
    }
}

