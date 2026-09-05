package com.aptprice.tracker.presentation.feed.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.aptprice.tracker.presentation.feed.Sparkline

/** 선 굵기. 카드 안이라 얇게 두되, 색이 읽힐 만큼은 준다. */
private val StrokeWidth = 2.dp

/** 마지막(가장 최근) 거래를 찍는 점. */
private val DotRadius = 3.dp

/**
 * 카드 한 장 안에 들어가는 미니 추이선.
 *
 * 가로축은 **거래 순서**다. 시간축이 아니다 — 카드만 한 폭에 몇 년을 넣으면 옛 거래
 * 한 건과 최근 몇 건이 한쪽에 뭉쳐 아무것도 읽히지 않는다. 그 사실은
 * [Sparkline.caption] 이 화면에 적는다.
 *
 * 점 사이를 잇는 선은 두 신고 사이를 이은 것일 뿐, 그 사이의 가격이 아니다.
 * 없는 거래를 만들어 채우지 않는다.
 */
@Composable
fun SparklineChart(
    sparkline: Sparkline,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val ratios = sparkline.points
        // 점이 하나면 선이 아니다. 애초에 SparklineBuilder 가 걸러 주지만,
        // 그리는 쪽에서도 0 으로 나누지 않도록 막아 둔다.
        if (ratios.size < 2) return@Canvas

        val strokePx = StrokeWidth.toPx()
        val dotPx = DotRadius.toPx()
        // 선 굵기와 점 반지름만큼 위아래·오른쪽을 비워 둔다. 안 그러면 최고가·최저가
        // 지점이 캔버스 밖으로 잘려 나간다.
        val vInset = dotPx + strokePx / 2f
        val usableHeight = (size.height - vInset * 2f).coerceAtLeast(1f)
        val right = (size.width - dotPx).coerceAtLeast(1f)
        val stepX = right / (ratios.size - 1)

        fun yOf(ratio: Float) = vInset + (1f - ratio) * usableHeight

        val line = Path()
        val area = Path()
        ratios.forEachIndexed { index, ratio ->
            val x = index * stepX
            val y = yOf(ratio)
            if (index == 0) {
                line.moveTo(x, y)
                area.moveTo(x, size.height)
                area.lineTo(x, y)
            } else {
                line.lineTo(x, y)
                area.lineTo(x, y)
            }
        }
        area.lineTo(right, size.height)
        area.close()

        drawPath(
            path = area,
            brush = Brush.verticalGradient(
                listOf(color.copy(alpha = 0.22f), Color.Transparent),
            ),
        )
        drawPath(
            path = line,
            color = color,
            style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        // 가장 최근 거래를 찍어 준다. 어느 쪽이 지금인지 한눈에 보이게.
        drawCircle(color = color, radius = dotPx, center = Offset(right, yOf(ratios.last())))
    }
}
