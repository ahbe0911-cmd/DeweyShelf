package ir.deweyshelf.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ir.deweyshelf.app.data.BackupCodec
import ir.deweyshelf.app.domain.BookDraft
import ir.deweyshelf.app.domain.BookRepository
import ir.deweyshelf.app.domain.BookValidator
import ir.deweyshelf.app.domain.DeweyBook
import ir.deweyshelf.app.domain.DeweySorter
import ir.deweyshelf.app.domain.ShelfPosition
import ir.deweyshelf.app.domain.ValidationResult
import ir.deweyshelf.app.core.normalizePersian
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DeweyUiState(
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val books: List<DeweyBook> = emptyList(),
    val filteredBooks: List<DeweyBook> = emptyList(),
    val shelfPositions: List<ShelfPosition> = emptyList(),
    val query: String = "",
) {
    val correctCount: Int get() = shelfPositions.count(ShelfPosition::isCorrect)
    val moveCount: Int get() = shelfPositions.size - correctCount
    val duplicateCount: Int get() = DeweySorter.duplicateCount(books)
}

private sealed interface BooksLoadState {
    data object Loading : BooksLoadState
    data class Ready(val books: List<DeweyBook>) : BooksLoadState
    data object Error : BooksLoadState
}

sealed interface SaveResult {
    data class Invalid(val validation: ValidationResult) : SaveResult
    data object Duplicate : SaveResult
    data object Saved : SaveResult
    data object Failed : SaveResult
}

sealed interface AppEvent {
    data object BookSaved : AppEvent
    data object BookUpdated : AppEvent
    data class BookDeleted(val book: DeweyBook) : AppEvent
    data class AllDeleted(val books: List<DeweyBook>) : AppEvent
    data object ImportSucceeded : AppEvent
    data object OperationFailed : AppEvent
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class DeweyViewModel(
    private val repository: BookRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val retrySignal = MutableStateFlow(0)
    private val eventsFlow = MutableSharedFlow<AppEvent>(extraBufferCapacity = 8)
    val events = eventsFlow

    private val booksState = retrySignal
        .flatMapLatest {
            repository.observeBooks()
                .map<List<DeweyBook>, BooksLoadState>(BooksLoadState::Ready)
                .onStart { emit(BooksLoadState.Loading) }
                .catch { emit(BooksLoadState.Error) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, BooksLoadState.Loading)

    private val appliedQuery = query.debounce(180).onStart { emit("") }

    val uiState = combine(booksState, query, appliedQuery) { loadState, visibleQuery, filterQuery ->
        val books = (loadState as? BooksLoadState.Ready)?.books.orEmpty()
        val normalizedQuery = filterQuery.normalizePersian().lowercase()
        val filtered = if (normalizedQuery.isEmpty()) {
            books
        } else {
            books.filter { book ->
                "${book.title} ${book.oneLineCallNumber}"
                    .normalizePersian()
                    .lowercase()
                    .contains(normalizedQuery)
            }
        }
        DeweyUiState(
            isLoading = loadState is BooksLoadState.Loading,
            hasError = loadState is BooksLoadState.Error,
            books = books,
            filteredBooks = filtered,
            shelfPositions = DeweySorter.analyze(books),
            query = visibleQuery,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DeweyUiState(),
    )

    fun setQuery(value: String) {
        query.value = value
    }

    fun retry() {
        retrySignal.value += 1
    }

    fun saveBook(
        draft: BookDraft,
        existing: DeweyBook?,
        allowDuplicate: Boolean = false,
        onResult: (SaveResult) -> Unit,
    ) {
        val validation = BookValidator.validate(draft)
        if (!validation.isValid) {
            onResult(SaveResult.Invalid(validation))
            return
        }

        val normalized = draft.normalized(existing)
        val duplicate = uiState.value.books.any { candidate ->
            candidate.id != normalized.id &&
                DeweySorter.labelKey(candidate) == DeweySorter.labelKey(normalized)
        }
        if (duplicate && !allowDuplicate) {
            onResult(SaveResult.Duplicate)
            return
        }

        viewModelScope.launch {
            runCatching { repository.save(normalized) }
                .onSuccess {
                    eventsFlow.emit(if (existing == null) AppEvent.BookSaved else AppEvent.BookUpdated)
                    onResult(SaveResult.Saved)
                }
                .onFailure {
                    eventsFlow.emit(AppEvent.OperationFailed)
                    onResult(SaveResult.Failed)
                }
        }
    }

    fun deleteBook(book: DeweyBook) {
        viewModelScope.launch {
            runCatching { repository.delete(book) }
                .onSuccess { eventsFlow.emit(AppEvent.BookDeleted(book)) }
                .onFailure { eventsFlow.emit(AppEvent.OperationFailed) }
        }
    }

    fun restoreBook(book: DeweyBook) {
        viewModelScope.launch {
            runCatching { repository.restore(listOf(book)) }
                .onFailure { eventsFlow.emit(AppEvent.OperationFailed) }
        }
    }

    fun deleteAll() {
        val snapshot = uiState.value.books
        if (snapshot.isEmpty()) return
        viewModelScope.launch {
            runCatching { repository.deleteAll() }
                .onSuccess { eventsFlow.emit(AppEvent.AllDeleted(snapshot)) }
                .onFailure { eventsFlow.emit(AppEvent.OperationFailed) }
        }
    }

    fun restoreAll(books: List<DeweyBook>) {
        viewModelScope.launch {
            runCatching { repository.restore(books) }
                .onFailure { eventsFlow.emit(AppEvent.OperationFailed) }
        }
    }

    fun exportJson(): String = BackupCodec.encode(uiState.value.books)

    fun decodeBackup(raw: String, onResult: (Result<List<DeweyBook>>) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) { runCatching { BackupCodec.decode(raw) } }
            onResult(result)
        }
    }

    fun importBooks(books: List<DeweyBook>) {
        viewModelScope.launch {
            runCatching { repository.replaceAll(books) }
                .onSuccess { eventsFlow.emit(AppEvent.ImportSucceeded) }
                .onFailure { eventsFlow.emit(AppEvent.OperationFailed) }
        }
    }

    companion object {
        fun factory(repository: BookRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DeweyViewModel(repository) as T
            }
    }
}
