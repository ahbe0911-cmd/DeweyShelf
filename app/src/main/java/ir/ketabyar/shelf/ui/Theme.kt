package ir.ketabyar.shelf.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val DeweyTeal = Color(0xFF087B96)
val DeweyTealDark = Color(0xFF04586D)
val DeweyYellow = Color(0xFFF6B81A)
val Literature = Color(0xFF8B315B)
val Paper = Color(0xFFF7F3E8)

@Composable fun KetabYarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = DeweyTeal, secondary = Literature, tertiary = DeweyYellow, background = Color(0xFFF3F8F8), surface = Color.White, error = Color(0xFFBA1A1A)),
        shapes = Shapes(small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp), large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)),
        content = content
    )
}
