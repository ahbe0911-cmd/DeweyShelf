package ir.deweyshelf.app.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.unit.dp
import ir.deweyshelf.app.R
import ir.deweyshelf.app.core.DeweySpacing
import ir.deweyshelf.app.core.toPersianNumber
import ir.deweyshelf.app.domain.DeweyCatalog
import ir.deweyshelf.app.presentation.components.SectionHeader
import ir.deweyshelf.app.presentation.components.StatusBadge

@Composable
fun GuideScreen(modifier: Modifier = Modifier) {
    val priorities = stringArrayResource(R.array.sorting_priorities)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(DeweySpacing.md),
        verticalArrangement = Arrangement.spacedBy(DeweySpacing.sm),
    ) {
        item {
            SectionHeader(
                title = stringResource(R.string.guide_title),
                subtitle = stringResource(R.string.guide_subtitle),
            )
        }
        item {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(DeweySpacing.md),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(DeweySpacing.sm),
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = stringResource(R.string.sorting_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
        item { SectionHeader(title = stringResource(R.string.sorting_priority)) }
        itemsIndexed(priorities) { index, title ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Row(
                    modifier = Modifier.padding(DeweySpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusBadge((index + 1).toPersianNumber())
                    Spacer(Modifier.width(DeweySpacing.sm))
                    Text(title, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item { SectionHeader(title = stringResource(R.string.main_classes), modifier = Modifier.padding(top = DeweySpacing.sm)) }
        itemsIndexed(DeweyCatalog.classes) { _, item ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Row(
                    modifier = Modifier.padding(DeweySpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DeweySpacing.sm),
                ) {
                    StatusBadge(item.range)
                    Text(item.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
