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
        val originalIndexes = books.mapIndexed { index, book -> book.id to index }.toMap()
        return sorted.mapIndexed { index, book ->
            ShelfPosition(
                book = book,
                originalIndex = originalIndexes[book.id] ?: index,
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

object VoiceInputNormalizer {
    private val digitWords = mapOf(
        "صفر" to "0",
        "یک" to "1",
        "یه" to "1",
        "دو" to "2",
        "سه" to "3",
        "چهار" to "4",
        "پنج" to "5",
        "شش" to "6",
        "هفت" to "7",
        "هشت" to "8",
        "نه" to "9",
    )

    private val numberWords = mapOf(
        "صفر" to 0,
        "یک" to 1,
        "یه" to 1,
        "دو" to 2,
        "سه" to 3,
        "چهار" to 4,
        "پنج" to 5,
        "شش" to 6,
        "هفت" to 7,
        "هشت" to 8,
        "نه" to 9,
        "ده" to 10,
        "یازده" to 11,
        "دوازده" to 12,
        "سیزده" to 13,
        "چهارده" to 14,
        "پانزده" to 15,
        "شانزده" to 16,
        "هفده" to 17,
        "هجده" to 18,
        "نوزده" to 19,
        "بیست" to 20,
        "سی" to 30,
        "چهل" to 40,
        "پنجاه" to 50,
        "شصت" to 60,
        "هفتاد" to 70,
        "هشتاد" to 80,
        "نود" to 90,
        "صد" to 100,
        "یکصد" to 100,
        "دویست" to 200,
        "سیصد" to 300,
        "چهارصد" to 400,
        "پانصد" to 500,
        "ششصد" to 600,
        "هفتصد" to 700,
        "هشتصد" to 800,
        "نهصد" to 900,
    )

    fun forField(field: FormField, spokenText: String): String {
        val normalized = spokenText.normalizePersian()
        return when (field) {
            FormField.Title -> normalized
            FormField.AuthorLetter,
            FormField.WorkMark,
            -> normalized.firstNormalizedCharacter()
            FormField.MainClass -> normalized.toSpokenDigits().take(3)
            FormField.DecimalPart,
            FormField.AuthorNumber,
            -> normalized.toSpokenDigits().take(8)
            FormField.Volume,
            FormField.CopyNumber,
            -> normalized.toSpokenDigits().take(3)
            FormField.PublicationYear -> normalized.toSpokenDigits().take(4)
        }
    }

    private fun String.toSpokenDigits(): String {
        digitsOnly().takeIf(String::isNotEmpty)?.let { return it }
        val tokens = replace(Regex("[^\u0600-\u06FF\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() && it != "و" }
        if (tokens.isEmpty()) return ""
        if (tokens.all(digitWords::containsKey)) {
            return tokens.joinToString("") { digitWords.getValue(it) }
        }

        var total = 0L
        var current = 0L
        tokens.forEach { token ->
            when (token) {
                "هزار" -> {
                    total += (if (current == 0L) 1L else current) * 1_000L
                    current = 0L
                }
                "میلیون" -> {
                    total = (total + if (current == 0L) 1L else current) * 1_000_000L
                    current = 0L
                }
                else -> current += numberWords[token] ?: return ""
            }
        }
        return (total + current).toString()
    }
}
