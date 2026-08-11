package ir.deweyshelf.app.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ir.deweyshelf.app.R
import ir.deweyshelf.app.core.DeweySpacing
import ir.deweyshelf.app.domain.DeweyBook
import ir.deweyshelf.app.presentation.DeweyUiState
import ir.deweyshelf.app.presentation.components.BookRow
import ir.deweyshelf.app.presentation.components.EmptyState
import ir.deweyshelf.app.presentation.components.LoadingState
import ir.deweyshelf.app.presentation.components.SearchField
import ir.deweyshelf.app.presentation.components.SectionHeader

@Composable
fun BooksScreen(
    state: DeweyUiState,
    onQueryChange: (String) -> Unit,
    onAddBook: () -> Unit,
    onEdit: (DeweyBook) -> Unit,
    onDelete: (DeweyBook) -> Unit,
    onCopy: (DeweyBook) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDelete by remember { mutableStateOf<DeweyBook?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(DeweySpacing.md),
        verticalArrangement = Arrangement.spacedBy(DeweySpacing.sm),
    ) {
        item {
            SectionHeader(
                title = stringResource(R.string.books_title),
                subtitle = stringResource(R.string.books_subtitle),
            )
        }
        item { SearchField(value = state.query, onValueChange = onQueryChange) }

        when {
            state.isLoading -> item { LoadingState() }
            state.hasError -> item {
                EmptyState(
                    title = stringResource(R.string.database_error),
                    body = stringResource(R.string.database_error),
                    actionLabel = stringResource(R.string.retry),
                    onAction = onRetry,
                )
            }
            state.books.isEmpty() -> item {
                EmptyState(
                    title = stringResource(R.string.no_books_title),
                    body = stringResource(R.string.no_books_body),
                    icon = Icons.Outlined.LibraryBooks,
                    actionLabel = stringResource(R.string.add_book),
                    onAction = onAddBook,
                )
            }
            state.filteredBooks.isEmpty() -> item {
                EmptyState(
                    title = stringResource(R.string.no_result_title),
                    body = stringResource(R.string.no_result_body),
                    icon = Icons.Outlined.SearchOff,
                    actionLabel = stringResource(R.string.clear_search),
                    onAction = { onQueryChange("") },
                )
            }
            else -> itemsIndexed(state.filteredBooks, key = { _, book -> book.id }) { _, book ->
                BookRow(
                    book = book,
                    rowNumber = state.books.indexOfFirst { it.id == book.id } + 1,
                    onEdit = { onEdit(book) },
                    onDelete = { pendingDelete = book },
                    onCopy = { onCopy(book) },
                )
            }
        }
    }

    pendingDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_book_title)) },
            text = { Text(stringResource(R.string.delete_book_body, book.title)) },
            confirmButton = {
                TextButton(onClick = { pendingDelete = null; onDelete(book) }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

