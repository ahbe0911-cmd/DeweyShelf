package ir.deweyshelf.app.core

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.deweyshelf.app.R

object DeweyColors {
    val Primary = Color(0xFF0B6673)
    val PrimaryContainer = Color(0xFFD7EEF0)
    val OnPrimaryContainer = Color(0xFF0A3B43)
    val Secondary = Color(0xFF466268)
    val Accent = Color(0xFFD99B2B)
    val Background = Color(0xFFF7F9F8)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceVariant = Color(0xFFEAF0EF)
    val Border = Color(0xFFD7E0DE)
    val TextPrimary = Color(0xFF172B2E)
    val TextSecondary = Color(0xFF5E7377)
    val TextDisabled = Color(0xFF95A3A5)
    val Success = Color(0xFF177A59)
    val Warning = Color(0xFF9A650D)
    val Error = Color(0xFFB3261E)
    val Information = Color(0xFF315F9A)
    val Overlay = Color(0x1F0A2529)
    val Scrim = Color(0x660A2529)
}

object DeweySpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 40.dp
    val touch = 48.dp
}

object DeweyRadius {
    val small = 8.dp
    val medium = 12.dp
    val large = 18.dp
    val full = 999.dp
}

object DeweyMotion {
    const val Fast = 120
    const val Normal = 180
    const val Emphasized = 240
}

private val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
)

private val DeweyTypography = Typography(
    displaySmall = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 42.sp),
    headlineSmall = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 30.sp),
    titleMedium = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 26.sp),
    titleSmall = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 28.sp),
    bodyMedium = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 24.sp),
    bodySmall = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 22.sp),
    labelMedium = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
)

private val DeweyShapes = Shapes(
    extraSmall = RoundedCornerShape(DeweyRadius.small),
    small = RoundedCornerShape(DeweyRadius.small),
    medium = RoundedCornerShape(DeweyRadius.medium),
    large = RoundedCornerShape(DeweyRadius.large),
    extraLarge = RoundedCornerShape(24.dp),
)

private val LightColors = lightColorScheme(
    primary = DeweyColors.Primary,
    onPrimary = Color.White,
    primaryContainer = DeweyColors.PrimaryContainer,
    onPrimaryContainer = DeweyColors.OnPrimaryContainer,
    secondary = DeweyColors.Secondary,
    onSecondary = Color.White,
    secondaryContainer = DeweyColors.SurfaceVariant,
    onSecondaryContainer = DeweyColors.TextPrimary,
    tertiary = DeweyColors.Accent,
    onTertiary = Color(0xFF3E2A00),
    background = DeweyColors.Background,
    onBackground = DeweyColors.TextPrimary,
    surface = DeweyColors.Surface,
    onSurface = DeweyColors.TextPrimary,
    surfaceVariant = DeweyColors.SurfaceVariant,
    onSurfaceVariant = DeweyColors.TextSecondary,
    outline = DeweyColors.Border,
    error = DeweyColors.Error,
    scrim = DeweyColors.Scrim,
)

@Composable
fun DeweyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = DeweyTypography,
        shapes = DeweyShapes,
        content = content,
    )
}

