package com.aptprice.tracker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 모서리 반경을 한곳에서 정한다.
 *
 * 화면마다 12dp·16dp·20dp 를 섞어 쓰면 정돈되지 않아 보인다.
 * 카드는 [AppShape.Card], 칩·버튼은 [AppShape.Pill] 로 통일한다.
 */
object AppShape {
    /** 목록 카드·시트 안의 큰 블록 */
    val Card = RoundedCornerShape(20.dp)

    /** 카드 안에 들어가는 작은 블록 */
    val Inner = RoundedCornerShape(14.dp)

    /** 칩·세그먼트·버튼처럼 완전히 둥근 것 */
    val Pill = RoundedCornerShape(percent = 50)

    /** 배지처럼 아주 작은 것 */
    val Badge = RoundedCornerShape(8.dp)

    /** 바닥에서 올라오는 시트 */
    val Sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}

val AppShapes = Shapes(
    extraSmall = AppShape.Badge,
    small = AppShape.Inner,
    medium = AppShape.Inner,
    large = AppShape.Card,
    extraLarge = AppShape.Sheet,
)
