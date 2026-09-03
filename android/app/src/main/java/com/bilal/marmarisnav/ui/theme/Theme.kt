package com.bilal.marmarisnav.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.bilal.marmarisnav.data.ChartTheme

private val DayScheme = lightColorScheme(
    primary = Color(0xFF0B5C8A),
    onPrimary = Color.White,
    secondary = Color(0xFF00796B),
    background = Color(0xFFF2F4F6),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF15202B),
    surfaceVariant = Color(0xFFE3E8ED),
    onSurfaceVariant = Color(0xFF41505C),
    error = Color(0xFFC62828),
)

private val DuskScheme = darkColorScheme(
    primary = Color(0xFF62A8CE),
    onPrimary = Color(0xFF06212F),
    secondary = Color(0xFF4DB6AC),
    background = Color(0xFF101C24),
    surface = Color(0xFF16242E),
    onSurface = Color(0xFFDCE5EB),
    surfaceVariant = Color(0xFF1E3040),
    onSurfaceVariant = Color(0xFFA7BAC6),
    error = Color(0xFFEF5350),
)

/**
 * Night keeps the UI legible while giving up as little dark adaptation as
 * possible: near-black surfaces, dim red/amber accents, no large light areas.
 */
private val NightScheme = darkColorScheme(
    primary = Color(0xFFCC5533),
    onPrimary = Color(0xFF120400),
    secondary = Color(0xFF8A6A2A),
    background = Color(0xFF050708),
    surface = Color(0xFF0B0E11),
    onSurface = Color(0xFF9A8A78),
    surfaceVariant = Color(0xFF14181C),
    onSurfaceVariant = Color(0xFF7A6E60),
    error = Color(0xFFCC3322),
    outline = Color(0xFF3A342C),
)

/** Tabular figures matter here: a jittering speed readout is hard to read at sea. */
private val NavTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
    ),
)

@Composable
fun MarmarisNavTheme(
    chartTheme: ChartTheme,
    content: @Composable () -> Unit,
) {
    val scheme = when (chartTheme) {
        ChartTheme.DAY -> if (isSystemInDarkTheme()) DuskScheme else DayScheme
        ChartTheme.DUSK -> DuskScheme
        ChartTheme.NIGHT -> NightScheme
    }
    MaterialTheme(colorScheme = scheme, typography = NavTypography, content = content)
}
