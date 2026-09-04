package com.aptprice.tracker.presentation.detail.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aptprice.tracker.core.format.DateFormatter
import com.aptprice.tracker.core.format.MoneyFormatter
import com.aptprice.tracker.presentation.detail.ChartPoint
import com.aptprice.tracker.presentation.detail.PriceChartData
import com.aptprice.tracker.presentation.detail.SeriesType

/**
 * 실거래가 시계열 라인 차트.
 *
 * - 점은 실제로 신고된 거래다.
 * - 선은 가까운 두 거래 사이에만 그린다. 신고가 오래 없던 구간은 끊어서, 그 사이 시세를
 *   아는 것처럼 보이지 않게 한다. (구간을 나누는 일은 ChartBuilder 가 한다)
 * - 점을 누르면 그 거래의 값을 그대로 보여준다.
 */
@Composable
fun PriceChart(
    data: PriceChartData,
    modifier: Modifier = Modifier,
    height: Dp = 240.dp,
) {
    var selected by remember(data) { mutableStateOf<ChartPoint?>(null) }
    var selectedType by remember(data) { mutableStateOf<SeriesType?>(null) }

    val saleColor = MaterialTheme.colorScheme.primary
    val jeonseColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val peakColor = MaterialTheme.colorScheme.error

    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(height)) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .pointerInput(data) {
                        detectTapGestures { tap ->
                            val hit = data.nearestPoint(tap, size.width.toFloat(), size.height.toFloat())
                            selected = hit?.second
                            selectedType = hit?.first
                        }
                    },
            ) {
                val w = size.width
                val h = size.height

                // 가로 격자 + 세로축 눈금
                data.yTicks.forEach { tick ->
                    val y = h - tick.position * h
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f,
                    )
                }

                // 기간 내 최고가 가이드라인 (매매 거래가 있을 때만)
                data.peak?.let { peak ->
                    val y = h - peak.position * h
                    drawLine(
                        color = peakColor.copy(alpha = 0.7f),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
                    )
                }

                data.series.forEach { series ->
                    val color = if (series.type == SeriesType.SALE) saleColor else jeonseColor
                    series.segments.forEach { segment ->
                        drawSegment(segment.points, color, w, h, 2.dp.toPx())
                    }
                    segmentDots(series.points, color, w, h, 3.dp.toPx())
                }

                selected?.let { point ->
                    val color = if (selectedType == SeriesType.SALE) saleColor else jeonseColor
                    val cx = point.x * w
                    val cy = h - point.y * h
                    drawLine(
                        color = color.copy(alpha = 0.4f),
                        start = Offset(cx, 0f),
                        end = Offset(cx, h),
                        strokeWidth = 1f,
                    )
                    drawCircle(color = color, radius = 3.dp.toPx() * 1.8f, center = Offset(cx, cy))
                }
            }
        }

        ChartAxisLabels(data)

        selected?.let { point ->
            ChartTooltip(
                point = point,
                type = selectedType,
                isPeak = data.peak?.amountManwon == point.amountManwon &&
                    selectedType == SeriesType.SALE,
            )
        }
    }
}

private fun DrawScope.drawSegment(
    points: List<ChartPoint>,
    color: Color,
    w: Float,
    h: Float,
    strokeWidth: Float,
) {
    if (points.size < 2) return
    val path = Path()
    points.forEachIndexed { index, point ->
        val x = point.x * w
        val y = h - point.y * h
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path = path, color = color, style = Stroke(width = strokeWidth))
}

private fun DrawScope.segmentDots(
    points: List<ChartPoint>,
    color: Color,
    w: Float,
    h: Float,
    radius: Float,
) {
    points.forEach { point ->
        drawCircle(color = color, radius = radius, center = Offset(point.x * w, h - point.y * h))
    }
}

/** 탭 지점에서 가장 가까운 거래를 찾는다. 너무 멀면 선택하지 않는다. */
private fun PriceChartData.nearestPoint(
    tap: Offset,
    width: Float,
    height: Float,
): Pair<SeriesType, ChartPoint>? {
    var best: Pair<SeriesType, ChartPoint>? = null
    var bestDistance = Float.MAX_VALUE
    series.forEach { s ->
        s.points.forEach { point ->
            val dx = point.x * width - tap.x
            val dy = (height - point.y * height) - tap.y
            val distance = dx * dx + dy * dy
            if (distance < bestDistance) {
                bestDistance = distance
                best = s.type to point
            }
        }
    }
    val threshold = width * 0.12f
    return if (bestDistance <= threshold * threshold) best else null
}

/** 가로축 눈금은 균등 간격이라 Row 로 펼치면 된다. */
@Composable
private fun ChartAxisLabels(data: PriceChartData) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        data.xTicks.forEach { tick ->
            Text(
                text = tick.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 계열 색 안내. */
@Composable
fun ChartLegend(data: PriceChartData, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        data.series.forEach { series ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = if (series.type == SeriesType.SALE) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.secondary
                            },
                            shape = CircleShape,
                        ),
                )
                Text(
                    text = "${series.type.label} ${series.points.size}건",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        data.peak?.let {
            Text(
                text = it.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ChartTooltip(point: ChartPoint, type: SeriesType?, isPeak: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = listOfNotNull(
                    DateFormatter.formatIso(point.date),
                    type?.label,
                    point.floor?.let { "${it}층" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = MoneyFormatter.formatManwon(point.amountManwon) +
                    if (isPeak) "  (기간 내 최고가)" else "",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
