package com.aptprice.tracker.presentation.feed.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.aptprice.tracker.core.format.DateFormatter
import com.aptprice.tracker.domain.model.DealTab
import com.aptprice.tracker.presentation.feed.FeedBucket
import com.aptprice.tracker.presentation.feed.FeedSummary
import com.aptprice.tracker.ui.components.AppCard
import com.aptprice.tracker.ui.components.TonalBadge
import com.aptprice.tracker.ui.theme.AppSpacing
import com.aptprice.tracker.ui.theme.LocalSeriesColors
import com.aptprice.tracker.ui.theme.PriceLargeTextStyle

/**
 * 목록 맨 위의 요약.
 *
 * 카드만 죽 늘어놓으면 "지금 이 동네가 어떤 상황인지" 를 스크롤해서 눈으로 세어야 한다.
 * 건수·중간가·최고/최저를 한 번에 보여 주고, 그 아래 **거래량 막대 + 중간가 선**으로
 * 기간 안의 흐름을 보여 준다.
 *
 * 모든 값은 지금 목록에 보이는 실제 신고 건에서 나온다. 거래가 없는 구간은 막대가 없고
 * 선도 잇지 않는다 — 없는 구간의 시세를 아는 것처럼 보이지 않게 한다.
 */
@Composable
fun FeedSummaryCard(
    summary: FeedSummary,
    modifier: Modifier = Modifier,
) {
    val series = LocalSeriesColors.current
    val accent = series.of(summary.tab == DealTab.SALE)

    AppCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(AppSpacing.CardInner),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = summary.headline,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (summary.canceledCount > 0) {
                    // 해제 건은 금액 통계에서 뺐다. 뺐다는 사실을 감추지 않는다.
                    TonalBadge(
                        text = "해제 ${summary.canceledCount}건 제외",
                        container = MaterialTheme.colorScheme.errorContainer,
                        content = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = summary.medianCaption,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = summary.medianLabel ?: "—",
                        style = PriceLargeTextStyle,
                        color = accent,
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    summary.maxLabel?.let { ExtremeRow("최고", it) }
                    summary.minLabel?.let { ExtremeRow("최저", it) }
                }
            }

            if (summary.hasChart) {
                VolumeAndPriceChart(buckets = summary.buckets, accent = accent)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    AxisLabel(DateFormatter.formatFeedDate(summary.buckets.first().startDate))
                    AxisLabel(DateFormatter.formatFeedDate(summary.buckets.last().endDate))
                }
                Text(
                    text = "막대는 신고 건수, 선은 구간별 중간가입니다. 신고가 없는 구간은 비워 둡니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ExtremeRow(caption: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AxisLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * 거래량 막대 위에 중간가 선을 겹쳐 그린다.
 *
 * 선은 **이웃한 두 칸에 모두 거래가 있을 때만** 잇는다. 빈 칸을 건너뛰어 이으면
 * 그 사이의 시세를 아는 것처럼 보이는데, 그건 신고된 적 없는 값이다.
 */
@Composable
private fun VolumeAndPriceChart(
    buckets: List<FeedBucket>,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val barColor = accent.copy(alpha = 0.22f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier.fillMaxWidth().height(CHART_HEIGHT)) {
        val w = size.width
        val h = size.height
        // 선이 위아래로 잘리지 않게 여백을 둔다.
        val top = h * 0.12f
        val plotHeight = h * 0.88f - top

        drawLine(
            color = gridColor,
            start = Offset(0f, h),
            end = Offset(w, h),
            strokeWidth = 1f,
        )

        if (buckets.isEmpty()) return@Canvas
        val slot = w / buckets.size
        // 칸이 적으면(2주는 7칸) 막대가 화면 폭을 나눠 갖느라 뚱뚱해진다. 상한을 둔다.
        val barWidth = (slot * 0.55f).coerceIn(2f, MAX_BAR_WIDTH.toPx())
        val cornerPx = BAR_CORNER.toPx()

        buckets.forEachIndexed { index, bucket ->
            if (bucket.count == 0) return@forEachIndexed
            // 건수가 1건이어도 보이도록 최소 높이를 준다.
            val barHeight = (bucket.countRatio * h * 0.9f).coerceAtLeast(3f)
            // 모서리를 폭의 절반으로 두면 낮은 막대가 알약처럼 뭉개진다.
            // 높이의 절반을 넘지 않게 잡아 막대로 읽히게 한다.
            val radius = minOf(cornerPx, barWidth / 2f, barHeight / 2f)
            drawRoundRect(
                color = barColor,
                topLeft = Offset(index * slot + (slot - barWidth) / 2f, h - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(radius, radius),
            )
        }

        val centerX = { index: Int -> index * slot + slot / 2f }
        val centerY = { ratio: Float -> top + (1f - ratio) * plotHeight }

        var path: Path? = null
        buckets.forEachIndexed { index, bucket ->
            val ratio = bucket.priceRatio
            if (ratio == null) {
                path?.let { drawPath(it, accent, style = Stroke(width = 2.dp.toPx())) }
                path = null
                return@forEachIndexed
            }
            val x = centerX(index)
            val y = centerY(ratio)
            val current = path
            if (current == null) {
                path = Path().apply { moveTo(x, y) }
            } else {
                current.lineTo(x, y)
            }
            drawCircle(color = accent, radius = 2.6.dp.toPx(), center = Offset(x, y))
        }
        path?.let { drawPath(it, accent, style = Stroke(width = 2.dp.toPx())) }
    }
}

private val CHART_HEIGHT = 76.dp

/** 막대가 뚱뚱해지지 않도록 하는 폭 상한. */
private val MAX_BAR_WIDTH = 14.dp

private val BAR_CORNER = 3.dp
