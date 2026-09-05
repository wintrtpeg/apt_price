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
    primaryContainer = AppColors.RoyalBlueTint,
    onPrimaryContainer = AppColors.DeepRoyalBlue,
    secondary = AppColors.CobaltBlue,
    onSecondary = Color.White,
    secondaryContainer = AppColors.RoyalBlueTintStrong,
    onSecondaryContainer = AppColors.DeepRoyalBlue,
    background = AppColors.Canvas,
    onBackground = AppColors.Ink900,
    surface = AppColors.Surface,
    onSurface = AppColors.Ink900,
    surfaceVariant = AppColors.Ink100,
    onSurfaceVariant = AppColors.Ink500,
    // 카드 위에 칩·입력창을 얹을 때 쓰는 단계. 흰색 위 흰색을 피한다.
    surfaceContainerLowest = AppColors.Surface,
    surfaceContainerLow = AppColors.SurfaceMuted,
    surfaceContainer = AppColors.Ink100,
    surfaceContainerHigh = AppColors.Ink200,
    surfaceContainerHighest = AppColors.Ink200,
    outline = AppColors.Ink300,
    outlineVariant = AppColors.Hairline,
    error = AppColors.CarmineRed,
    errorContainer = AppColors.CarmineTint,
    onErrorContainer = AppColors.CarmineRed,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FAAFF),
    onPrimary = Color(0xFF0A1637),
    primaryContainer = Color(0xFF20263A),
    onPrimaryContainer = Color(0xFFC7D4FF),
    secondary = Color(0xFF8AB4FF),
    onSecondary = Color(0xFF0A1637),
    secondaryContainer = Color(0xFF1E2A44),
    onSecondaryContainer = Color(0xFFC7D4FF),
    background = AppColors.DarkCanvas,
    onBackground = Color(0xFFECECEE),
    surface = AppColors.DarkSurface,
    onSurface = Color(0xFFECECEE),
    surfaceVariant = AppColors.DarkSurfaceElevated,
    onSurfaceVariant = Color(0xFF9B9BA1),
    surfaceContainerLowest = AppColors.DarkCanvas,
    surfaceContainerLow = AppColors.DarkSurface,
    surfaceContainer = AppColors.DarkSurfaceElevated,
    surfaceContainerHigh = Color(0xFF242427),
    surfaceContainerHighest = Color(0xFF2C2C30),
    outline = Color(0xFF3C3C42),
    outlineVariant = AppColors.DarkHairline,
    error = Color(0xFFFF7A7A),
    errorContainer = Color(0xFF3A1E22),
    onErrorContainer = Color(0xFFFFB4B4),
)

/**
 * 매매 / 전세 계열색.
 *
 * Material 스킴의 primary·secondary 를 쓰면 안 된다 — 그 둘은 같은 계열이라
 * 차트에서 두 선이 구분되지 않았다. 여기서 색상환상 떨어진 두 색을 따로 정한다.
 */
data class SeriesColors(
    /** 매매 */
    val sale: Color,
    /** 전세 */
    val jeonse: Color,
    /** 배지·바탕에 쓰는 옅은 톤 */
    val saleContainer: Color,
    val jeonseContainer: Color,
) {
    fun of(isSale: Boolean): Color = if (isSale) sale else jeonse

    fun containerOf(isSale: Boolean): Color = if (isSale) saleContainer else jeonseContainer
}

private val LightSeriesColors = SeriesColors(
    sale = AppColors.SaleBlue,
    jeonse = AppColors.JeonseTeal,
    saleContainer = AppColors.SaleBlueTint,
    jeonseContainer = AppColors.JeonseTealTint,
)

private val DarkSeriesColors = SeriesColors(
    sale = AppColors.SaleBlueDark,
    jeonse = AppColors.JeonseTealDark,
    saleContainer = AppColors.SaleBlueTintDark,
    jeonseContainer = AppColors.JeonseTealTintDark,
)

val LocalSeriesColors = staticCompositionLocalOf { LightSeriesColors }

/** 등락 표시색처럼 Material 스킴에 없는 의미색 묶음. */
data class TrendColors(
    val up: Color,
    val upContainer: Color,
    val down: Color,
    val downContainer: Color,
    val flat: Color,
    val flatContainer: Color,
    val noData: Color,
)

private val LightTrendColors = TrendColors(
    up = AppColors.CarmineRed,
    upContainer = AppColors.CarmineTint,
    down = AppColors.CobaltBlue,
    downContainer = AppColors.CobaltTint,
    flat = AppColors.Ink500,
    flatContainer = AppColors.Ink100,
    noData = AppColors.NoData,
)

private val DarkTrendColors = TrendColors(
    up = Color(0xFFFF8A8A),
    upContainer = Color(0xFF3A2124),
    down = Color(0xFF8AB4FF),
    downContainer = Color(0xFF1D2333),
    flat = Color(0xFF9B9BA1),
    flatContainer = Color(0xFF242427),
    noData = Color(0xFF767D8B),
)

val LocalTrendColors = staticCompositionLocalOf { LightTrendColors }

@Composable
fun AptPriceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Dynamic Color(Material You)는 쓰지 않는다.
    // 상승/하락 색이 기기 배경색에 따라 바뀌면 금액 정보를 잘못 읽을 수 있다.
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val trendColors = if (darkTheme) DarkTrendColors else LightTrendColors
    val seriesColors = if (darkTheme) DarkSeriesColors else LightSeriesColors

    CompositionLocalProvider(
        LocalTrendColors provides trendColors,
        LocalSeriesColors provides seriesColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
