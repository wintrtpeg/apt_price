package com.aptprice.tracker.data.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class CachePolicyTest {

    private val today = LocalDate.of(2026, 9, 4)
    private val now: Instant = Instant.parse("2026-09-04T12:00:00Z")

    @Test
    fun `받은 적 없는 구간은 항상 만료다`() {
        assertTrue(CachePolicy.isStale("202609", null, now, today))
        assertTrue(CachePolicy.isStale("202101", null, now, today))
    }

    @Test
    fun `이번 달과 직전 두 달은 자주 다시 받는다`() {
        assertEquals(CachePolicy.RECENT_MONTH_TTL, CachePolicy.ttlFor("202609", today))
        assertEquals(CachePolicy.RECENT_MONTH_TTL, CachePolicy.ttlFor("202608", today))
        assertEquals(CachePolicy.RECENT_MONTH_TTL, CachePolicy.ttlFor("202607", today))
    }

    @Test
    fun `신고가 마무리된 달은 오래 유지한다`() {
        assertEquals(CachePolicy.SETTLED_MONTH_TTL, CachePolicy.ttlFor("202606", today))
        assertEquals(CachePolicy.SETTLED_MONTH_TTL, CachePolicy.ttlFor("202109", today))
    }

    @Test
    fun `최근 달은 유효기간이 지나면 만료된다`() {
        val justFetched = now.minus(Duration.ofHours(1))
        assertFalse(CachePolicy.isStale("202609", justFetched, now, today))

        val old = now.minus(Duration.ofHours(7))
        assertTrue(CachePolicy.isStale("202609", old, now, today))
    }

    @Test
    fun `지난 달은 하루가 지나도 유효하다`() {
        val yesterday = now.minus(Duration.ofDays(1))
        assertFalse(CachePolicy.isStale("202301", yesterday, now, today))

        val longAgo = now.minus(Duration.ofDays(31))
        assertTrue(CachePolicy.isStale("202301", longAgo, now, today))
    }

    @Test
    fun `5년 전 구간은 한 번 받으면 재조회가 거의 없다`() {
        val aWeekAgo = now.minus(Duration.ofDays(7))
        assertFalse("5년 전 달을 일주일 전에 받았으면 재조회 불필요", CachePolicy.isStale("202109", aWeekAgo, now, today))
    }

    @Test
    fun `기기 시계가 뒤로 간 경우에도 다시 받는다`() {
        val future = now.plus(Duration.ofDays(1))
        assertTrue(CachePolicy.isStale("202609", future, now, today))
    }

    @Test
    fun `아직 오지 않은 달은 조회 대상이 아니다`() {
        assertTrue(CachePolicy.isFutureMonth("202610", today))
        assertTrue(CachePolicy.isFutureMonth("202701", today))
        assertFalse(CachePolicy.isFutureMonth("202609", today))
        assertFalse(CachePolicy.isFutureMonth("202608", today))
    }

    @Test
    fun `계약월 형식이 어긋나면 미래로 보지 않고 안전하게 다시 받는다`() {
        assertFalse(CachePolicy.isFutureMonth("2026", today))
        assertFalse(CachePolicy.isFutureMonth("202613", today))
        assertFalse(CachePolicy.isFutureMonth("abcdef", today))
        assertEquals(CachePolicy.RECENT_MONTH_TTL, CachePolicy.ttlFor("202613", today))
    }
}
