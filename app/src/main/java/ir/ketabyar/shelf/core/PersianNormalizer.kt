package ir.ketabyar.shelf.core

object PersianNormalizer {
    private val digits = mapOf(
        '۰' to '0', '۱' to '1', '۲' to '2', '۳' to '3', '۴' to '4',
        '۵' to '5', '۶' to '6', '۷' to '7', '۸' to '8', '۹' to '9',
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
        '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9'
    )

    fun normalize(value: String): String = buildString {
        value.trim().forEach { char ->
            val c = digits[char] ?: when (char) {
                'ي', 'ى' -> 'ی'
                'ك' -> 'ک'
                '\u200c', '\u200d', '\u200e', '\u200f' -> return@forEach
                else -> char
            }
            if (!c.isWhitespace()) append(c)
        }
    }

    fun digitsOnly(value: String) = normalize(value).filter(Char::isDigit)
    fun normalizedInteger(value: String) = digitsOnly(value).trimStart('0').ifEmpty { "0" }
}

