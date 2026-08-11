package ir.deweyshelf.app.domain

import ir.deweyshelf.app.core.digitsOnly
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookIdentifierGeneratorTest {
    @Test
    fun `generated identifier contains a six digit book number`() {
        val identifier = BookIdentifierGenerator.next(Random(42))

        assertTrue(identifier.startsWith("کتاب "))
        assertEquals(6, identifier.digitsOnly().length)
    }

    @Test
    fun `seeded generation is deterministic for testing`() {
        val first = BookIdentifierGenerator.next(Random(7))
        val second = BookIdentifierGenerator.next(Random(7))

        assertEquals(first, second)
    }
}
