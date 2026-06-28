package net.igsoft.polyglot.core

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.messageContains
import assertk.assertions.prop
import kotlin.test.Test
import kotlin.test.assertFailsWith

class MsgLocaleTest {

    @Test
    fun `Given blank language tag when creating locale then throw`() {
        // Given
        val blankLanguageTag = "   "

        // When
        val exception = assertFailsWith<IllegalArgumentException> {
            MsgLocale.of(blankLanguageTag)
        }

        // Then
        assertThat(exception).messageContains("languageTag can not be blank")
    }

    @Test
    fun `Given message locale when reading language tag then expose raw value`() {
        // Given
        val msgLocale = MsgLocales.PL_PL

        // When
        val languageTag = msgLocale.languageTag

        // Then
        assertThat(languageTag).isEqualTo("pl-PL")
    }

    @Test
    fun `Given language tag with extension when creating locale then normalize and strip extensions`() {
        // Given
        val languageTag = "pl-PL-u-ca-gregory"

        // When
        val msgLocale = MsgLocale.of(languageTag)

        // Then
        assertThat(msgLocale).prop(MsgLocale::languageTag).isEqualTo("pl-PL")
    }

    @Test
    fun `Given invalid language tag format when creating locale then throw`() {
        // Given
        val invalidLanguageTag = " invalid_tag "

        // When
        val exception = assertFailsWith<IllegalArgumentException> {
            MsgLocale.of(invalidLanguageTag)
        }

        // Then
        assertThat(exception).messageContains("languageTag has invalid format")
    }

    @Test
    fun `Given well formed non standard language tag when creating locale then accept it`() {
        // Given
        val nonStandardLanguageTag = " qaa-QM "

        // When
        val msgLocale = MsgLocale.of(nonStandardLanguageTag)

        // Then
        assertThat(msgLocale).prop(MsgLocale::languageTag).isEqualTo("qaa-QM")
    }
}

