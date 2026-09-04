package com.aptprice.tracker.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 모던 미니멀리즘 팔레트.
 * 강조색은 작업지시서 5항의 지정 색상을 그대로 사용한다.
 */
object AppColors {
    /** Deep Royal Blue — Primary */
    val DeepRoyalBlue = Color(0xFF1E40AF)

    /** Carmine Red — 신고가 / 상승 */
    val CarmineRed = Color(0xFFDC2626)

    /** Cobalt Blue — 하락 */
    val CobaltBlue = Color(0xFF2563EB)

    // 중립 계열 (미니멀 레이아웃의 여백·구분선·본문)
    val Ink900 = Color(0xFF111827)
    val Ink700 = Color(0xFF374151)
    val Ink500 = Color(0xFF6B7280)
    val Ink300 = Color(0xFFD1D5DB)
    val Ink100 = Color(0xFFF3F4F6)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceMuted = Color(0xFFFAFAFA)

    val DarkSurface = Color(0xFF111318)
    val DarkSurfaceElevated = Color(0xFF1B1E25)
    val DarkOutline = Color(0xFF2C313A)

    /** 데이터 없음 / 미신고 상태 표시색 (강조하지 않는 회색) */
    val NoData = Ink500
}
