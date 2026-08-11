package ir.deweyshelf.app.domain

import ir.deweyshelf.app.core.digitsOnly
import ir.deweyshelf.app.core.firstNormalizedCharacter
import ir.deweyshelf.app.core.normalizePersian
import java.text.Collator
import java.util.Locale

object DeweyCatalog {
    val classes = listOf(
        DeweyClassInfo("۰۰۰–۰۹۹", "کلیات، اطلاعات و علوم رایانه", 0, 99),
        DeweyClassInfo("۱۰۰–۱۹۹", "فلسفه و روان‌شناسی", 100, 199),
        DeweyClassInfo("۲۰۰–۲۹۹", "دین و مذهب", 200, 299),
        DeweyClassInfo("۳۰۰–۳۹۹", "علوم اجتماعی", 300, 399),
        DeweyClassInfo("۴۰۰–۴۹۹", "زبان", 400, 499),
        DeweyClassInfo("۵۰۰–۵۹۹", "علوم طبیعی و ریاضیات", 500, 599),
        DeweyClassInfo("۶۰۰–۶۹۹", "فناوری و علوم کاربردی", 600, 699),
        DeweyClassInfo("۷۰۰–۷۹۹", "هنر و سرگرمی", 700, 799),
        DeweyClassInfo("۸۰۰–۸۹۹", "ادبیات", 800, 899),
        DeweyClassInfo("۹۰۰–۹۹۹", "تاریخ و جغرافیا", 900, 999),
    )

    fun classFor(number: Int): DeweyClassInfo = classes[(number.coerceIn(0, 999) / 100)]
}

object BookValidator {
    fun validate(draft: BookDraft): ValidationResult {
        val errors = buildMap {
            if (draft.title.normalizePersian().isEmpty()) put(FormField.Title, ValidationError.Required)

            val mainDigits = draft.mainClass.digitsOnly()
            val main = mainDigits.toIntOrNull()
            if (mainDigits.isEmpty()) {
                put(FormField.MainClass, ValidationError.Required)
            } else if (main == null || main !in 0..999 || mainDigits.length > 3) {
                put(FormField.MainClass, ValidationError.InvalidMainClass)
            }

            if (draft.authorLetter.firstNormalizedCharacter().isEmpty()) {
                put(FormField.AuthorLetter, ValidationError.Required)
            }
            if (draft.authorNumber.digitsOnly().isEmpty()) {
                put(FormField.AuthorNumber, ValidationError.Required)
            }

            listOf(
                FormField.DecimalPart to draft.decimalPart,
                FormField.AuthorNumber to draft.authorNumber,
                FormField.Volume to draft.volume,
                FormField.CopyNumber to draft.copyNumber,
                FormField.PublicationYear to draft.publicationYear,
            ).forEach { (field, value) ->
                if (value.isNotBlank() && value.digitsOnly().length != value.filterNot(Char::isWhitespace).length) {
                    put(field, ValidationError.InvalidNumber)
                }
            }
        }
        return ValidationResult(errors)
    }
}

object DeweySorter {
    private val alphabet = listOf(
        "ا", "آ", "ب", "پ", "ت", "ث", "ج", "چ", "ح", "خ", "د", "ذ", "ر", "ز", "ژ",
        "س", "ش", "ص", "ض", "ط", "ظ", "ع", "غ", "ف", "ق", "ک", "گ", "ل", "م", "ن", "و", "ه", "ی",
    )
    private val collator: Collator = Collator.getInstance(Locale("fa")).apply {
        strength = Collator.PRIMARY
    }

    val comparator: Comparator<DeweyBook> = Comparator { left, right -> compareCore(left, right) }

    fun sort(books: List<DeweyBook>): List<DeweyBook> = books.sortedWith(comparator)

    fun analyze(books: List<DeweyBook>): List<ShelfPosition> {
        val sorted = sort(books)
        val counts = books.groupingBy(::labelKey).eachCount()
        return sorted.mapIndexed { index, book ->
            ShelfPosition(
                book = book,
                originalIndex = books.indexOfFirst { it.id == book.id },
                sortedIndex = index,
                reason = reasonBetween(sorted.getOrNull(index - 1), book),
                isDuplicate = counts[labelKey(book)]?.let { it > 1 } == true,
            )
        }
    }

    fun labelKey(book: DeweyBook): String = listOf(
        "%03d".format(book.mainClass),
        book.decimalPart.digitsOnly(),
        book.authorLetter.firstNormalizedCharacter(),
        book.authorNumber.digitsOnly(),
        book.workMark.firstNormalizedCharacter(),
        book.volume?.toString().orEmpty(),
        book.copyNumber?.toString().orEmpty(),
        book.publicationYear?.toString().orEmpty(),
    ).joinToString("|")

    fun duplicateCount(books: List<DeweyBook>): Int =
        books.groupingBy(::labelKey).eachCount().values.sumOf { count -> (count - 1).coerceAtLeast(0) }

    private fun compareCore(left: DeweyBook, right: DeweyBook): Int {
        compareValues(left.mainClass, right.mainClass).takeIf { it != 0 }?.let { return it }
        compareDecimalDigits(left.decimalPart, right.decimalPart).takeIf { it != 0 }?.let { return it }
        compareLetters(left.authorLetter, right.authorLetter).takeIf { it != 0 }?.let { return it }
        compareDecimalDigits(left.authorNumber, right.authorNumber).takeIf { it != 0 }?.let { return it }
        compareText(left.workMark, right.workMark).takeIf { it != 0 }?.let { return it }
        compareValues(left.volume ?: 0, right.volume ?: 0).takeIf { it != 0 }?.let { return it }
        compareValues(left.copyNumber ?: 0, right.copyNumber ?: 0).takeIf { it != 0 }?.let { return it }
        compareValues(left.publicationYear ?: 0, right.publicationYear ?: 0).takeIf { it != 0 }?.let { return it }
        compareText(left.title, right.title).takeIf { it != 0 }?.let { return it }
        compareValues(left.createdAt, right.createdAt).takeIf { it != 0 }?.let { return it }
        return compareValues(left.id, right.id)
    }

    private fun compareDecimalDigits(leftValue: String, rightValue: String): Int {
        var left = leftValue.digitsOnly()
        var right = rightValue.digitsOnly()
        val length = maxOf(left.length, right.length)
        left = left.padEnd(length, '0')
        right = right.padEnd(length, '0')
        return left.compareTo(right)
    }

    private fun compareLetters(leftValue: String, rightValue: String): Int {
        val left = leftValue.firstNormalizedCharacter()
        val right = rightValue.firstNormalizedCharacter()
        val leftIndex = alphabet.indexOf(left).takeIf { it >= 0 } ?: (1_000 + left.firstOrNull()?.code.orEmpty())
        val rightIndex = alphabet.indexOf(right).takeIf { it >= 0 } ?: (1_000 + right.firstOrNull()?.code.orEmpty())
        return leftIndex.compareTo(rightIndex)
    }

    private fun Int?.orEmpty(): Int = this ?: 0

    private fun compareText(left: String, right: String): Int =
        collator.compare(left.normalizePersian(), right.normalizePersian())

    private fun reasonBetween(previous: DeweyBook?, current: DeweyBook): SortReason {
        if (previous == null) return SortReason.Start
        if (previous.mainClass != current.mainClass) return SortReason.MainClass
        if (compareDecimalDigits(previous.decimalPart, current.decimalPart) != 0) return SortReason.DecimalPart
        if (compareLetters(previous.authorLetter, current.authorLetter) != 0) return SortReason.AuthorLetter
        if (compareDecimalDigits(previous.authorNumber, current.authorNumber) != 0) return SortReason.AuthorNumber
        if (compareText(previous.workMark, current.workMark) != 0) return SortReason.WorkMark
        if (previous.volume != current.volume) return SortReason.Volume
        if (previous.copyNumber != current.copyNumber) return SortReason.CopyNumber
        if (previous.publicationYear != current.publicationYear) return SortReason.PublicationYear
        return SortReason.Title
    }
}

