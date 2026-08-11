package ir.deweyshelf.app.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.deweyshelf.app.R
import ir.deweyshelf.app.core.DeweyColors
import ir.deweyshelf.app.core.DeweySpacing
import ir.deweyshelf.app.core.toPersianNumber
import ir.deweyshelf.app.domain.DeweyBook
import ir.deweyshelf.app.domain.DeweyCatalog
import ir.deweyshelf.app.domain.ShelfPosition

@Composable
fun CallNumberBadge(callNumber: String, modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Text(
                text = callNumber,
                modifier = Modifier.padding(horizontal = DeweySpacing.sm, vertical = DeweySpacing.xs),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun BookRow(
    book: DeweyBook,
    rowNumber: Int,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onCopy: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(DeweySpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DeweySpacing.sm),
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = rowNumber.toPersianNumber(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(DeweySpacing.xxs))
                CallNumberBadge(book.oneLineCallNumber)
                if (!compact) {
                    Spacer(Modifier.height(DeweySpacing.xs))
                    Text(
                        text = DeweyCatalog.classFor(book.mainClass).title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (onEdit != null || onDelete != null || onCopy != null) {
                BookActions(book, onEdit, onDelete, onCopy)
            }
        }
    }
}

@Composable
private fun BookActions(
    book: DeweyBook,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onCopy: (() -> Unit)?,
) {
    var expanded by remember { mutableStateOf(false) }
    val optionsDescription = stringResource(R.string.book_options, book.title)
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics {
                contentDescription = optionsDescription
            },
        ) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more_actions))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (onEdit != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.edit)) },
                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                    onClick = { expanded = false; onEdit() },
                )
            }
            if (onCopy != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.copy)) },
                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                    onClick = { expanded = false; onCopy() },
                )
            }
            if (onDelete != null) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    },
                    onClick = { expanded = false; onDelete() },
                )
            }
        }
    }
}

@Composable
fun ShelfRow(
    position: ShelfPosition,
    reason: String,
    modifier: Modifier = Modifier,
) {
    val movementKind = when {
        position.isCorrect -> BadgeKind.Success
        else -> BadgeKind.Warning
    }
    val movementText = when {
        position.isCorrect -> stringResource(R.string.position_is_correct)
        position.movement > 0 -> stringResource(R.string.move_up, position.movement.toPersianNumber())
        else -> stringResource(R.string.move_down, (-position.movement).toPersianNumber())
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (position.isDuplicate) MaterialTheme.colorScheme.errorContainer.copy(alpha = .25f)
            else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            1.dp,
            if (position.isDuplicate) MaterialTheme.colorScheme.error.copy(alpha = .35f)
            else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Row(
            modifier = Modifier.padding(DeweySpacing.sm),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(DeweySpacing.sm),
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = (position.sortedIndex + 1).toPersianNumber(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = position.book.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (position.isDuplicate) {
                        Spacer(Modifier.width(DeweySpacing.xs))
                        StatusBadge(stringResource(R.string.duplicate_badge), kind = BadgeKind.Error)
                    }
                }
                Spacer(Modifier.height(DeweySpacing.xs))
                CallNumberBadge(position.book.oneLineCallNumber)
                Spacer(Modifier.height(DeweySpacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(DeweySpacing.xs)) {
                    Text(
                        text = stringResource(R.string.original_position, (position.originalIndex + 1).toPersianNumber()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.correct_position, (position.sortedIndex + 1).toPersianNumber()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(DeweySpacing.xs))
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(DeweySpacing.xs))
                StatusBadge(text = movementText, kind = movementKind)
            }
        }
    }
}

@Composable
fun SpinePreview(
    draft: ir.deweyshelf.app.domain.BookDraft,
    modifier: Modifier = Modifier,
) {
    val preview = draft.normalized().copy(
        title = draft.title.ifBlank { stringResource(R.string.book_title_hint) },
        authorLetter = draft.authorLetter.ifBlank { "ح" },
        authorNumber = draft.authorNumber.ifBlank { "۰۰۰" },
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(DeweySpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DeweySpacing.md),
        ) {
            Surface(
                modifier = Modifier.width(112.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
            ) {
                Column(
                    modifier = Modifier.padding(DeweySpacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(preview.deweyNumber, style = MaterialTheme.typography.titleMedium)
                        Text(preview.cutter, style = MaterialTheme.typography.titleMedium)
                    }
                    preview.volume?.let { Text("ج ${it.toPersianNumber()}", style = MaterialTheme.typography.bodySmall) }
                    preview.copyNumber?.let { Text("ن ${it.toPersianNumber()}", style = MaterialTheme.typography.bodySmall) }
                    preview.publicationYear?.let { Text(it.toPersianNumber(), style = MaterialTheme.typography.bodySmall) }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.spine_preview), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(DeweySpacing.xs))
                Text(
                    text = preview.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(DeweySpacing.xs))
                Text(
                    text = DeweyCatalog.classFor(preview.mainClass).title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
