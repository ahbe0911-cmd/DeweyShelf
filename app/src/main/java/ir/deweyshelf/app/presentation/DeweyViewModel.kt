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
import kotlinx.coroutines.flow.flowOn
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
    val correctCount: Int = 0,
    val moveCount: Int = 0,
    val duplicateCount: Int = 0,
)

private data class PreparedCatalog(
    val books: List<DeweyBook>,
    val shelfPositions: List<ShelfPosition>,
    val searchableText: List<String>,
    val correctCount: Int,
    val duplicateCount: Int,
)

private sealed interface CatalogLoadState {
    data object Loading : CatalogLoadState
    data class Ready(val catalog: PreparedCatalog) : CatalogLoadState
    data object Error : CatalogLoadState
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

    private val catalogState = retrySignal
        .flatMapLatest {
            repository.observeBooks()
                .map<List<DeweyBook>, CatalogLoadState> { books ->
                    val shelfPositions = DeweySorter.analyze(books)
                    CatalogLoadState.Ready(
                        PreparedCatalog(
                            books = books,
                            shelfPositions = shelfPositions,
                            searchableText = books.map { book ->
                                "${book.title} ${book.oneLineCallNumber}"
                                    .normalizePersian()
                                    .lowercase()
                            },
                            correctCount = shelfPositions.count(ShelfPosition::isCorrect),
                            duplicateCount = DeweySorter.duplicateCount(books),
                        ),
                    )
                }
                .onStart { emit(CatalogLoadState.Loading) }
                .catch { emit(CatalogLoadState.Error) }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, CatalogLoadState.Loading)

    private val appliedQuery = query.debounce(180).onStart { emit("") }

    val uiState = combine(catalogState, query, appliedQuery) { loadState, visibleQuery, filterQuery ->
        val catalog = (loadState as? CatalogLoadState.Ready)?.catalog
        val books = catalog?.books.orEmpty()
        val normalizedQuery = filterQuery.normalizePersian().lowercase()
        val filtered = if (normalizedQuery.isEmpty()) {
            books
        } else {
            books.filterIndexed { index, _ ->
                catalog?.searchableText?.get(index)?.contains(normalizedQuery) == true
            }
        }
        val correctCount = catalog?.correctCount ?: 0
        DeweyUiState(
            isLoading = loadState is CatalogLoadState.Loading,
            hasError = loadState is CatalogLoadState.Error,
            books = books,
            filteredBooks = filtered,
            shelfPositions = catalog?.shelfPositions.orEmpty(),
            query = visibleQuery,
            correctCount = correctCount,
            moveCount = books.size - correctCount,
            duplicateCount = catalog?.duplicateCount ?: 0,
        )
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
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
