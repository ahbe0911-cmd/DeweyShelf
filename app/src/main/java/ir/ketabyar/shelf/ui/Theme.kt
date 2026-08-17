package ir.ketabyar.shelf.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import ir.ketabyar.shelf.R

val DeweyTeal = Color(0xFF087B96)
val DeweyTealDark = Color(0xFF04586D)
val DeweyYellow = Color(0xFFF6B81A)
val Literature = Color(0xFF8B315B)
val Paper = Color(0xFFF7F3E8)

@Composable fun KetabYarTheme(content: @Composable () -> Unit) {
    val vazir = FontFamily(Font(R.font.vazirmatn_regular))
    val base = Typography()
    val vazirTypography = base.copy(
        displayLarge=base.displayLarge.copy(fontFamily=vazir), displayMedium=base.displayMedium.copy(fontFamily=vazir), displaySmall=base.displaySmall.copy(fontFamily=vazir),
        headlineLarge=base.headlineLarge.copy(fontFamily=vazir), headlineMedium=base.headlineMedium.copy(fontFamily=vazir), headlineSmall=base.headlineSmall.copy(fontFamily=vazir),
        titleLarge=base.titleLarge.copy(fontFamily=vazir), titleMedium=base.titleMedium.copy(fontFamily=vazir), titleSmall=base.titleSmall.copy(fontFamily=vazir),
        bodyLarge=base.bodyLarge.copy(fontFamily=vazir), bodyMedium=base.bodyMedium.copy(fontFamily=vazir), bodySmall=base.bodySmall.copy(fontFamily=vazir),
        labelLarge=base.labelLarge.copy(fontFamily=vazir), labelMedium=base.labelMedium.copy(fontFamily=vazir), labelSmall=base.labelSmall.copy(fontFamily=vazir)
    )
    MaterialTheme(
        colorScheme = lightColorScheme(primary = DeweyTeal, secondary = Literature, tertiary = DeweyYellow, background = Color(0xFFF3F8F8), surface = Color.White, error = Color(0xFFBA1A1A)),
        shapes = Shapes(small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp), large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)),
        typography = vazirTypography,
        content = content
    )
}
