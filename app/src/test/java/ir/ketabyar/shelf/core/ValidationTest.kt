package ir.ketabyar.shelf.core

import org.junit.Assert.*
import org.junit.Test

class ValidationTest {
    private val rules = LibraryRules(literaturePatternConfirmed = true)
    @Test fun `main class requires exactly three digits`() { assertTrue(BookValidator.validate(ShelfCode(BookSection.GENERAL, mainClass="99"),"کتاب","1",rules).containsKey("mainClass")) }
    @Test fun `Persian main class is valid`() { assertFalse(BookValidator.validate(ShelfCode(BookSection.GENERAL, mainClass="۷۴۶"),"کتاب","1",rules).containsKey("mainClass")) }
    @Test fun `decimal rejects separator`() { assertTrue(BookValidator.validate(ShelfCode(BookSection.GENERAL, mainClass="746",classDecimal=".755"),"کتاب","1",rules).containsKey("classDecimal")) }
    @Test fun `author components are optional`() { val e=BookValidator.validate(ShelfCode(BookSection.GENERAL,mainClass="746"),"کتاب","1",rules); assertFalse(e.containsKey("authorLetter")); assertFalse(e.containsKey("authorNumber")) }
    @Test fun `literature pattern needs librarian confirmation`() { val e=BookValidator.validate(ShelfCode(BookSection.LITERATURE,languageCode="8فا",literaturePeriod="32"),"کتاب","1",LibraryRules()); assertTrue(e.containsKey("literaturePattern")) }
    @Test fun `year must be plausible`() { assertTrue(BookValidator.validate(ShelfCode(BookSection.GENERAL,mainClass="746",year="999"),"کتاب","1",rules).containsKey("year")) }
}

