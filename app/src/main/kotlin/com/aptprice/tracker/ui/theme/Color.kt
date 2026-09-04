package com.aptprice.tracker.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 모던 미니멀리즘 팔레트.
 *
 * 강조색은 작업지시서 5항의 지정 색상을 그대로 쓰되, 그 주위를 **한 톤 낮은 중립색과
 * 옅은 틴트**로 채운다. 흰 배경에 흰 카드를 올리면 요소가 서로 붙어 보이므로
 * 배경(캔버스) → 카드(표면) → 강조(틴트) 세 단계를 분명히 나눈다.
 */
object AppColors {
    /** Deep Royal Blue — Primary */
    val DeepRoyalBlue = Color(0xFF1E40AF)

    /** 버튼·선택 칩처럼 넓은 면에 쓰는 한 단계 밝은 파랑 */
    val RoyalBlueBright = Color(0xFF3355E0)

    /** 강조색을 아주 옅게 깐 면 (선택 칩 배경, 배지) */
    val RoyalBlueTint = Color(0xFFEEF2FF)
    val RoyalBlueTintStrong = Color(0xFFDDE4FF)

    /** Carmine Red — 신고가 / 상승 */
    val CarmineRed = Color(0xFFDC2626)
    val CarmineTint = Color(0xFFFEECEC)

    /** Cobalt Blue — 하락 */
    val CobaltBlue = Color(0xFF2563EB)
    val CobaltTint = Color(0xFFE8F0FE)

    // 중립 계열 (미니멀 레이아웃의 여백·구분선·본문)
    val Ink900 = Color(0xFF0F1319)
    val Ink800 = Color(0xFF1B212B)
    val Ink700 = Color(0xFF374151)
    val Ink600 = Color(0xFF4B5563)
    val Ink500 = Color(0xFF6B7280)
    val Ink400 = Color(0xFF9AA1AC)
    val Ink300 = Color(0xFFD1D5DB)
    val Ink200 = Color(0xFFE7E9EE)
    val Ink100 = Color(0xFFF1F3F6)

    val Surface = Color(0xFFFFFFFF)

    /** 카드가 올라앉는 바탕. 흰색보다 한 톤 낮춰 카드 경계를 만든다. */
    val Canvas = Color(0xFFF4F5F8)

    /** 칩·입력창처럼 카드 위에 한 겹 더 얹는 면 */
    val SurfaceMuted = Color(0xFFF7F8FA)

    /** 그림자 대신 쓰는 가는 테두리 */
    val Hairline = Color(0xFFEAECF1)

    val DarkCanvas = Color(0xFF0B0E13)
    val DarkSurface = Color(0xFF151920)
    val DarkSurfaceElevated = Color(0xFF1D222B)
    val DarkOutline = Color(0xFF2A303B)
    val DarkHairline = Color(0xFF232935)

    /** 데이터 없음 / 미신고 상태 표시색 (강조하지 않는 회색) */
    val NoData = Ink500
}
