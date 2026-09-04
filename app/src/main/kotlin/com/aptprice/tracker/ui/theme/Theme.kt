package com.aptprice.tracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = AppColors.DeepRoyalBlue,
    onPrimary = Color.White,
    secondary = AppColors.CobaltBlue,
    onSecondary = Color.White,
    background = AppColors.SurfaceMuted,
    onBackground = AppColors.Ink900,
    surface = AppColors.Surface,
    onSurface = AppColors.Ink900,
    onSurfaceVariant = AppColors.Ink500,
    outlineVariant = AppColors.Ink300,
    error = AppColors.CarmineRed,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7DA2FF),
    onPrimary = Color(0xFF0A1637),
    secondary = Color(0xFF8AB4FF),
    onSecondary = Color(0xFF0A1637),
    background = AppColors.DarkSurface,
    onBackground = Color(0xFFE8EAF0),
    surface = AppColors.DarkSurfaceElevated,
    onSurface = Color(0xFFE8EAF0),
    onSurfaceVariant = Color(0xFF9AA1AF),
    outlineVariant = AppColors.DarkOutline,
    error = Color(0xFFFF6B6B),
)

/** 등락 표시색처럼 Material 스킴에 없는 의미색 묶음. */
data class TrendColors(
    val up: Color,
    val down: Color,
    val flat: Color,
    val noData: Color,
)

val LocalTrendColors = staticCompositionLocalOf {
    TrendColors(
        up = AppColors.CarmineRed,
        down = AppColors.CobaltBlue,
        flat = AppColors.Ink500,
        noData = AppColors.NoData,
    )
}

@Composable
fun AptPriceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Dynamic Color(Material You)는 쓰지 않는다.
    // 상승/하락 색이 기기 배경색에 따라 바뀌면 금액 정보를 잘못 읽을 수 있다.
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val trendColors = if (darkTheme) {
        TrendColors(
            up = Color(0xFFFF7A7A),
            down = Color(0xFF8AB4FF),
            flat = Color(0xFF9AA1AF),
            noData = Color(0xFF767D8B),
        )
    } else {
        TrendColors(
            up = AppColors.CarmineRed,
            down = AppColors.CobaltBlue,
            flat = AppColors.Ink500,
            noData = AppColors.NoData,
        )
    }

    CompositionLocalProvider(LocalTrendColors provides trendColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content,
        )
    }
}
