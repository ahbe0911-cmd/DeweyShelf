package ir.ketabyar.shelf.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Teal = Color(0xFF075E59)
private val Literature = Color(0xFF6D294D)

@Composable fun KetabYarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Teal, secondary = Literature, background = Color(0xFFF7FAF9), surface = Color.White, error = Color(0xFFBA1A1A)),
        shapes = Shapes(small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp), large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)),
        content = content
    )
}

