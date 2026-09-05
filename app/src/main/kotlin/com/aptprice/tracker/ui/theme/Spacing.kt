package com.aptprice.tracker.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 화면 간격을 한곳에서 정한다.
 *
 * 여백이 화면마다 16/18/20dp 로 조금씩 다르면 스크롤할 때 목록이 좌우로 흔들려 보인다.
 * 좌우 여백은 [Screen] 하나만 쓴다.
 */
object AppSpacing {
    /** 화면 좌우 기본 여백 */
    val Screen = 20.dp

    /** 카드 안쪽 여백 */
    val CardInner = 18.dp

    /** 붙어 있는 요소 사이 */
    val Tight = 6.dp

    /** 문단 사이 */
    val Between = 12.dp

    /** 구획 사이 */
    val Section = 20.dp
}
