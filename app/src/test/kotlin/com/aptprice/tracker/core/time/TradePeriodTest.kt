package com.aptprice.tracker.core.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TradePeriodTest {

    private val today = LocalDate.of(2026, 9, 4)

    @Test
    fun `기본 기간은 최근 2주다`() {
        assertEquals(TradePeriod.TWO_WEEKS, TradePeriod.DEFAULT)
        assertEquals(LocalDate.of(2026, 8, 22), TradePeriod.DEFAULT.startDate(today))
    }

    @Test
    fun `최대 기간은 5년이다`() {
        assertEquals(TradePeriod.FIVE_YEARS, TradePeriod.MAX)
        assertEquals(LocalDate.of(2021, 9, 5), TradePeriod.FIVE_YEARS.startDate(today))
        // 5년을 넘는 프리셋은 없다.
        assertTrue(TradePeriod.entries.none { it.isWiderThan(TradePeriod.FIVE_YEARS) })
    }

    @Test
    fun `모든 프리셋의 시작일이 맞다`() {
        assertEquals(LocalDate.of(2026, 8, 22), TradePeriod.TWO_WEEKS.startDate(today))
        assertEquals(LocalDate.of(2026, 8, 5), TradePeriod.ONE_MONTH.startDate(today))
        assertEquals(LocalDate.of(2026, 6, 5), TradePeriod.THREE_MONTHS.startDate(today))
        assertEquals(LocalDate.of(2026, 3, 5), TradePeriod.SIX_MONTHS.startDate(today))
        assertEquals(LocalDate.of(2025, 9, 5), TradePeriod.ONE_YEAR.startDate(today))
        assertEquals(LocalDate.of(2023, 9, 5), TradePeriod.THREE_YEARS.startDate(today))
        assertEquals(LocalDate.of(2021, 9, 5), TradePeriod.FIVE_YEARS.startDate(today))
    }

    @Test
    fun `구간은 항상 오늘로 끝나고 양끝을 포함한다`() {
        TradePeriod.entries.forEach { period ->
            val range = period.range(today)
            assertEquals("${period.label} 종료일", today, range.endInclusive)
            assertTrue("${period.label} 시작일 포함", range.start in range)
            assertTrue("${period.label} 종료일 포함", today in range)
            assertFalse("${period.label} 시작 직전일 제외", range.start.minusDays(1) in range)
        }
    }

    @Test
    fun `기간이 넓어질수록 시작일이 앞당겨진다`() {
        val starts = TradePeriod.entries.map { it.startDate(today) }
        assertEquals(starts.sortedDescending(), starts)
    }

    @Test
    fun `프리셋 순서대로 폭이 커진다`() {
        assertTrue(TradePeriod.FIVE_YEARS.isWiderThan(TradePeriod.ONE_YEAR))
        assertTrue(TradePeriod.ONE_MONTH.isWiderThan(TradePeriod.TWO_WEEKS))
        assertFalse(TradePeriod.TWO_WEEKS.isWiderThan(TradePeriod.TWO_WEEKS))
        assertFalse(TradePeriod.SIX_MONTHS.isWiderThan(TradePeriod.THREE_YEARS))
    }

    @Test
    fun `윤년 2월 29일 기준으로도 시작일이 유효하다`() {
        val leapDay = LocalDate.of(2028, 2, 29)
        // 2027-02-29 는 없으므로 java_time 이 2027-02-28 로 맞춘 뒤 +1일 → 2027-03-01
        assertEquals(LocalDate.of(2027, 3, 1), TradePeriod.ONE_YEAR.startDate(leapDay))
        assertEquals(LocalDate.of(2023, 3, 1), TradePeriod.FIVE_YEARS.startDate(leapDay))
        TradePeriod.entries.forEach { period ->
            assertTrue(period.startDate(leapDay) <= leapDay)
        }
    }

    @Test
    fun `피드는 2주부터 5년까지 모두 고를 수 있다`() {
        assertEquals(7, TradePeriod.feedOptions.size)
        assertTrue(TradePeriod.TWO_WEEKS in TradePeriod.feedOptions)
        assertTrue(TradePeriod.FIVE_YEARS in TradePeriod.feedOptions)
    }

    @Test
    fun `차트는 점이 너무 적은 짧은 구간을 뺀다`() {
        assertFalse(TradePeriod.TWO_WEEKS in TradePeriod.chartOptions)
        assertFalse(TradePeriod.ONE_MONTH in TradePeriod.chartOptions)
        assertTrue(TradePeriod.FIVE_YEARS in TradePeriod.chartOptions)
        assertEquals(TradePeriod.ONE_YEAR, TradePeriod.CHART_DEFAULT)
        assertTrue(TradePeriod.CHART_DEFAULT in TradePeriod.chartOptions)
    }
}
