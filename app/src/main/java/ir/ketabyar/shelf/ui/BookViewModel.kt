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
    val editingId: Long? = null,
    val section: BookSection = BookSection.GENERAL,
    val title: String = "", val authorFirst: String = "", val authorLast: String = "", val subject: String = "", val registration: String = "",
    val mainClass: String = "", val classDecimal: String = "", val language: String = "", val languageCode: String = "فا", val literaturePeriod: String = "", val workType: String = "",
    val authorLetter: String = "", val authorNumber: String = "", val workMark: String = "", val titleLetter: String = "",
    val volume: String = "", val edition: String = "", val year: String = "", val notes: String = "", val shelf: String = "", val row: String = "",
    val errors: Map<String, String> = emptyMap(), val savedMessage: String? = null,
    val shelved: Boolean = false,
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
    fun edit(book: BookEntity) {
        _form.value = BookFormState(editingId=book.id,section=book.section,title=book.title,authorFirst=book.authorFirstName,authorLast=book.authorLastName,subject=book.subject,registration=book.registrationNumber,mainClass=book.mainClass,classDecimal=book.classDecimal,language=book.language,languageCode=book.languageCode,literaturePeriod=book.literaturePeriod,workType=book.workType,authorLetter=book.authorLetter,authorNumber=book.authorNumber,workMark=book.workMark,titleLetter=book.titleLetter,volume=book.volume,edition=book.edition,year=book.year,notes=book.notes,shelf=book.shelfName,row=book.rowNumber?.toString().orEmpty(),shelved=book.shelved)
    }
    fun delete(book: BookEntity) = viewModelScope.launch { repository.delete(book) }
    fun deleteAll() = viewModelScope.launch { repository.deleteAll() }
    fun setShelved(book:BookEntity,value:Boolean)=viewModelScope.launch{repository.setShelved(book,value)}
    fun setSeparator(value: String) = viewModelScope.launch { settings.setSeparator(value) }
    fun confirmLiterature(value: Boolean) = viewModelScope.launch { settings.confirmLiterature(value) }
    fun clearSavedMessage() { _form.update { it.copy(savedMessage = null) } }
    fun save() = viewModelScope.launch {
        val f = _form.value; val errors = BookValidator.validate(f.code(), f.title, f.registration, rules.value).toMutableMap()
        if (repository.isDuplicate(f.registration, f.editingId)) errors["registrationNumber"] = "این شماره ثبت قبلاً ذخیره شده است"
        if (errors.isNotEmpty()) { _form.update { it.copy(errors = errors) }; return@launch }
        if(f.editingId==null) repository.add(f.toEntity()) else repository.update(f.toEntity().copy(id=f.editingId))
        val ordered = books.value + f.toEntity()
        val sorted = ordered.sortedWith(compareByCode(rules.value)); val index = sorted.indexOfFirst { it.registrationNumber == f.registration }
        val prev = sorted.getOrNull(index - 1)?.title; val next = sorted.getOrNull(index + 1)?.title
        var nextRegistration: String
        do { nextRegistration = Random.nextInt(100000, 999999).toString() } while (repository.isDuplicate(nextRegistration))
        _form.value = BookFormState(
            section = f.section,
            registration = nextRegistration,
            savedMessage = if(f.editingId==null) "کتاب ذخیره شد" else "ویرایش ذخیره شد",
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
    needsReview = section == BookSection.LITERATURE,shelved=shelved)

private fun BookEntity.code() = ShelfCode(section, mainClass, classDecimal, languageCode, literaturePeriod, authorLetter, authorNumber, workMark, titleLetter, volume, edition, year)
