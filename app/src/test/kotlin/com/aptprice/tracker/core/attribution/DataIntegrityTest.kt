package com.aptprice.tracker.core.attribution

import com.aptprice.tracker.core.format.MoneyFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class DataIntegrityTest {

    @Test
    fun `출처 라벨에 제공기관과 기준일시가 들어간다`() {
        val label = DataSourceAttribution.label(LocalDateTime.of(2026, 9, 4, 11, 20))
        assertEquals(
            "데이터 출처: 국토교통부 실거래가 공개시스템 (기준일시: 2026-09-04 11:20)",
            label,
        )
    }

    @Test
    fun `동기화 전에도 출처는 표기한다`() {
        assertTrue(DataSourceAttribution.LABEL_NOT_SYNCED.contains("국토교통부 실거래가 공개시스템"))
    }

    @Test
    fun `값이 없으면 미신고 문구를 그대로 노출한다`() {
        val missing: TradeValue<Long> = TradeValue.ofNullable(MoneyFormatter.parseManwon("미상"))
        assertTrue(missing is TradeValue.Missing)
        assertEquals(
            DataSourceAttribution.NOT_REPORTED,
            missing.display { MoneyFormatter.formatManwon(it) },
        )
        assertNull(missing.valueOrNull())
    }

    @Test
    fun `값이 있으면 포맷해서 노출한다`() {
        val reported: TradeValue<Long> = TradeValue.ofNullable(MoneyFormatter.parseManwon("87,500"))
        assertTrue(reported is TradeValue.Reported)
        assertEquals("8억 7,500만원", reported.display { MoneyFormatter.formatManwon(it) })
        assertEquals(87_500L, reported.valueOrNull())
    }

    @Test
    fun `누락 사유는 호출부가 지정할 수 있다`() {
        val missing = TradeValue.ofNullable<Long>(null, DataSourceAttribution.UNAVAILABLE)
        assertEquals(
            DataSourceAttribution.UNAVAILABLE,
            missing.display { MoneyFormatter.formatManwon(it) },
        )
    }
}
