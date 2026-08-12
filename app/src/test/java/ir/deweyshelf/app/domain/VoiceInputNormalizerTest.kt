package ir.deweyshelf.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceInputNormalizerTest {
    @Test
    fun `persian cardinal speech becomes digits`() {
        assertEquals(
            "915",
            VoiceInputNormalizer.forField(FormField.MainClass, "نهصد و پانزده"),
        )
    }

    @Test
    fun `separate spoken digits preserve their order`() {
        assertEquals(
            "516",
            VoiceInputNormalizer.forField(FormField.AuthorNumber, "پنج یک شش"),
        )
    }

    @Test
    fun `numeric voice input respects field length`() {
        assertEquals(
            "1405",
            VoiceInputNormalizer.forField(FormField.PublicationYear, "۱۴۰۵۹"),
        )
    }

    @Test
    fun `letter voice input keeps the first normalized character`() {
        assertEquals("ی", VoiceInputNormalizer.forField(FormField.AuthorLetter, "ياسین"))
    }
}
