package com.aptprice.tracker.presentation.detail

import java.time.LocalDate

/** 차트에 찍히는 실거래 한 건. */
data class ChartPoint(
    val date: LocalDate,
    /** 금액 (만원). 매매는 거래금액, 전세는 보증금. */
    val amountManwon: Long,
    /** 0..1 로 정규화된 가로 위치 */
    val x: Float,
    /** 0..1 로 정규화된 세로 위치 (1 이 위쪽) */
    val y: Float,
    val floor: Int?,
)

/**
 * 선이 이어지는 한 덩어리.
 *
 * 신고가 공백이 길면 두 점을 직선으로 잇지 않고 구간을 끊는다.
 * 없는 기간의 시세를 직선으로 추정해 보여주지 않기 위한 것이다.
 */
data class ChartSegment(val points: List<ChartPoint>)

/** 매매 / 전세 각각의 계열. */
data class ChartSeries(
    val type: SeriesType,
    val segments: List<ChartSegment>,
) {
    val points: List<ChartPoint> get() = segments.flatMap { it.points }
    val isEmpty: Boolean get() = points.isEmpty()
}

enum class SeriesType(val label: String) {
    SALE("매매"),
    JEONSE("전세"),
}

/** 세로축 눈금 하나. */
data class AxisTick(
    val amountManwon: Long,
    /** 0..1 정규화 위치 */
    val position: Float,
    val label: String,
)

/** 가로축 눈금 하나. */
data class DateTick(
    val date: LocalDate,
    val position: Float,
    val label: String,
)

/**
 * 기간 안에서 가장 높은 매매가.
 *
 * 해제된 계약은 성사되지 않은 가격이므로 신고가로 보지 않는다.
 * 매매 거래가 없으면 `null` 이고, 이때 가이드라인을 그리지 않는다.
 */
data class PeakMarker(
    val amountManwon: Long,
    val date: LocalDate,
    val position: Float,
    val label: String,
)

/** 차트 한 장을 그리는 데 필요한 모든 것. */
data class PriceChartData(
    val series: List<ChartSeries>,
    val yTicks: List<AxisTick>,
    val xTicks: List<DateTick>,
    val peak: PeakMarker?,
    val minAmountManwon: Long,
    val maxAmountManwon: Long,
    val startDate: LocalDate,
    val endDate: LocalDate,
) {
    val isEmpty: Boolean get() = series.all { it.isEmpty }
    val pointCount: Int get() = series.sumOf { it.points.size }
}
