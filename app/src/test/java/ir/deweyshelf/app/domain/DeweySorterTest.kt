package ir.deweyshelf.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeweySorterTest {
    @Test
    fun `dewey decimals are compared digit by digit`() {
        val books = listOf(
            book(id = 1, decimal = "72", title = "سوم"),
            book(id = 2, decimal = "5", title = "اول"),
            book(id = 3, decimal = "694", title = "دوم"),
        )

        assertEquals(listOf("5", "694", "72"), DeweySorter.sort(books).map { it.decimalPart })
    }

    @Test
    fun `author numbers use cutter decimal order`() {
        val books = listOf(
            book(id = 1, authorNumber = "52", title = "سوم"),
            book(id = 2, authorNumber = "516", title = "دوم"),
            book(id = 3, authorNumber = "51", title = "اول"),
        )

        assertEquals(listOf("51", "516", "52"), DeweySorter.sort(books).map { it.authorNumber })
    }

    @Test
    fun `analysis reports original and correct positions`() {
        val books = listOf(
            book(id = 1, mainClass = 900, title = "دوم"),
            book(id = 2, mainClass = 100, title = "اول"),
        )

        val result = DeweySorter.analyze(books)

        assertEquals(2L, result.first().book.id)
        assertEquals(1, result.first().originalIndex)
        assertEquals(0, result.first().sortedIndex)
        assertEquals(1, result.first().movement)
    }

    @Test
    fun `identical labels are marked duplicate`() {
        val books = listOf(book(id = 1, title = "الف"), book(id = 2, title = "ب"))

        val result = DeweySorter.analyze(books)

        assertTrue(result.all { it.isDuplicate })
        assertEquals(1, DeweySorter.duplicateCount(books))
    }

    @Test
    fun `analysis preserves original indexes for an unsorted catalog`() {
        val books = (1L..250L).map { id ->
            book(id = id, mainClass = (1_000L - id).toInt().coerceAtMost(999), title = "کتاب $id")
        }

        val result = DeweySorter.analyze(books)

        assertEquals(249, result.first().originalIndex)
        assertEquals(0, result.last().originalIndex)
    }

    private fun book(
        id: Long,
        mainClass: Int = 915,
        decimal: String = "5",
        authorNumber: String = "51",
        title: String,
    ) = DeweyBook(
        id = id,
        title = title,
        mainClass = mainClass,
        decimalPart = decimal,
        authorLetter = "ب",
        authorNumber = authorNumber,
        workMark = "ت",
        createdAt = id,
    )
}
