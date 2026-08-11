package ir.deweyshelf.app.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.deweyshelf.app.R
import ir.deweyshelf.app.core.DeweyColors
import ir.deweyshelf.app.core.DeweySpacing
import ir.deweyshelf.app.core.toPersianNumber
import ir.deweyshelf.app.presentation.DeweyUiState
import ir.deweyshelf.app.presentation.components.BookRow
import ir.deweyshelf.app.presentation.components.EmptyState
import ir.deweyshelf.app.presentation.components.LoadingState
import ir.deweyshelf.app.presentation.components.SectionHeader
import ir.deweyshelf.app.presentation.components.StatTile

@Composable
fun HomeScreen(
    state: DeweyUiState,
    onAddBook: () -> Unit,
    onOpenBooks: () -> Unit,
    onOpenShelf: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(DeweySpacing.md),
        verticalArrangement = Arrangement.spacedBy(DeweySpacing.md),
    ) {
        item {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.large,
            ) {
                Column(modifier = Modifier.padding(DeweySpacing.xl)) {
                    Icon(Icons.Outlined.AutoStories, contentDescription = null)
                    Spacer(Modifier.height(DeweySpacing.md))
                    Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(DeweySpacing.xs))
                    Text(
                        stringResource(R.string.home_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .86f),
                    )
                    Spacer(Modifier.height(DeweySpacing.lg))
                    Button(
                        onClick = onAddBook,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DeweyColors.Accent,
                            contentColor = MaterialTheme.colorScheme.onTertiary,
                        ),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Spacer(Modifier.padding(horizontal = DeweySpacing.xxs))
                        Text(stringResource(R.string.add_book))
                    }
                }
            }
        }

        if (state.isLoading) {
            item { LoadingState() }
        } else {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(DeweySpacing.xs),
                ) {
                    StatTile(
                        value = state.books.size.toPersianNumber(),
                        label = stringResource(R.string.total_books),
                        modifier = Modifier.weight(1f),
                        accent = true,
                    )
                    StatTile(
                        value = state.correctCount.toPersianNumber(),
                        label = stringResource(R.string.correct_positions),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        value = state.duplicateCount.toPersianNumber(),
                        label = stringResource(R.string.duplicates),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (state.books.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.no_books_title),
                        body = stringResource(R.string.no_books_body),
                        actionLabel = stringResource(R.string.add_book),
                        onAction = onAddBook,
                    )
                }
            } else {
                item {
                    OutlinedButton(
                        onClick = onOpenShelf,
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .35f)),
                    ) {
                        Text(stringResource(R.string.view_shelf), modifier = Modifier.weight(1f))
                        Icon(Icons.Outlined.ChevronLeft, contentDescription = null)
                    }
                }
                item {
                    SectionHeader(
                        title = stringResource(R.string.recent_books),
                        action = {
                            androidx.compose.material3.TextButton(onClick = onOpenBooks) {
                                Text(stringResource(R.string.view_all))
                            }
                        },
                    )
                }
                itemsIndexed(state.books.takeLast(3).reversed(), key = { _, book -> book.id }) { index, book ->
                    BookRow(
                        book = book,
                        rowNumber = state.books.indexOfFirst { it.id == book.id } + 1,
                        compact = true,
                    )
                }
            }
        }
    }
}

