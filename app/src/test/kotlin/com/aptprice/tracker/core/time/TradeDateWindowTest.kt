package com.aptprice.tracker.core.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TradeDateWindowTest {

    @Test
    fun `기본 구간은 오늘을 포함한 14일이다`() {
        val today = LocalDate.of(2026, 9, 4)
        val window = TradeDateWindow.recent(today)

        assertEquals(LocalDate.of(2026, 8, 22), window.start)
        assertEquals(today, window.endInclusive)
        assertEquals(14, java.time.temporal.ChronoUnit.DAYS.between(window.start, window.endInclusive) + 1)
    }

    @Test
    fun `구간 경계는 양끝을 포함한다`() {
        val today = LocalDate.of(2026, 9, 4)
        val window = TradeDateWindow.recent(today)

        assertTrue(LocalDate.of(2026, 8, 22) in window)
        assertTrue(LocalDate.of(2026, 9, 4) in window)
        assertFalse(LocalDate.of(2026, 8, 21) in window)
        assertFalse(LocalDate.of(2026, 9, 5) in window)
    }

    @Test
    fun `구간이 달을 걸치면 두 달치 DEAL_YMD 를 모두 조회한다`() {
        val today = LocalDate.of(2026, 9, 4)
        assertEquals(listOf("202608", "202609"), TradeDateWindow.recentDealYmdCodes(today))
    }

    @Test
    fun `구간이 한 달 안에 있으면 DEAL_YMD 는 하나다`() {
        val today = LocalDate.of(2026, 9, 20)
        assertEquals(listOf("202609"), TradeDateWindow.recentDealYmdCodes(today))
    }

    @Test
    fun `해를 넘기는 구간도 올바른 DEAL_YMD 를 만든다`() {
        val today = LocalDate.of(2027, 1, 5)
        assertEquals(listOf("202612", "202701"), TradeDateWindow.recentDealYmdCodes(today))
    }

    @Test
    fun `윤년 2월도 정확히 계산한다`() {
        val today = LocalDate.of(2028, 3, 5)
        val window = TradeDateWindow.recent(today)
        assertEquals(LocalDate.of(2028, 2, 21), window.start)
        assertEquals(listOf("202802", "202803"), TradeDateWindow.dealYmdCodes(window))
    }

    @Test
    fun `조회 일수는 바꿀 수 있다`() {
        val today = LocalDate.of(2026, 9, 4)
        assertEquals(LocalDate.of(2026, 9, 4), TradeDateWindow.recent(today, days = 1).start)
        assertEquals(LocalDate.of(2026, 8, 6), TradeDateWindow.recent(today, days = 30).start)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `조회 일수가 0 이하이면 예외다`() {
        TradeDateWindow.recent(LocalDate.of(2026, 9, 4), days = 0)
    }

    @Test
    fun `연월일 필드를 계약일로 조립한다`() {
        assertEquals(
            LocalDate.of(2026, 9, 3),
            TradeDateWindow.parseDealDate("2026", "9", "3"),
        )
        assertEquals(
            LocalDate.of(2026, 9, 3),
            TradeDateWindow.parseDealDate(" 2026 ", " 09 ", " 03 "),
        )
    }

    @Test
    fun `계약일이 비었거나 형식이 어긋나면 null 이며 날짜를 임의로 보정하지 않는다`() {
        assertNull(TradeDateWindow.parseDealDate(null, "9", "3"))
        assertNull(TradeDateWindow.parseDealDate("2026", null, "3"))
        assertNull(TradeDateWindow.parseDealDate("2026", "9", null))
        assertNull(TradeDateWindow.parseDealDate("2026", "13", "3"))
        assertNull(TradeDateWindow.parseDealDate("2026", "2", "30"))
        assertNull(TradeDateWindow.parseDealDate("연도미상", "9", "3"))
    }
}
