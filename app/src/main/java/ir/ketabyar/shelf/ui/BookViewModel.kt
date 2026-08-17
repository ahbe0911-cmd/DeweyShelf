package ir.ketabyar.shelf.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.ketabyar.shelf.core.*
import ir.ketabyar.shelf.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.random.Random
import javax.inject.Inject

data class BookFormState(
    val section: BookSection = BookSection.GENERAL,
    val title: String = "", val authorFirst: String = "", val authorLast: String = "", val subject: String = "", val registration: String = "",
    val mainClass: String = "", val classDecimal: String = "", val language: String = "", val languageCode: String = "8فا", val literaturePeriod: String = "32", val workType: String = "",
    val authorLetter: String = "", val authorNumber: String = "", val workMark: String = "", val titleLetter: String = "",
    val volume: String = "", val edition: String = "", val year: String = "", val notes: String = "", val shelf: String = "", val row: String = "",
    val errors: Map<String, String> = emptyMap(), val savedMessage: String? = null,
    val previousTitle: String? = null, val nextTitle: String? = null
) {
    fun code() = ShelfCode(section, mainClass, classDecimal, languageCode, literaturePeriod, authorLetter, authorNumber, workMark, titleLetter, volume, edition, year)
}

@HiltViewModel
class BookViewModel @Inject constructor(private val repository: BookRepository, private val settings: SettingsStore) : ViewModel() {
    private val _form = MutableStateFlow(BookFormState())
    val form = _form.asStateFlow()
    val rules = settings.rules.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryRules())
    val books = combine(repository.books, rules) { list, r -> list.sortedWith(compareByCode(r)) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun start(section: BookSection) {
        _form.value = BookFormState(section = section)
        viewModelScope.launch {
            var number: String
            do { number = Random.nextInt(100000, 999999).toString() } while (repository.isDuplicate(number))
            _form.update { it.copy(registration = number) }
        }
    }
    fun change(transform: (BookFormState) -> BookFormState) { _form.update(transform) }
    fun setSeparator(value: String) = viewModelScope.launch { settings.setSeparator(value) }
    fun confirmLiterature(value: Boolean) = viewModelScope.launch { settings.confirmLiterature(value) }
    fun clearSavedMessage() { _form.update { it.copy(savedMessage = null) } }
    fun save() = viewModelScope.launch {
        val f = _form.value; val errors = BookValidator.validate(f.code(), f.title, f.registration, rules.value).toMutableMap()
        if (repository.isDuplicate(f.registration)) errors["registrationNumber"] = "این شماره ثبت قبلاً ذخیره شده است"
        if (errors.isNotEmpty()) { _form.update { it.copy(errors = errors) }; return@launch }
        repository.add(f.toEntity())
        val ordered = books.value + f.toEntity()
        val sorted = ordered.sortedWith(compareByCode(rules.value)); val index = sorted.indexOfFirst { it.registrationNumber == f.registration }
        val prev = sorted.getOrNull(index - 1)?.title; val next = sorted.getOrNull(index + 1)?.title
        var nextRegistration: String
        do { nextRegistration = Random.nextInt(100000, 999999).toString() } while (repository.isDuplicate(nextRegistration))
        _form.value = BookFormState(
            section = f.section,
            registration = nextRegistration,
            savedMessage = "کتاب ذخیره شد",
            previousTitle = prev,
            nextTitle = next
        )
    }

    private fun compareByCode(rules: LibraryRules) = Comparator<BookEntity> { a, b -> ShelfCodeComparator(rules).compare(a.code(), b.code()) }
}

private fun BookFormState.toEntity() = BookEntity(section = section, title = title.trim(), authorFirstName = authorFirst, authorLastName = authorLast, subject = subject,
    registrationNumber = registration, mainClass = mainClass, classDecimal = classDecimal, language = language, languageCode = languageCode,
    literaturePeriod = literaturePeriod, workType = workType, authorLetter = authorLetter, authorNumber = authorNumber, workMark = workMark,
    titleLetter = titleLetter, volume = volume, edition = edition, year = year, notes = notes, shelfName = shelf, rowNumber = row.toIntOrNull(),
    needsReview = section == BookSection.LITERATURE)

private fun BookEntity.code() = ShelfCode(section, mainClass, classDecimal, languageCode, literaturePeriod, authorLetter, authorNumber, workMark, titleLetter, volume, edition, year)
