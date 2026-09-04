package com.aptprice.tracker.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyFormatterTest {

    @Test
    fun `국토부 응답의 콤마 금액 문자열을 만원 단위로 파싱한다`() {
        assertEquals(87_500L, MoneyFormatter.parseManwon("87,500"))
        assertEquals(87_500L, MoneyFormatter.parseManwon("   87,500 "))
        assertEquals(5_000L, MoneyFormatter.parseManwon("5000"))
        assertEquals(0L, MoneyFormatter.parseManwon("0"))
    }

    @Test
    fun `파싱할 수 없는 값은 null 이며 추정치로 대체하지 않는다`() {
        assertNull(MoneyFormatter.parseManwon(null))
        assertNull(MoneyFormatter.parseManwon(""))
        assertNull(MoneyFormatter.parseManwon("   "))
        assertNull(MoneyFormatter.parseManwon("미상"))
        assertNull(MoneyFormatter.parseManwon("8억"))
        assertNull(MoneyFormatter.parseManwon("12.5"))
    }

    @Test
    fun `억과 만원이 모두 있으면 둘 다 표기한다`() {
        assertEquals("8억 7,500만원", MoneyFormatter.formatManwon(87_500))
        assertEquals("12억 3,456만원", MoneyFormatter.formatManwon(123_456))
        assertEquals("1억 500만원", MoneyFormatter.formatManwon(10_500))
    }

    @Test
    fun `만원 단위가 0이면 억만 표기한다`() {
        assertEquals("8억", MoneyFormatter.formatManwon(80_000))
        assertEquals("10억", MoneyFormatter.formatManwon(100_000))
        assertEquals("1억", MoneyFormatter.formatManwon(10_000))
    }

    @Test
    fun `1억 미만은 만원으로만 표기한다`() {
        assertEquals("5,000만원", MoneyFormatter.formatManwon(5_000))
        assertEquals("900만원", MoneyFormatter.formatManwon(900))
        assertEquals("0원", MoneyFormatter.formatManwon(0))
    }

    @Test
    fun `음수는 부호를 유지한다`() {
        assertEquals("-3,000만원", MoneyFormatter.formatManwon(-3_000))
        assertEquals("-2억 500만원", MoneyFormatter.formatManwon(-20_500))
    }

    @Test
    fun `축약 표기는 차트 축에 맞게 억 단위로 줄인다`() {
        assertEquals("8.8억", MoneyFormatter.formatCompact(87_500))
        assertEquals("12억", MoneyFormatter.formatCompact(120_000))
        assertEquals("7,500만", MoneyFormatter.formatCompact(7_500))
        assertEquals("0", MoneyFormatter.formatCompact(0))
        assertEquals("-8.8억", MoneyFormatter.formatCompact(-87_500))
    }

    @Test
    fun `월세는 보증금과 월세액을 분리해서 보여준다`() {
        assertEquals("1억 / 120만원", MoneyFormatter.formatMonthlyRent(10_000, 120))
        assertEquals("5,000만원 / 90만원", MoneyFormatter.formatMonthlyRent(5_000, 90))
    }

    @Test
    fun `직전 거래가 없으면 등락률을 계산하지 않는다`() {
        assertNull(MoneyFormatter.changeRatePercent(87_500, null))
        assertNull(MoneyFormatter.changeRatePercent(87_500, 0))
    }

    @Test
    fun `등락률은 직전 거래 대비 백분율이다`() {
        val up = MoneyFormatter.changeRatePercent(110_000, 100_000)!!
        assertEquals(10.0, up, 1e-9)
        assertEquals("+10.0%", MoneyFormatter.formatChangeRate(up))

        val down = MoneyFormatter.changeRatePercent(90_000, 100_000)!!
        assertEquals(-10.0, down, 1e-9)
        assertEquals("-10.0%", MoneyFormatter.formatChangeRate(down))

        assertEquals("0.0%", MoneyFormatter.formatChangeRate(0.0))
    }
}
