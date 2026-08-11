package ir.deweyshelf.app.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ir.deweyshelf.app.R
import ir.deweyshelf.app.core.DeweySpacing
import ir.deweyshelf.app.core.toPersianNumber
import ir.deweyshelf.app.domain.SortReason
import ir.deweyshelf.app.presentation.DeweyUiState
import ir.deweyshelf.app.presentation.components.EmptyState
import ir.deweyshelf.app.presentation.components.LoadingState
import ir.deweyshelf.app.presentation.components.SectionHeader
import ir.deweyshelf.app.presentation.components.ShelfRow
import ir.deweyshelf.app.presentation.components.StatTile

@Composable
fun ShelfScreen(
    state: DeweyUiState,
    onAddBook: () -> Unit,
    onCopyOrder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(DeweySpacing.md),
        verticalArrangement = Arrangement.spacedBy(DeweySpacing.sm),
    ) {
        item {
            SectionHeader(
                title = stringResource(R.string.shelf_title),
                subtitle = stringResource(R.string.shelf_subtitle),
                action = if (state.books.isNotEmpty()) {
                    {
                        TextButton(onClick = onCopyOrder) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                            Text(stringResource(R.string.copy))
                        }
                    }
                } else null,
            )
        }

        if (state.isLoading) {
            item { LoadingState() }
        } else if (state.books.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(R.string.empty_shelf_title),
                    body = stringResource(R.string.empty_shelf_body),
                    icon = Icons.Outlined.SwapVert,
                    actionLabel = stringResource(R.string.add_book),
                    onAction = onAddBook,
                )
            }
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
                        value = state.moveCount.toPersianNumber(),
                        label = stringResource(R.string.needs_move),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            items(state.shelfPositions, key = { it.book.id }) { position ->
                ShelfRow(position = position, reason = sortReasonText(position.reason))
            }
        }
    }
}

@Composable
private fun sortReasonText(reason: SortReason): String = stringResource(
    when (reason) {
        SortReason.Start -> R.string.sort_reason_start
        SortReason.MainClass -> R.string.sort_reason_main
        SortReason.DecimalPart -> R.string.sort_reason_decimal
        SortReason.AuthorLetter -> R.string.sort_reason_author_letter
        SortReason.AuthorNumber -> R.string.sort_reason_author_number
        SortReason.WorkMark -> R.string.sort_reason_work
        SortReason.Volume -> R.string.sort_reason_volume
        SortReason.CopyNumber -> R.string.sort_reason_copy
        SortReason.PublicationYear -> R.string.sort_reason_year
        SortReason.Title -> R.string.sort_reason_title
    },
)

