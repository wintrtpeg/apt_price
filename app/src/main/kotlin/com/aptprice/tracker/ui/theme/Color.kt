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

    /**
     * 다크 모드의 중립색.
     *
     * 예전 값은 파랑이 섞여 있었다(#0B0E13 처럼 B 가 R 보다 8 이상 높았다).
     * 배경·카드·칩이 모두 푸른기를 띠면 화면 전체가 파랗게 물들고,
     * 정작 강조색인 파랑이 묻힌다. 색은 강조에만 쓰고 바탕은 중립으로 둔다.
     */
    val DarkCanvas = Color(0xFF0D0D0F)
    val DarkSurface = Color(0xFF151517)
    val DarkSurfaceElevated = Color(0xFF1C1C1F)
    val DarkOutline = Color(0xFF2E2E33)
    val DarkHairline = Color(0xFF262629)

    /**
     * 차트·이력의 계열색. **매매와 전세는 색상 자체가 달라야 한다.**
     *
     * 이전에는 매매에 primary, 전세에 secondary 를 썼는데 둘 다 파랑이라
     * (#8FAAFF vs #8AB4FF) 실기기에서 구분이 되지 않았다.
     * 파랑(매매) ↔ 청록(전세) 으로 색상환에서 떨어뜨리고,
     * 선 모양(실선/파선)까지 다르게 해서 색을 못 읽어도 구분되게 한다.
     */
    val SaleBlue = Color(0xFF2F5BD9)
    val JeonseTeal = Color(0xFF0E9384)
    val SaleBlueTint = Color(0xFFE8EEFF)
    val JeonseTealTint = Color(0xFFDFF5F1)

    val SaleBlueDark = Color(0xFF7FA5FF)
    val JeonseTealDark = Color(0xFF3FD8B4)
    val SaleBlueTintDark = Color(0xFF1B2440)
    val JeonseTealTintDark = Color(0xFF11332E)

    /** 데이터 없음 / 미신고 상태 표시색 (강조하지 않는 회색) */
    val NoData = Ink500
}
