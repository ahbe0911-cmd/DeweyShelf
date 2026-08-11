package ir.deweyshelf.app.domain

import ir.deweyshelf.app.core.digitsOnly
import ir.deweyshelf.app.core.firstNormalizedCharacter
import ir.deweyshelf.app.core.normalizePersian
import ir.deweyshelf.app.core.toPersianDigits
import kotlin.random.Random

object BookIdentifierGenerator {
    fun next(random: Random = Random.Default): String =
        "کتاب ${random.nextInt(from = 100_000, until = 1_000_000).toString().toPersianDigits()}"
}

data class DeweyBook(
    val id: Long = 0,
    val title: String,
    val mainClass: Int,
    val decimalPart: String = "",
    val authorLetter: String,
    val authorNumber: String,
    val workMark: String = "",
    val volume: Int? = null,
    val copyNumber: Int? = null,
    val publicationYear: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val deweyNumber: String
        get() = "%03d%s".format(
            mainClass,
            decimalPart.takeIf(String::isNotEmpty)?.let { "/$it" }.orEmpty(),
        ).toPersianDigits()

    val cutter: String
        get() = buildString {
            append(authorLetter)
            append(authorNumber.toPersianDigits())
            append(workMark)
        }

    val oneLineCallNumber: String
        get() = buildList {
            add(deweyNumber)
            add(cutter)
            volume?.let { add("ج ${it.toString().toPersianDigits()}") }
            copyNumber?.let { add("ن ${it.toString().toPersianDigits()}") }
            publicationYear?.let { add(it.toString().toPersianDigits()) }
        }.joinToString(" | ")

    val multilineCallNumber: String
        get() = buildList {
            add(deweyNumber)
            add(cutter)
            volume?.let { add("جلد ${it.toString().toPersianDigits()}") }
            copyNumber?.let { add("نسخه ${it.toString().toPersianDigits()}") }
            publicationYear?.let { add(it.toString().toPersianDigits()) }
        }.joinToString("\n")
}

data class BookDraft(
    val title: String = "",
    val mainClass: String = "",
    val decimalPart: String = "",
    val authorLetter: String = "",
    val authorNumber: String = "",
    val workMark: String = "",
    val volume: String = "",
    val copyNumber: String = "",
    val publicationYear: String = "",
) {
    fun normalized(existing: DeweyBook? = null): DeweyBook = DeweyBook(
        id = existing?.id ?: 0,
        title = title.normalizePersian(),
        mainClass = mainClass.digitsOnly().toIntOrNull() ?: 0,
        decimalPart = decimalPart.digitsOnly(),
        authorLetter = authorLetter.firstNormalizedCharacter(),
        authorNumber = authorNumber.digitsOnly(),
        workMark = workMark.firstNormalizedCharacter(),
        volume = volume.digitsOnly().toIntOrNull(),
        copyNumber = copyNumber.digitsOnly().toIntOrNull(),
        publicationYear = publicationYear.digitsOnly().toIntOrNull(),
        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
    )

    companion object {
        fun from(book: DeweyBook): BookDraft = BookDraft(
            title = book.title,
            mainClass = "%03d".format(book.mainClass).toPersianDigits(),
            decimalPart = book.decimalPart.toPersianDigits(),
            authorLetter = book.authorLetter,
            authorNumber = book.authorNumber.toPersianDigits(),
            workMark = book.workMark,
            volume = book.volume?.toString()?.toPersianDigits().orEmpty(),
            copyNumber = book.copyNumber?.toString()?.toPersianDigits().orEmpty(),
            publicationYear = book.publicationYear?.toString()?.toPersianDigits().orEmpty(),
        )
    }
}

enum class FormField {
    Title,
    MainClass,
    DecimalPart,
    AuthorLetter,
    AuthorNumber,
    WorkMark,
    Volume,
    CopyNumber,
    PublicationYear,
}

enum class ValidationError { Required, InvalidMainClass, InvalidNumber }

data class ValidationResult(val errors: Map<FormField, ValidationError>) {
    val isValid: Boolean get() = errors.isEmpty()
}

enum class SortReason {
    Start,
    MainClass,
    DecimalPart,
    AuthorLetter,
    AuthorNumber,
    WorkMark,
    Volume,
    CopyNumber,
    PublicationYear,
    Title,
}

data class ShelfPosition(
    val book: DeweyBook,
    val originalIndex: Int,
    val sortedIndex: Int,
    val reason: SortReason,
    val isDuplicate: Boolean,
) {
    val movement: Int get() = originalIndex - sortedIndex
    val isCorrect: Boolean get() = movement == 0
}

data class DeweyClassInfo(
    val range: String,
    val title: String,
    val start: Int,
    val endInclusive: Int,
)
