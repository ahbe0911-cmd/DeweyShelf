package ir.ketabyar.shelf.core

import org.junit.Assert.*
import org.junit.Test

class ShelfCodeComparatorTest {
    private val comparator = ShelfCodeComparator(LibraryRules(literaturePatternConfirmed = true))
    private fun g(main: String="746", decimal: String="755", letter: String="ر", number: String="376", mark: String="ع", volume: String="", edition: String="", year: String="") = ShelfCode(BookSection.GENERAL, main, decimal, authorLetter=letter, authorNumber=number, workMark=mark, volume=volume, edition=edition, year=year)
    private fun l(language: String="8فا", period: String="32", letter: String="د", number: String="198", mark: String="ح") = ShelfCode(BookSection.LITERATURE, languageCode=language, literaturePeriod=period, authorLetter=letter, authorNumber=number, workMark=mark)

    @Test fun `one hundred generated ordering cases`() {
        val cases = buildList {
            for (i in 0 until 25) add(g(main=(100+i).toString()) to g(main=(101+i).toString()))
            for (i in 1..25) add(g(decimal="0${i}") to g(decimal=(i+1).toString()))
            for (i in 1..25) add(g(number="0${i}") to g(number=(i+1).toString()))
            val letters = "ابتثجچحخدذرزژسشصضطظعغفقکگلم"
            for (i in 0 until 25) add(g(letter=letters[i].toString()) to g(letter=letters[i+1].toString()))
        }
        assertEquals(100, cases.size)
        cases.forEachIndexed { index, (a,b) -> assertTrue("case $index", comparator.compare(a,b) < 0) }
    }

    @Test fun `decimal continuation edge cases`() {
        assertTrue(comparator.compare(g(decimal="001"), g(decimal="01")) < 0)
        assertTrue(comparator.compare(g(decimal="01"), g(decimal="1")) < 0)
        assertEquals(0, comparator.compare(g(decimal="1"), g(decimal="10")))
        assertTrue(comparator.compare(g(decimal="10"), g(decimal="11")) < 0)
    }

    @Test fun `trailing decimal zero is equivalent`() { assertEquals(0, comparator.compare(g(decimal="1"), g(decimal="10"))) }
    @Test fun `persian and english digits are equivalent`() { assertEquals(0, comparator.compare(g(number="۳۷۶"), g(number="376"))) }
    @Test fun `arabic and persian yeh are equivalent`() { assertEquals(0, comparator.compare(g(letter="ي"), g(letter="ی"))) }
    @Test fun `arabic and persian kaf are equivalent`() { assertEquals(0, comparator.compare(g(letter="ك"), g(letter="ک"))) }
    @Test fun `spaces and half spaces do not alter order`() { assertEquals(0, comparator.compare(g(letter=" ر "), g(letter="ر\u200c"))) }
    @Test fun `author letter can be absent`() { assertTrue(comparator.compare(g(letter="",number="376"), g(letter="ر",number="376")) < 0) }
    @Test fun `author number can be absent`() { assertTrue(comparator.compare(g(number=""), g(number="376")) < 0) }
    @Test fun `work marks order in Persian`() { assertTrue(comparator.compare(g(mark="ح"),g(mark="خ")) < 0) }
    @Test fun `volume edition and year break ties`() { assertTrue(comparator.compare(g(volume="1"),g(volume="2")) < 0); assertTrue(comparator.compare(g(edition="1"),g(edition="2")) < 0); assertTrue(comparator.compare(g(year="1400"),g(year="1401")) < 0) }
    @Test fun `provided literature labels are ordered`() { assertTrue(comparator.compare(l(letter="ح",number="468",mark="خ"), l(letter="د",number="198",mark="ح")) < 0) }
    @Test fun `identical label is duplicate candidate`() { assertEquals(0, comparator.compare(g(),g())) }
}
