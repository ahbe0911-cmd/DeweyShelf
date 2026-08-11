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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.deweyshelf.app.R
import ir.deweyshelf.app.core.DeweyColors
import ir.deweyshelf.app.core.DeweyRadius
import ir.deweyshelf.app.core.DeweySpacing

@Composable
fun DeweyLogo(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(44.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Outlined.AccountBalance,
                contentDescription = stringResource(R.string.content_description_app_logo),
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DeweySpacing.sm),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Spacer(Modifier.height(DeweySpacing.xxs))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        action?.invoke()
    }
}

@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (accent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = DeweySpacing.sm, vertical = DeweySpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(DeweySpacing.xxs))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        textStyle = MaterialTheme.typography.bodyMedium,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Outlined.Clear, contentDescription = stringResource(R.string.clear_search))
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DeweySpacing.xl, vertical = DeweySpacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(30.dp))
            }
        }
        Spacer(Modifier.height(DeweySpacing.md))
        Text(text = title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(DeweySpacing.xs))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(DeweySpacing.lg))
            Button(
                onClick = onAction,
                shape = MaterialTheme.shapes.medium,
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(DeweySpacing.xxxl), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            strokeWidth = 3.dp,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
fun StatusBadge(
    text: String,
    modifier: Modifier = Modifier,
    kind: BadgeKind = BadgeKind.Neutral,
) {
    val (container, content) = when (kind) {
        BadgeKind.Neutral -> ColorPair(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        BadgeKind.Success -> ColorPair(Color = DeweyColors.Success.copy(alpha = .12f), onColor = DeweyColors.Success)
        BadgeKind.Warning -> ColorPair(Color = DeweyColors.Warning.copy(alpha = .13f), onColor = DeweyColors.Warning)
        BadgeKind.Error -> ColorPair(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
    }
    Surface(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(DeweyRadius.full),
        color = container,
        contentColor = content,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = DeweySpacing.sm, vertical = DeweySpacing.xxs),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

enum class BadgeKind { Neutral, Success, Warning, Error }

private data class ColorPair(val Color: androidx.compose.ui.graphics.Color, val onColor: androidx.compose.ui.graphics.Color)
