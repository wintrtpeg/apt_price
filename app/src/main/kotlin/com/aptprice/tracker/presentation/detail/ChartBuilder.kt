package com.aptprice.tracker.presentation.detail

import com.aptprice.tracker.core.format.MoneyFormatter
import com.aptprice.tracker.core.time.TradePeriod
import com.aptprice.tracker.domain.model.AptRent
import com.aptprice.tracker.domain.model.AptTrade
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.max

/**
 * 실거래 목록을 시계열 차트 데이터로 바꾼다.
 *
 * ## 없는 구간을 잇지 않는 이유
 * 신고된 거래가 1년 넘게 없는 단지에서 두 점을 직선으로 이으면, 그 사이 기간에 시세가
 * 매끄럽게 변한 것처럼 보인다. 우리는 그 기간의 시세를 모른다.
 * 그래서 간격이 [gapThresholdDays] 를 넘으면 선을 끊고 점만 남긴다.
 * 점은 실제 신고된 거래이고, 선은 가까운 두 관측 사이에서만 그린다.
 *
 * ## 해제된 계약
 * 성사되지 않은 가격이므로 차트에도, 신고가 판정에도 넣지 않는다.
 * (거래 이력 표에는 취소선과 함께 남는다)
 */
object ChartBuilder {

    /** 가로축 눈금 개수 목표. */
    private const val DATE_TICK_COUNT = 4

    /**
     * 선을 끊는 기준. 기간의 1/6 을 넘는 공백이면 끊는다.
     * 예) 1년 차트는 약 2개월, 5년 차트는 약 10개월
     */
    private const val GAP_RATIO = 6

    fun build(
        trades: List<AptTrade>,
        jeonse: List<AptRent>,
        period: TradePeriod,
        today: LocalDate,
    ): PriceChartData {
        val range = period.range(today)
        val start = range.start
        val end = range.endInclusive
        val totalDays = max(1L, ChronoUnit.DAYS.between(start, end))
        val gapThresholdDays = max(1L, totalDays / GAP_RATIO)

        // 해제된 계약은 차트에 넣지 않는다.
        val saleDeals = trades.filterNot { it.canceled }.sortedBy { it.dealDate }
        val rentDeals = jeonse.filterNot { it.canceled }.sortedBy { it.dealDate }

        val amounts = saleDeals.map { it.dealAmountManwon } + rentDeals.map { it.depositManwon }
        if (amounts.isEmpty()) {
            return PriceChartData(
                series = emptyList(),
                yTicks = emptyList(),
                xTicks = dateTicks(start, end, totalDays),
                peak = null,
                minAmountManwon = 0,
                maxAmountManwon = 0,
                startDate = start,
                endDate = end,
            )
        }

        val ticks = AxisScale.niceTicks(amounts.min(), amounts.max())
        val axisMin = ticks.first()
        val axisMax = ticks.last()

        val series = listOfNotNull(
            saleDeals
                .map { it.dealDate to Pair(it.dealAmountManwon, it.floor) }
                .toSeries(SeriesType.SALE, start, totalDays, axisMin, axisMax, gapThresholdDays),
            rentDeals
                .map { it.dealDate to Pair(it.depositManwon, it.floor) }
                .toSeries(SeriesType.JEONSE, start, totalDays, axisMin, axisMax, gapThresholdDays),
        ).filterNot { it.isEmpty }

        return PriceChartData(
            series = series,
            yTicks = ticks.map {
                AxisTick(
                    amountManwon = it,
                    position = AxisScale.normalize(it, axisMin, axisMax),
                    label = MoneyFormatter.formatCompact(it),
                )
            },
            xTicks = dateTicks(start, end, totalDays),
            peak = peakOf(saleDeals, axisMin, axisMax),
            minAmountManwon = axisMin,
            maxAmountManwon = axisMax,
            startDate = start,
            endDate = end,
        )
    }

    private fun List<Pair<LocalDate, Pair<Long, Int?>>>.toSeries(
        type: SeriesType,
        start: LocalDate,
        totalDays: Long,
        axisMin: Long,
        axisMax: Long,
        gapThresholdDays: Long,
    ): ChartSeries {
        val points = map { (date, value) ->
            ChartPoint(
                date = date,
                amountManwon = value.first,
                x = (ChronoUnit.DAYS.between(start, date).toDouble() / totalDays)
                    .coerceIn(0.0, 1.0)
                    .toFloat(),
                y = AxisScale.normalize(value.first, axisMin, axisMax),
                floor = value.second,
            )
        }
        return ChartSeries(type = type, segments = points.splitOnGaps(gapThresholdDays))
    }

    /** 간격이 임계값을 넘는 지점에서 구간을 끊는다. */
    private fun List<ChartPoint>.splitOnGaps(gapThresholdDays: Long): List<ChartSegment> {
        if (isEmpty()) return emptyList()

        val segments = mutableListOf<ChartSegment>()
        var current = mutableListOf(first())
        for (index in 1 until size) {
            val previous = this[index - 1]
            val point = this[index]
            if (ChronoUnit.DAYS.between(previous.date, point.date) > gapThresholdDays) {
                segments += ChartSegment(current)
                current = mutableListOf(point)
            } else {
                current += point
            }
        }
        segments += ChartSegment(current)
        return segments
    }

    /** 기간 내 최고 매매가. 매매 거래가 없으면 가이드라인을 그리지 않는다. */
    private fun peakOf(sales: List<AptTrade>, axisMin: Long, axisMax: Long): PeakMarker? {
        val peak = sales.maxByOrNull { it.dealAmountManwon } ?: return null
        return PeakMarker(
            amountManwon = peak.dealAmountManwon,
            date = peak.dealDate,
            position = AxisScale.normalize(peak.dealAmountManwon, axisMin, axisMax),
            label = "기간 내 최고가 ${MoneyFormatter.formatManwon(peak.dealAmountManwon)}",
        )
    }

    private fun dateTicks(start: LocalDate, end: LocalDate, totalDays: Long): List<DateTick> {
        val formatter = if (totalDays > 400) YEAR_MONTH else MONTH_DAY
        return (0 until DATE_TICK_COUNT).map { index ->
            val fraction = index.toDouble() / (DATE_TICK_COUNT - 1)
            val date = start.plusDays((totalDays * fraction).toLong())
            DateTick(
                date = date,
                position = fraction.toFloat(),
                label = date.format(formatter),
            )
        }
    }

    private val MONTH_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("M.d", Locale.KOREA)
    private val YEAR_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("yy.M", Locale.KOREA)
}
