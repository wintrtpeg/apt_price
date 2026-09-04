package com.aptprice.tracker.core.time

import com.aptprice.tracker.domain.region.RegionCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TradeQueryPlanTest {

    private val today = LocalDate.of(2026, 9, 4)

    @Test
    fun `기본 2주 조회는 지역당 두 달치만 부른다`() {
        val plan = TradeQueryPlan.of(TradePeriod.TWO_WEEKS, today, RegionCatalog.lawdCodes)
        assertEquals(listOf("202608", "202609"), plan.dealYmdCodes)
        assertEquals(36, plan.regionCount)
        assertEquals(72, plan.requestCount)
        assertEquals(144, plan.requestCountAllEndpoints)
    }

    @Test
    fun `5년 전체 지역 조회는 개월수만큼 곱해진다`() {
        val plan = TradeQueryPlan.of(TradePeriod.FIVE_YEARS, today, RegionCatalog.lawdCodes)
        // 2021-09 .. 2026-09 = 61개월
        assertEquals(61, plan.monthCount)
        assertEquals("202109", plan.dealYmdCodes.first())
        assertEquals("202609", plan.dealYmdCodes.last())
        assertEquals(61 * 36, plan.requestCount)
        assertEquals(61 * 36 * 2, plan.requestCountAllEndpoints)
        assertTrue("전체 지역 5년은 무거운 조회여야 함", plan.isHeavy)
    }

    @Test
    fun `단지 상세 차트는 지역 하나뿐이라 5년이어도 가볍다`() {
        val plan = TradeQueryPlan.forSingleRegion(TradePeriod.FIVE_YEARS, today, "41135")
        assertEquals(1, plan.regionCount)
        assertEquals(61, plan.requestCount)
        assertFalse("단일 지역 5년은 무겁지 않아야 함", plan.isHeavy)
    }

    @Test
    fun `짧은 기간은 전체 지역이어도 가볍다`() {
        val plan = TradeQueryPlan.of(TradePeriod.TWO_WEEKS, today, RegionCatalog.lawdCodes)
        assertFalse(plan.isHeavy)
    }

    @Test
    fun `지역을 좁히면 호출량이 줄어든다`() {
        val wide = TradeQueryPlan.of(TradePeriod.FIVE_YEARS, today, RegionCatalog.lawdCodes)
        val narrow = wide.narrowedTo(listOf("41135"))
        assertEquals(61, narrow.requestCount)
        assertFalse(narrow.isHeavy)
        // 기간과 계약월 목록은 그대로다.
        assertEquals(wide.dealYmdCodes, narrow.dealYmdCodes)
        assertEquals(wide.period, narrow.period)
    }

    @Test
    fun `중복 지역 코드는 한 번만 부른다`() {
        val plan = TradeQueryPlan.of(TradePeriod.ONE_MONTH, today, listOf("11680", "11680", "41135"))
        assertEquals(2, plan.regionCount)
    }

    @Test
    fun `호출 키는 지역 곱하기 월 만큼 생성되고 중복이 없다`() {
        val plan = TradeQueryPlan.of(TradePeriod.THREE_MONTHS, today, listOf("11680", "41135"))
        val keys = plan.requestKeys()
        assertEquals(plan.requestCount, keys.size)
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(TradeRequestKey("11680", "202609") in keys)
        assertTrue(TradeRequestKey("41135", "202606") in keys)
    }

    @Test
    fun `캐시된 키는 다시 부르지 않는다`() {
        val plan = TradeQueryPlan.of(TradePeriod.TWO_WEEKS, today, listOf("11680"))
        assertEquals(2, plan.requestKeys().size)

        val cached = setOf(TradeRequestKey("11680", "202608"))
        val pending = plan.pendingRequestKeys(cached)
        assertEquals(listOf(TradeRequestKey("11680", "202609")), pending)

        val allCached = plan.requestKeys().toSet()
        assertTrue(plan.pendingRequestKeys(allCached).isEmpty())
    }

    @Test
    fun `호출량 안내 문구에 기간과 지역 수가 들어간다`() {
        val plan = TradeQueryPlan.of(TradePeriod.FIVE_YEARS, today, RegionCatalog.lawdCodes)
        val notice = plan.volumeNotice()
        assertTrue(notice.contains("최근 5년"))
        assertTrue(notice.contains("36개 지역"))
        assertTrue(notice.contains("2196"))
    }

    @Test
    fun `개월수 계산이 구간과 일치한다`() {
        TradePeriod.entries.forEach { period ->
            val plan = TradeQueryPlan.of(period, today, listOf("11680"))
            assertEquals(
                "${period.label} 개월수",
                TradeDateWindow.monthCount(period.range(today)),
                plan.monthCount,
            )
        }
    }
}
