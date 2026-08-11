package ir.deweyshelf.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookValidatorTest {
    @Test
    fun `required fields are reported`() {
        val result = BookValidator.validate(BookDraft())

        assertFalse(result.isValid)
        assertEquals(ValidationError.Required, result.errors[FormField.Title])
        assertEquals(ValidationError.Required, result.errors[FormField.MainClass])
        assertEquals(ValidationError.Required, result.errors[FormField.AuthorLetter])
        assertEquals(ValidationError.Required, result.errors[FormField.AuthorNumber])
    }

    @Test
    fun `persian digits are accepted and normalized`() {
        val draft = BookDraft(
            title = "تاریخ ایران",
            mainClass = "۹۱۵",
            decimalPart = "۶۹۴",
            authorLetter = "ب",
            authorNumber = "۵۱۶",
        )

        val result = BookValidator.validate(draft)
        val book = draft.normalized()

        assertTrue(result.isValid)
        assertEquals(915, book.mainClass)
        assertEquals("694", book.decimalPart)
        assertEquals("516", book.authorNumber)
    }

    @Test
    fun `main class outside three digits is rejected`() {
        val draft = BookDraft(title = "کتاب", mainClass = "۱۰۰۰", authorLetter = "ک", authorNumber = "۱۲")

        val result = BookValidator.validate(draft)

        assertEquals(ValidationError.InvalidMainClass, result.errors[FormField.MainClass])
    }
}

