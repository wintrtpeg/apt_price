package com.aptprice.tracker.core.time

import java.time.LocalDate
import java.time.YearMonth

/**
 * 메인 피드의 "최근 2주" 계약일 구간과, 그 구간을 덮기 위해 호출해야 하는
 * 국토교통부 API 의 `DEAL_YMD`(YYYYMM) 목록을 계산한다.
 *
 * 실거래가 API 는 **계약월 단위**로만 조회되므로, 2주 구간이 달을 걸치면
 * 두 달치를 모두 받아온 뒤 계약일 기준으로 다시 걸러야 한다.
 */
object TradeDateWindow {

    /** 메인 피드 기본 조회 일수. 오늘을 포함한 14일. */
    const val DEFAULT_WINDOW_DAYS = 14

    /**
     * [today] 를 마지막 날로 포함하는 [days] 일짜리 계약일 구간을 만든다.
     * 예) today=2026-09-04, days=14 → 2026-08-22 .. 2026-09-04
     */
    fun recent(today: LocalDate, days: Int = DEFAULT_WINDOW_DAYS): ClosedRange<LocalDate> {
        require(days >= 1) { "조회 일수는 1 이상이어야 합니다: $days" }
        return today.minusDays((days - 1).toLong())..today
    }

    /**
     * 구간을 덮는 `DEAL_YMD` 목록(오름차순).
     * 예) 2026-08-22 .. 2026-09-04 → ["202608", "202609"]
     */
    fun dealYmdCodes(range: ClosedRange<LocalDate>): List<String> {
        val start = YearMonth.from(range.start)
        val end = YearMonth.from(range.endInclusive)
        val codes = mutableListOf<String>()
        var cursor = start
        while (!cursor.isAfter(end)) {
            codes += cursor.toDealYmd()
            cursor = cursor.plusMonths(1)
        }
        return codes
    }

    /** [recent] + [dealYmdCodes] 를 한 번에. */
    fun recentDealYmdCodes(today: LocalDate, days: Int = DEFAULT_WINDOW_DAYS): List<String> =
        dealYmdCodes(recent(today, days))

    /**
     * 국토교통부 응답의 연/월/일 필드를 [LocalDate] 로 조립한다.
     * 값이 비었거나 형식이 어긋나면 `null` — 날짜를 임의로 보정하지 않는다.
     */
    fun parseDealDate(year: String?, month: String?, day: String?): LocalDate? {
        val y = year?.trim()?.toIntOrNull() ?: return null
        val m = month?.trim()?.toIntOrNull() ?: return null
        val d = day?.trim()?.toIntOrNull() ?: return null
        if (m !in 1..12) return null
        return runCatching { LocalDate.of(y, m, d) }.getOrNull()
    }

    /** `YearMonth` → `"YYYYMM"` */
    fun YearMonth.toDealYmd(): String = "%04d%02d".format(year, monthValue)
}
