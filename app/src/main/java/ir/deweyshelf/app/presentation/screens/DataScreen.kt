package ir.deweyshelf.app.presentation.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.deweyshelf.app.R
import ir.deweyshelf.app.core.DeweySpacing
import ir.deweyshelf.app.core.toPersianNumber
import ir.deweyshelf.app.data.BackupFileStore
import ir.deweyshelf.app.domain.DeweyBook
import ir.deweyshelf.app.presentation.DeweyUiState
import ir.deweyshelf.app.presentation.components.BadgeKind
import ir.deweyshelf.app.presentation.components.SectionHeader
import ir.deweyshelf.app.presentation.components.StatusBadge
import kotlinx.coroutines.launch

@Composable
fun DataScreen(
    state: DeweyUiState,
    exportJson: () -> String,
    decodeBackup: (String, (Result<List<DeweyBook>>) -> Unit) -> Unit,
    onImport: (List<DeweyBook>) -> Unit,
    onDeleteAll: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingImport by remember { mutableStateOf<List<DeweyBook>?>(null) }
    var showDeleteAll by remember { mutableStateOf(false) }
    val saveSuccess = stringResource(R.string.backup_saved)
    val saveFailed = stringResource(R.string.backup_failed)
    val invalidBackup = stringResource(R.string.import_failed)

    val createBackup = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val success = BackupFileStore.write(context.contentResolver, uri, exportJson())
                onMessage(if (success) saveSuccess else saveFailed)
            }
        }
    }

    val openBackup = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val raw = BackupFileStore.read(context.contentResolver, uri)
                if (raw == null) {
                    onMessage(invalidBackup)
                } else {
                    decodeBackup(raw) { result ->
                        result.onSuccess { pendingImport = it }
                            .onFailure { onMessage(invalidBackup) }
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(DeweySpacing.md),
        verticalArrangement = Arrangement.spacedBy(DeweySpacing.md),
    ) {
        SectionHeader(
            title = stringResource(R.string.data_title),
            subtitle = stringResource(R.string.data_subtitle),
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Column(
                modifier = Modifier.padding(DeweySpacing.md),
                verticalArrangement = Arrangement.spacedBy(DeweySpacing.sm),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(DeweySpacing.sm))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.offline_badge), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.database_status),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    StatusBadge(state.books.size.toPersianNumber(), kind = BadgeKind.Success)
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(
                modifier = Modifier.padding(DeweySpacing.md),
                verticalArrangement = Arrangement.spacedBy(DeweySpacing.md),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(DeweySpacing.sm))
                    Text(stringResource(R.string.backup_description), style = MaterialTheme.typography.bodyMedium)
                }
                Button(
                    onClick = { createBackup.launch(context.getString(R.string.backup_file_name)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.FileUpload, contentDescription = null)
                    Spacer(Modifier.width(DeweySpacing.xs))
                    Text(stringResource(R.string.export_backup))
                }
                OutlinedButton(
                    onClick = { openBackup.launch(arrayOf("application/json", "text/plain")) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.FileDownload, contentDescription = null)
                    Spacer(Modifier.width(DeweySpacing.xs))
                    Text(stringResource(R.string.import_backup))
                }
                HorizontalDivider()
                OutlinedButton(
                    onClick = { showDeleteAll = true },
                    enabled = state.books.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = null)
                    Spacer(Modifier.width(DeweySpacing.xs))
                    Text(stringResource(R.string.delete_all))
                }
            }
        }

        Text(
            text = stringResource(R.string.app_version),
            modifier = Modifier.align(Alignment.CenterHorizontally),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    pendingImport?.let { books ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.import_confirm_title)) },
            text = { Text(stringResource(R.string.import_confirm_body, books.size.toPersianNumber())) },
            confirmButton = {
                TextButton(onClick = { pendingImport = null; onImport(books) }) {
                    Text(stringResource(R.string.replace_data))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showDeleteAll) {
        AlertDialog(
            onDismissRequest = { showDeleteAll = false },
            title = { Text(stringResource(R.string.delete_all_title)) },
            text = { Text(stringResource(R.string.delete_all_body)) },
            confirmButton = {
                TextButton(onClick = { showDeleteAll = false; onDeleteAll() }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAll = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

