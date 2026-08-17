package ir.ketabyar.shelf.core

object BookValidator {
    fun validate(code: ShelfCode, title: String, registrationNumber: String, rules: LibraryRules): Map<String, String> = buildMap {
        if (registrationNumber.isBlank()) put("registrationNumber", "شماره ثبت الزامی است")
        if (code.section == BookSection.GENERAL) {
            val main = PersianNormalizer.digitsOnly(code.mainClass)
            if (main.length != 3 || main.toIntOrNull() !in 0..999) put("mainClass", "شماره اصلی باید دقیقاً سه رقم از 000 تا 999 باشد")
            if (code.classDecimal.isNotBlank() && PersianNormalizer.normalize(code.classDecimal).any { !it.isDigit() }) put("classDecimal", "اعشار را فقط با رقم و بدون ممیز وارد کنید")
        } else {
            if (code.languageCode.isBlank()) put("languageCode", "کد زبان ادبی الزامی است")
            if (code.literaturePeriod.isBlank()) put("literaturePeriod", "دوره یا بخش ادبی الزامی است")
            if (!rules.literaturePatternConfirmed) put("literaturePattern", "الگوی ادبیات باید ابتدا توسط کتابدار ارشد تأیید شود")
        }
        if (code.authorNumber.isNotBlank() && PersianNormalizer.normalize(code.authorNumber).any { !it.isDigit() }) put("authorNumber", "عدد مؤلف فقط باید شامل رقم باشد")
        val year = PersianNormalizer.digitsOnly(code.year)
        if (year.isNotEmpty() && (year.length != 4 || year.toInt() !in 1200..2200)) put("year", "سال انتشار معتبر نیست")
    }
}
