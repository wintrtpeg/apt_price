package com.aptprice.tracker.presentation.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AxisScaleTest {

    @Test
    fun `눈금이 데이터 범위를 감싼다`() {
        val ticks = AxisScale.niceTicks(min = 87_532, max = 123_456)
        assertTrue("아래를 감싼다", ticks.first() <= 87_532)
        assertTrue("위를 감싼다", ticks.last() >= 123_456)
    }

    @Test
    fun `눈금 간격이 일정하다`() {
        val ticks = AxisScale.niceTicks(min = 50_000, max = 120_000)
        val gaps = ticks.zipWithNext { a, b -> b - a }.toSet()
        assertEquals("간격이 하나여야 한다: $ticks", 1, gaps.size)
    }

    @Test
    fun `눈금이 읽기 좋은 값이다`() {
        val ticks = AxisScale.niceTicks(min = 87_532, max = 123_456)
        val step = ticks[1] - ticks[0]
        // 1 / 2 / 5 × 10^n 중 하나여야 한다.
        val mantissa = generateSequence(step) { if (it % 10 == 0L && it > 10) it / 10 else null }.last()
        assertTrue("간격 $step 이 읽기 좋은 값이 아니다", mantissa in listOf(1L, 2L, 5L, 10L))
    }

    @Test
    fun `눈금이 오름차순이다`() {
        val ticks = AxisScale.niceTicks(min = 30_000, max = 250_000)
        assertEquals(ticks.sorted(), ticks)
        assertTrue(ticks.size >= 2)
    }

    @Test
    fun `모든 거래가 같은 금액이면 위아래로 여백을 만든다`() {
        val ticks = AxisScale.niceTicks(min = 80_000, max = 80_000)
        assertEquals(3, ticks.size)
        assertTrue(ticks.first() < 80_000)
        assertTrue(ticks.last() > 80_000)
        assertTrue(80_000L in ticks)
    }

    @Test
    fun `작은 금액대도 눈금이 만들어진다`() {
        val ticks = AxisScale.niceTicks(min = 3_000, max = 9_000)
        assertTrue(ticks.first() <= 3_000)
        assertTrue(ticks.last() >= 9_000)
        assertTrue(ticks.size >= 2)
    }

    @Test
    fun `정규화는 0에서 1 사이를 준다`() {
        assertEquals(0f, AxisScale.normalize(100, 100, 200), 1e-6f)
        assertEquals(1f, AxisScale.normalize(200, 100, 200), 1e-6f)
        assertEquals(0.5f, AxisScale.normalize(150, 100, 200), 1e-6f)
    }

    @Test
    fun `범위가 0이면 가운데에 둔다`() {
        assertEquals(0.5f, AxisScale.normalize(100, 100, 100), 1e-6f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `눈금 개수가 2 미만이면 예외다`() {
        AxisScale.niceTicks(min = 1, max = 10, tickCount = 1)
    }
}
