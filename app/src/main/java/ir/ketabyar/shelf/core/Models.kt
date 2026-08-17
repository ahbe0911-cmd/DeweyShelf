package ir.ketabyar.shelf.core

enum class BookSection { GENERAL, LITERATURE }

data class ShelfCode(
    val section: BookSection,
    val mainClass: String = "",
    val classDecimal: String = "",
    val languageCode: String = "",
    val literaturePeriod: String = "",
    val authorLetter: String = "",
    val authorNumber: String = "",
    val workMark: String = "",
    val titleLetter: String = "",
    val volume: String = "",
    val edition: String = "",
    val year: String = ""
)

data class LibraryRules(
    val separator: String = "/",
    val persianAlphabet: String = "اآبپتثجچحخدذرزژسشصضطظعغفقکگلمنوهی",
    val emptyComponentFirst: Boolean = true,
    val volumeAffectsOrder: Boolean = true,
    val editionAffectsOrder: Boolean = true,
    val yearAffectsOrder: Boolean = true,
    val literaturePatternConfirmed: Boolean = false
)

data class PlacementResult(
    val previousTitle: String?,
    val nextTitle: String?,
    val shelfName: String?,
    val rowNumber: Int?,
    val reason: String,
    val isFirst: Boolean,
    val isLast: Boolean
)

