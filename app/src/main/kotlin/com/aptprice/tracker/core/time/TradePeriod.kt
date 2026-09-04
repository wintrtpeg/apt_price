package com.aptprice.tracker.core.time

import java.time.LocalDate

/**
 * 거래내역·차트의 조회 기간 프리셋.
 *
 * 기본값은 [TWO_WEEKS] 이고, 사용자가 원하면 [FIVE_YEARS] 까지 넓힐 수 있다.
 * 모든 구간은 **오늘을 마지막 날로 포함**하며 양끝을 모두 포함한다.
 * (예: 2주 = 오늘 포함 14일, 1개월 = 오늘 포함 직전 한 달)
 */
enum class TradePeriod(
    /** 목록 헤더 등에 쓰는 전체 라벨 */
    val label: String,
    /** 세그먼트 버튼·칩에 쓰는 짧은 라벨 */
    val shortLabel: String,
) {
    TWO_WEEKS("최근 2주", "2주"),
    ONE_MONTH("최근 1개월", "1개월"),
    THREE_MONTHS("최근 3개월", "3개월"),
    SIX_MONTHS("최근 6개월", "6개월"),
    ONE_YEAR("최근 1년", "1년"),
    THREE_YEARS("최근 3년", "3년"),
    FIVE_YEARS("최근 5년", "5년"),
    ;

    /** 구간의 첫 날(포함). */
    fun startDate(today: LocalDate): LocalDate = when (this) {
        TWO_WEEKS -> today.minusDays(13)
        ONE_MONTH -> today.minusMonths(1).plusDays(1)
        THREE_MONTHS -> today.minusMonths(3).plusDays(1)
        SIX_MONTHS -> today.minusMonths(6).plusDays(1)
        ONE_YEAR -> today.minusYears(1).plusDays(1)
        THREE_YEARS -> today.minusYears(3).plusDays(1)
        FIVE_YEARS -> today.minusYears(5).plusDays(1)
    }

    /** 계약일 필터에 쓰는 구간(양끝 포함). */
    fun range(today: LocalDate): ClosedRange<LocalDate> = startDate(today)..today

    /** 이 기간이 [other] 보다 넓은가. */
    fun isWiderThan(other: TradePeriod): Boolean = ordinal > other.ordinal

    companion object {
        /** 앱을 켰을 때의 기본 기간. 작업지시서의 "최근 2주" 요건. */
        val DEFAULT: TradePeriod = TWO_WEEKS

        /** 사용자가 넓힐 수 있는 최대 기간. */
        val MAX: TradePeriod = FIVE_YEARS

        /** 메인 피드의 기간 선택지 — 2주부터 5년까지 전부. */
        val feedOptions: List<TradePeriod> = entries

        /**
         * 단지 상세 차트의 기간 선택지.
         * 시계열 추이를 보는 화면이라 2주·1개월처럼 점이 몇 개 안 되는 구간은 뺀다.
         */
        val chartOptions: List<TradePeriod> =
            listOf(THREE_MONTHS, SIX_MONTHS, ONE_YEAR, THREE_YEARS, FIVE_YEARS)

        /** 차트 기본 기간. */
        val CHART_DEFAULT: TradePeriod = ONE_YEAR
    }
}
