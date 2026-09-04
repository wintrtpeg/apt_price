package com.aptprice.tracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.aptprice.tracker.R

/**
 * Pretendard 를 앱에 번들링해 한글 표시를 고정한다.
 *
 * 기기 기본 한글 폰트에 의존하면 제조사·OS 버전에 따라 자소 렌더링과 자간이 달라지고,
 * 일부 기기에서는 글리프가 빠져 네모(□)로 보이는 문제가 생긴다.
 * `res/font` 에 직접 넣은 Pretendard 만 사용해 그 편차를 없앤다.
 *
 * 폰트 라이선스: SIL Open Font License 1.1 (재배포·임베딩 허용)
 */
val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)

private val lineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun pretendard(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    letterSpacing: Double = -0.02,
) = TextStyle(
    fontFamily = Pretendard,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    // 한글은 자간을 약간 좁혀야 미니멀 레이아웃에서 균형이 맞는다.
    letterSpacing = (size * letterSpacing).sp,
    lineHeightStyle = lineHeightStyle,
)

val AppTypography = Typography(
    displaySmall = pretendard(32, 40, FontWeight.Bold),
    headlineMedium = pretendard(26, 34, FontWeight.Bold),
    headlineSmall = pretendard(22, 30, FontWeight.SemiBold),
    titleLarge = pretendard(19, 26, FontWeight.SemiBold),
    titleMedium = pretendard(17, 24, FontWeight.SemiBold),
    titleSmall = pretendard(15, 22, FontWeight.Medium),
    bodyLarge = pretendard(16, 24, FontWeight.Normal),
    bodyMedium = pretendard(14, 21, FontWeight.Normal),
    bodySmall = pretendard(13, 19, FontWeight.Normal),
    labelLarge = pretendard(14, 20, FontWeight.Medium),
    labelMedium = pretendard(12, 17, FontWeight.Medium),
    labelSmall = pretendard(11, 15, FontWeight.Medium),
)

/**
 * 금액처럼 자리수가 흔들리면 안 되는 숫자에 쓰는 스타일.
 *
 * 카드에서 가장 먼저 읽혀야 하는 값이므로 본문보다 확실히 크게 잡고,
 * 자간을 좁혀 숫자 덩어리가 한 단어처럼 보이게 한다.
 */
val PriceTextStyle = TextStyle(
    fontFamily = Pretendard,
    fontWeight = FontWeight.Bold,
    fontSize = 23.sp,
    lineHeight = 29.sp,
    letterSpacing = (-0.7).sp,
)

/** 상세 화면 머리말처럼 금액을 더 크게 세울 때. */
val PriceLargeTextStyle = PriceTextStyle.copy(
    fontSize = 30.sp,
    lineHeight = 38.sp,
    letterSpacing = (-1.0).sp,
)
