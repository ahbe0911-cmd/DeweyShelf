package ir.ketabyar.shelf.core

class ShelfCodeComparator(private val rules: LibraryRules) : Comparator<ShelfCode> {
    override fun compare(a: ShelfCode, b: ShelfCode): Int {
        val section = a.section.compareTo(b.section)
        if (section != 0) return section
        return if (a.section == BookSection.GENERAL) compareGeneral(a, b) else compareLiterature(a, b)
    }

    fun compareWithReason(a: ShelfCode, b: ShelfCode): Pair<Int, String> {
        val checks = if (a.section == BookSection.GENERAL) generalChecks(a, b) else literatureChecks(a, b)
        val first = checks.firstOrNull { it.first != 0 }
        return (first?.first ?: 0) to (first?.second ?: "تمام اجزای شماره بازیابی برابر است")
    }

    private fun compareGeneral(a: ShelfCode, b: ShelfCode) = generalChecks(a, b).firstOrNull { it.first != 0 }?.first ?: 0
    private fun compareLiterature(a: ShelfCode, b: ShelfCode) = literatureChecks(a, b).firstOrNull { it.first != 0 }?.first ?: 0

    private fun generalChecks(a: ShelfCode, b: ShelfCode) = buildList {
        add(intPart(a.mainClass, b.mainClass) to "شماره اصلی رده تعیین‌کننده است")
        add(decimalDigits(a.classDecimal, b.classDecimal) to "اعشار رده رقم‌به‌رقم مقایسه شد")
        add(alpha(a.authorLetter, b.authorLetter) to "حرف مؤلف تعیین‌کننده است")
        add(decimalDigits(a.authorNumber, b.authorNumber) to "عدد مؤلف مانند ادامه اعشاری مقایسه شد")
        add(alpha(a.workMark, b.workMark) to "نشانه اثر تعیین‌کننده است")
        if (rules.volumeAffectsOrder) add(natural(a.volume, b.volume) to "جلد تعیین‌کننده است")
        if (rules.editionAffectsOrder) add(natural(a.edition, b.edition) to "نسخه تعیین‌کننده است")
        if (rules.yearAffectsOrder) add(natural(a.year, b.year) to "سال انتشار تعیین‌کننده است")
    }

    private fun literatureChecks(a: ShelfCode, b: ShelfCode) = buildList {
        add(mixedCode(a.languageCode, b.languageCode) to "کد زبان ادبی تعیین‌کننده است")
        add(decimalDigits(a.literaturePeriod, b.literaturePeriod) to "دوره یا بخش ادبی مقایسه شد")
        add(alpha(a.authorLetter, b.authorLetter) to "حرف مؤلف تعیین‌کننده است")
        add(decimalDigits(a.authorNumber, b.authorNumber) to "عدد مؤلف مانند ادامه اعشاری مقایسه شد")
        add(alpha(a.workMark, b.workMark) to "نشانه اثر تعیین‌کننده است")
        add(alpha(a.titleLetter, b.titleLetter) to "حرف عنوان تعیین‌کننده است")
        if (rules.volumeAffectsOrder) add(natural(a.volume, b.volume) to "جلد تعیین‌کننده است")
        if (rules.editionAffectsOrder) add(natural(a.edition, b.edition) to "نسخه تعیین‌کننده است")
        if (rules.yearAffectsOrder) add(natural(a.year, b.year) to "سال انتشار تعیین‌کننده است")
    }

    private fun intPart(a: String, b: String) = PersianNormalizer.normalizedInteger(a).toBigInteger().compareTo(PersianNormalizer.normalizedInteger(b).toBigInteger())

    /** Digits are a decimal continuation: 1=.1, 01=.01, 001=.001, 10=.10. */
    private fun decimalDigits(a: String, b: String): Int {
        val x = PersianNormalizer.digitsOnly(a)
        val y = PersianNormalizer.digitsOnly(b)
        if (x.isEmpty() || y.isEmpty()) return emptyCompare(x, y)
        val size = maxOf(x.length, y.length)
        return x.padEnd(size, '0').compareTo(y.padEnd(size, '0'))
    }

    private fun natural(a: String, b: String): Int {
        val x = PersianNormalizer.normalize(a)
        val y = PersianNormalizer.normalize(b)
        if (x.isEmpty() || y.isEmpty()) return emptyCompare(x, y)
        val xn = x.toIntOrNull(); val yn = y.toIntOrNull()
        return if (xn != null && yn != null) xn.compareTo(yn) else alpha(x, y)
    }

    private fun alpha(a: String, b: String): Int {
        val x = PersianNormalizer.normalize(a)
        val y = PersianNormalizer.normalize(b)
        if (x.isEmpty() || y.isEmpty()) return emptyCompare(x, y)
        for (i in 0 until minOf(x.length, y.length)) {
            val xi = rules.persianAlphabet.indexOf(x[i]).let { if (it < 0) Int.MAX_VALUE else it }
            val yi = rules.persianAlphabet.indexOf(y[i]).let { if (it < 0) Int.MAX_VALUE else it }
            if (xi != yi) return xi.compareTo(yi)
            if (x[i] != y[i]) return x[i].compareTo(y[i])
        }
        return x.length.compareTo(y.length)
    }

    private fun mixedCode(a: String, b: String): Int {
        val x = PersianNormalizer.normalize(a); val y = PersianNormalizer.normalize(b)
        val xn = x.takeWhile(Char::isDigit); val yn = y.takeWhile(Char::isDigit)
        val number = intPart(xn, yn)
        return if (number != 0) number else alpha(x.drop(xn.length), y.drop(yn.length))
    }

    private fun emptyCompare(a: String, b: String): Int = when {
        a.isEmpty() && b.isEmpty() -> 0
        a.isEmpty() -> if (rules.emptyComponentFirst) -1 else 1
        else -> if (rules.emptyComponentFirst) 1 else -1
    }
}

