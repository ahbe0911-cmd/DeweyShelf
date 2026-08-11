package ir.deweyshelf.app.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.deweyshelf.app.R
import ir.deweyshelf.app.core.DeweySpacing
import ir.deweyshelf.app.domain.BookIdentifierGenerator
import ir.deweyshelf.app.domain.BookDraft
import ir.deweyshelf.app.domain.DeweyBook
import ir.deweyshelf.app.domain.FormField
import ir.deweyshelf.app.domain.ValidationError
import ir.deweyshelf.app.presentation.SaveResult
import ir.deweyshelf.app.presentation.components.SectionHeader
import ir.deweyshelf.app.presentation.components.SpinePreview
import kotlinx.coroutines.delay

private val BookDraftSaver = listSaver<BookDraft, String>(
    save = {
        listOf(
            it.title,
            it.mainClass,
            it.decimalPart,
            it.authorLetter,
            it.authorNumber,
            it.workMark,
            it.volume,
            it.copyNumber,
            it.publicationYear,
        )
    },
    restore = {
        BookDraft(
            title = it[0],
            mainClass = it[1],
            decimalPart = it[2],
            authorLetter = it[3],
            authorNumber = it[4],
            workMark = it[5],
            volume = it[6],
            copyNumber = it[7],
            publicationYear = it[8],
        )
    },
)

@Composable
fun BookEditorScreen(
    existing: DeweyBook?,
    onSave: (BookDraft, Boolean, (SaveResult) -> Unit) -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by rememberSaveable(existing?.id, stateSaver = BookDraftSaver) {
        mutableStateOf(
            existing?.let { BookDraft.from(it) }
                ?: BookDraft(title = BookIdentifierGenerator.next()),
        )
    }
    var errors by remember { mutableStateOf<Map<FormField, ValidationError>>(emptyMap()) }
    var showDuplicateDialog by rememberSaveable { mutableStateOf(false) }
    var isSaving by rememberSaveable { mutableStateOf(false) }

    fun submit(allowDuplicate: Boolean = false) {
        if (isSaving) return
        isSaving = true
        onSave(draft, allowDuplicate) { result ->
            isSaving = false
            when (result) {
                is SaveResult.Invalid -> errors = result.validation.errors
                SaveResult.Duplicate -> showDuplicateDialog = true
                SaveResult.Saved -> onSaved()
                SaveResult.Failed -> Unit
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(
                tonalElevation = 2.dp,
                shadowElevation = 2.dp,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Button(
                    onClick = { submit() },
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = DeweySpacing.md, vertical = DeweySpacing.sm),
                    contentPadding = PaddingValues(vertical = DeweySpacing.sm),
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(DeweySpacing.xs))
                        Text(stringResource(R.string.saving))
                    } else {
                        Text(stringResource(if (existing == null) R.string.save_book else R.string.save_changes))
                    }
                }
            }
        },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(DeweySpacing.md),
        ) {
            val form: @Composable (Modifier) -> Unit = { childModifier ->
                FormContent(
                    draft = draft,
                    errors = errors,
                    onChange = { field, value ->
                        draft = draft.updated(field, value)
                        errors = errors - field
                    },
                    onDone = { submit() },
                    isNewBook = existing == null,
                    modifier = childModifier,
                )
            }

            if (maxWidth >= 680.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(DeweySpacing.lg)) {
                    form(Modifier.weight(1f))
                    SpinePreview(draft = draft, modifier = Modifier.width(300.dp))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(DeweySpacing.md)) {
                    SpinePreview(draft = draft)
                    form(Modifier.fillMaxWidth())
                }
            }
        }
    }

    if (showDuplicateDialog) {
        AlertDialog(
            onDismissRequest = { showDuplicateDialog = false },
            title = { Text(stringResource(R.string.duplicate_title)) },
            text = { Text(stringResource(R.string.duplicate_body)) },
            confirmButton = {
                TextButton(onClick = { showDuplicateDialog = false; submit(allowDuplicate = true) }) {
                    Text(stringResource(R.string.save_anyway))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun FormContent(
    draft: BookDraft,
    errors: Map<FormField, ValidationError>,
    onChange: (FormField, String) -> Unit,
    onDone: () -> Unit,
    isNewBook: Boolean,
    modifier: Modifier = Modifier,
) {
    val titleFocus = remember { FocusRequester() }
    val mainClassFocus = remember { FocusRequester() }
    val decimalFocus = remember { FocusRequester() }
    val authorLetterFocus = remember { FocusRequester() }
    val authorNumberFocus = remember { FocusRequester() }
    val workMarkFocus = remember { FocusRequester() }
    val volumeFocus = remember { FocusRequester() }
    val copyFocus = remember { FocusRequester() }
    val yearFocus = remember { FocusRequester() }

    LaunchedEffect(isNewBook) {
        if (isNewBook) {
            delay(250)
            mainClassFocus.requestFocus()
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DeweySpacing.md)) {
        FormSection(title = stringResource(R.string.book_information)) {
            DeweyTextField(
                value = draft.title,
                onValueChange = { onChange(FormField.Title, it) },
                label = stringResource(R.string.book_title_label),
                placeholder = stringResource(R.string.book_title_hint),
                helper = if (isNewBook) stringResource(R.string.random_book_helper) else null,
                error = errors[FormField.Title],
                focusRequester = titleFocus,
                nextFocusRequester = mainClassFocus,
            )
        }

        FormSection(title = stringResource(R.string.call_number_information)) {
            Row(horizontalArrangement = Arrangement.spacedBy(DeweySpacing.sm)) {
                DeweyTextField(
                    value = draft.mainClass,
                    onValueChange = { onChange(FormField.MainClass, it.take(3)) },
                    label = stringResource(R.string.main_class_label),
                    placeholder = stringResource(R.string.main_class_hint),
                    helper = stringResource(R.string.main_class_helper),
                    keyboardType = KeyboardType.Number,
                    error = errors[FormField.MainClass],
                    focusRequester = mainClassFocus,
                    nextFocusRequester = decimalFocus,
                    autoAdvanceLength = 3,
                    autoAdvanceAfterPause = true,
                    modifier = Modifier.weight(1f),
                )
                DeweyTextField(
                    value = draft.decimalPart,
                    onValueChange = { onChange(FormField.DecimalPart, it.take(8)) },
                    label = stringResource(R.string.decimal_label),
                    placeholder = stringResource(R.string.decimal_hint),
                    helper = stringResource(R.string.decimal_helper),
                    keyboardType = KeyboardType.Number,
                    error = errors[FormField.DecimalPart],
                    focusRequester = decimalFocus,
                    nextFocusRequester = authorLetterFocus,
                    autoAdvanceAfterPause = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(DeweySpacing.sm)) {
                DeweyTextField(
                    value = draft.authorLetter,
                    onValueChange = { onChange(FormField.AuthorLetter, it.take(1)) },
                    label = stringResource(R.string.author_letter_label),
                    placeholder = stringResource(R.string.author_letter_hint),
                    error = errors[FormField.AuthorLetter],
                    focusRequester = authorLetterFocus,
                    nextFocusRequester = authorNumberFocus,
                    autoAdvanceLength = 1,
                    modifier = Modifier.weight(1f),
                )
                DeweyTextField(
                    value = draft.authorNumber,
                    onValueChange = { onChange(FormField.AuthorNumber, it.take(8)) },
                    label = stringResource(R.string.author_number_label),
                    placeholder = stringResource(R.string.author_number_hint),
                    helper = stringResource(R.string.author_number_helper),
                    keyboardType = KeyboardType.Number,
                    error = errors[FormField.AuthorNumber],
                    focusRequester = authorNumberFocus,
                    nextFocusRequester = workMarkFocus,
                    autoAdvanceAfterPause = true,
                    modifier = Modifier.weight(1f),
                )
            }
            DeweyTextField(
                value = draft.workMark,
                onValueChange = { onChange(FormField.WorkMark, it.take(1)) },
                label = stringResource(R.string.work_mark_label),
                placeholder = stringResource(R.string.work_mark_hint),
                error = errors[FormField.WorkMark],
                focusRequester = workMarkFocus,
                nextFocusRequester = volumeFocus,
                autoAdvanceLength = 1,
            )
        }

        FormSection(title = stringResource(R.string.optional_information)) {
            Row(horizontalArrangement = Arrangement.spacedBy(DeweySpacing.sm)) {
                DeweyTextField(
                    value = draft.volume,
                    onValueChange = { onChange(FormField.Volume, it.take(3)) },
                    label = stringResource(R.string.volume_label),
                    keyboardType = KeyboardType.Number,
                    error = errors[FormField.Volume],
                    focusRequester = volumeFocus,
                    nextFocusRequester = copyFocus,
                    autoAdvanceAfterPause = true,
                    modifier = Modifier.weight(1f),
                )
                DeweyTextField(
                    value = draft.copyNumber,
                    onValueChange = { onChange(FormField.CopyNumber, it.take(3)) },
                    label = stringResource(R.string.copy_label),
                    keyboardType = KeyboardType.Number,
                    error = errors[FormField.CopyNumber],
                    focusRequester = copyFocus,
                    nextFocusRequester = yearFocus,
                    autoAdvanceAfterPause = true,
                    modifier = Modifier.weight(1f),
                )
            }
            DeweyTextField(
                value = draft.publicationYear,
                onValueChange = { onChange(FormField.PublicationYear, it.take(4)) },
                label = stringResource(R.string.year_label),
                keyboardType = KeyboardType.Number,
                error = errors[FormField.PublicationYear],
                focusRequester = yearFocus,
                isLast = true,
                onDone = onDone,
            )
        }
    }
}

@Composable
private fun FormSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(DeweySpacing.md),
            verticalArrangement = Arrangement.spacedBy(DeweySpacing.sm),
        ) {
            SectionHeader(title = title)
            content()
        }
    }
}

@Composable
private fun DeweyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    helper: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    error: ValidationError? = null,
    focusRequester: FocusRequester? = null,
    nextFocusRequester: FocusRequester? = null,
    autoAdvanceLength: Int? = null,
    autoAdvanceAfterPause: Boolean = false,
    isLast: Boolean = false,
    onDone: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val supporting = error?.let { validationText(it) } ?: helper
    var isFocused by remember { mutableStateOf(false) }
    var editRevision by remember { mutableIntStateOf(0) }
    val currentlyFocused by rememberUpdatedState(isFocused)
    val moveNext by rememberUpdatedState<() -> Unit> {
        nextFocusRequester?.requestFocus() ?: focusManager.moveFocus(FocusDirection.Next)
    }

    LaunchedEffect(editRevision, value) {
        if (editRevision == 0 || value.isBlank() || isLast || !currentlyFocused) return@LaunchedEffect
        val enteredLength = value.count { !it.isWhitespace() }
        if (autoAdvanceLength != null && enteredLength >= autoAdvanceLength) {
            moveNext()
        } else if (autoAdvanceAfterPause) {
            delay(AUTO_ADVANCE_DELAY_MS)
            if (currentlyFocused) moveNext()
        }
    }

    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            if (newValue != value) {
                onValueChange(newValue)
                editRevision++
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused },
        label = { Text(label) },
        placeholder = if (placeholder.isEmpty()) null else ({ Text(placeholder) }),
        supportingText = supporting?.let { text -> ({ Text(text) }) },
        isError = error != null,
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = if (isLast) ImeAction.Done else ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(
            onNext = { moveNext() },
            onDone = { focusManager.clearFocus(); onDone() },
        ),
    )
}

private const val AUTO_ADVANCE_DELAY_MS = 700L

@Composable
private fun validationText(error: ValidationError): String = when (error) {
    ValidationError.Required -> stringResource(R.string.required_field)
    ValidationError.InvalidMainClass -> stringResource(R.string.invalid_main_class)
    ValidationError.InvalidNumber -> stringResource(R.string.invalid_number)
}

private fun BookDraft.updated(field: FormField, value: String): BookDraft = when (field) {
    FormField.Title -> copy(title = value)
    FormField.MainClass -> copy(mainClass = value)
    FormField.DecimalPart -> copy(decimalPart = value)
    FormField.AuthorLetter -> copy(authorLetter = value)
    FormField.AuthorNumber -> copy(authorNumber = value)
    FormField.WorkMark -> copy(workMark = value)
    FormField.Volume -> copy(volume = value)
    FormField.CopyNumber -> copy(copyNumber = value)
    FormField.PublicationYear -> copy(publicationYear = value)
}
