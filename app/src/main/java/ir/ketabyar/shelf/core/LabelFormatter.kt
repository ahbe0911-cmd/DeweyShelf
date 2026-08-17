package ir.ketabyar.shelf.core

object LabelFormatter {
    fun format(code: ShelfCode, rules: LibraryRules): String {
        val line1: String
        val line2: String
        if (code.section == BookSection.GENERAL) {
            line1 = PersianNormalizer.normalize(code.mainClass)
            line2 = if (code.classDecimal.isNotBlank()) rules.separator + PersianNormalizer.digitsOnly(code.classDecimal) else ""
        } else {
            line1 = PersianNormalizer.normalize(code.languageCode)
            line2 = rules.separator + PersianNormalizer.digitsOnly(code.literaturePeriod)
        }
        val extras = listOf(code.volume.takeIf { it.isNotBlank() }?.let { "ج.$it" }, code.edition.takeIf { it.isNotBlank() }?.let { "ن.$it" }).filterNotNull()
        return buildList {
            add(line1)
            add(line2)
            add(retrieval(code))
            if (extras.isNotEmpty()) add(extras.joinToString(" "))
        }.filter { it.isNotBlank() }.joinToString("\n")
    }

    private fun retrieval(code: ShelfCode) = listOf(code.authorLetter, PersianNormalizer.digitsOnly(code.authorNumber), code.workMark, code.titleLetter)
        .joinToString("") { PersianNormalizer.normalize(it) }
}
