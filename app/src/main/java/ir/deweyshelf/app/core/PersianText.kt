package ir.deweyshelf.app.core

private const val PERSIAN_DIGITS = "۰۱۲۳۴۵۶۷۸۹"
private const val ARABIC_DIGITS = "٠١٢٣٤٥٦٧٨٩"

fun String.toLatinDigits(): String = buildString(length) {
    this@toLatinDigits.forEach { char ->
        when {
            char in PERSIAN_DIGITS -> append(PERSIAN_DIGITS.indexOf(char))
            char in ARABIC_DIGITS -> append(ARABIC_DIGITS.indexOf(char))
            else -> append(char)
        }
    }
}

fun String.digitsOnly(): String = toLatinDigits().filter(Char::isDigit)

fun String.toPersianDigits(): String = buildString(length) {
    this@toPersianDigits.forEach { char ->
        if (char in '0'..'9') append(PERSIAN_DIGITS[char.digitToInt()]) else append(char)
    }
}

fun String.normalizePersian(): String =
    replace('ي', 'ی')
        .replace('ى', 'ی')
        .replace('ك', 'ک')
        .replace('ة', 'ه')
        .replace('ۀ', 'ه')
        .replace("ـ", "")
        .replace(Regex("\\s+"), " ")
        .trim()

fun String.firstNormalizedCharacter(): String =
    normalizePersian().replace(" ", "").firstOrNull()?.toString().orEmpty()

fun Int.toPersianNumber(): String = toString().toPersianDigits()

