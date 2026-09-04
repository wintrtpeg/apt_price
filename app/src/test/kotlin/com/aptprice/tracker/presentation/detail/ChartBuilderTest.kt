package com.aptprice.tracker.presentation.detail

import com.aptprice.tracker.core.time.TradePeriod
import com.aptprice.tracker.domain.model.AptRent
import com.aptprice.tracker.domain.model.AptTrade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ChartBuilderTest {

    private val today = LocalDate.of(2026, 9, 4)

    private fun trade(date: LocalDate, amount: Long, canceled: Boolean = false, floor: Int? = 10) =
        AptTrade(
            lawdCd = "11680", umdNm = "역삼동", aptName = "차트테스트", jibun = "101",
            exclusiveAreaM2 = 84.97, floor = floor, buildYear = 2005,
            dealDate = date, canceled = canceled,
            canceledDate = if (canceled) date.plusDays(5) else null,
            dealAmountManwon = amount, dealingGbn = null, registerDate = null,
        )

    private fun jeonse(date: LocalDate, deposit: Long) = AptRent(
        lawdCd = "11680", umdNm = "역삼동", aptName = "차트테스트", jibun = "101",
        exclusiveAreaM2 = 84.97, floor = 5, buildYear = 2005,
        dealDate = date, canceled = false, canceledDate = null,
        depositManwon = deposit, monthlyRentManwon = 0,
        contractType = null, contractTerm = null,
        previousDepositManwon = null, previousMonthlyRentManwon = null,
    )

    // ---------- 기본 ----------

    @Test
    fun `거래가 없으면 빈 차트이고 선을 그리지 않는다`() {
        val chart = ChartBuilder.build(emptyList(), emptyList(), TradePeriod.ONE_YEAR, today)

        assertTrue(chart.isEmpty)
        assertEquals(0, chart.pointCount)
        assertTrue(chart.series.isEmpty())
        assertNull("매매가 없으면 최고가 가이드라인도 없다", chart.peak)
        // 가로축은 기간을 나타내므로 데이터가 없어도 남는다.
        assertTrue(chart.xTicks.isNotEmpty())
    }

    @Test
    fun `매매와 전세가 각각 계열로 나온다`() {
        val chart = ChartBuilder.build(
            trades = listOf(trade(today.minusDays(30), 87_500), trade(today.minusDays(10), 92_000)),
            jeonse = listOf(jeonse(today.minusDays(20), 50_000)),
            period = TradePeriod.ONE_YEAR,
            today = today,
        )

        assertEquals(2, chart.series.size)
        assertEquals(2, chart.series.first { it.type == SeriesType.SALE }.points.size)
        assertEquals(1, chart.series.first { it.type == SeriesType.JEONSE }.points.size)
        assertEquals(3, chart.pointCount)
    }

    @Test
    fun `계열이 하나만 있으면 그 계열만 나온다`() {
        val chart = ChartBuilder.build(
            trades = listOf(trade(today.minusDays(10), 87_500)),
            jeonse = emptyList(),
            period = TradePeriod.ONE_YEAR,
            today = today,
        )
        assertEquals(1, chart.series.size)
        assertEquals(SeriesType.SALE, chart.series.single().type)
    }

    // ---------- 해제 계약 ----------

    @Test
    fun `해제된 계약은 차트에 찍지 않는다`() {
        val chart = ChartBuilder.build(
            trades = listOf(
                trade(today.minusDays(30), 87_500),
                trade(today.minusDays(20), 300_000, canceled = true),
            ),
            jeonse = emptyList(),
            period = TradePeriod.ONE_YEAR,
            today = today,
        )

        assertEquals(1, chart.pointCount)
        assertEquals(87_500L, chart.series.single().points.single().amountManwon)
    }

    @Test
    fun `해제된 계약은 기간 내 최고가로 보지 않는다`() {
        val chart = ChartBuilder.build(
            trades = listOf(
                trade(today.minusDays(30), 87_500),
                trade(today.minusDays(20), 300_000, canceled = true),
            ),
            jeonse = emptyList(),
            period = TradePeriod.ONE_YEAR,
            today = today,
        )

        assertNotNull(chart.peak)
        assertEquals("성사되지 않은 30억은 최고가가 아니다", 87_500L, chart.peak!!.amountManwon)
        assertTrue(chart.peak.label.contains("8억 7,500만원"))
    }

    // ---------- 공백 구간 ----------

    @Test
    fun `가까운 거래끼리는 한 구간으로 이어진다`() {
        val chart = ChartBuilder.build(
            trades = listOf(
                trade(today.minusDays(60), 80_000),
                trade(today.minusDays(40), 85_000),
                trade(today.minusDays(20), 90_000),
            ),
            jeonse = emptyList(),
            period = TradePeriod.ONE_YEAR,
            today = today,
        )

        val series = chart.series.single()
        assertEquals("공백이 짧으면 한 덩어리", 1, series.segments.size)
        assertEquals(3, series.segments.single().points.size)
    }

    @Test
    fun `신고가 오래 없던 구간은 선을 끊는다`() {
        // 1년 차트의 끊는 기준은 약 2개월(365 / 6 ≒ 60일)
        val chart = ChartBuilder.build(
            trades = listOf(
                trade(today.minusDays(350), 80_000),
                trade(today.minusDays(340), 82_000),
                // 300일 공백
                trade(today.minusDays(40), 95_000),
            ),
            jeonse = emptyList(),
            period = TradePeriod.ONE_YEAR,
            today = today,
        )

        val series = chart.series.single()
        assertEquals("긴 공백은 구간을 나눈다", 2, series.segments.size)
        assertEquals(2, series.segments[0].points.size)
        assertEquals(1, series.segments[1].points.size)
        // 점 자체는 모두 남는다. 실제로 신고된 거래이기 때문이다.
        assertEquals(3, series.points.size)
    }

    @Test
    fun `기간이 길수록 끊는 기준도 넓어진다`() {
        val deals = listOf(
            trade(today.minusDays(400), 80_000),
            trade(today.minusDays(300), 90_000),
        )

        // 1년 차트: 100일 공백은 기준(약 60일)을 넘어 끊긴다.
        // (다만 1년 구간에는 400일 전 거래가 들어오지 않으므로 5년으로 비교한다)
        val fiveYear = ChartBuilder.build(deals, emptyList(), TradePeriod.FIVE_YEARS, today)
        assertEquals("5년 차트의 기준은 약 10개월이라 100일 공백은 끊기지 않는다",
            1, fiveYear.series.single().segments.size)

        val threeMonth = ChartBuilder.build(
            listOf(trade(today.minusDays(80), 80_000), trade(today.minusDays(5), 90_000)),
            emptyList(),
            TradePeriod.THREE_MONTHS,
            today,
        )
        assertEquals("3개월 차트의 기준은 약 15일이라 75일 공백은 끊긴다",
            2, threeMonth.series.single().segments.size)
    }

    @Test
    fun `거래가 하나면 구간 하나에 점 하나다`() {
        val chart = ChartBuilder.build(
            listOf(trade(today.minusDays(10), 87_500)),
            emptyList(),
            TradePeriod.ONE_YEAR,
            today,
        )
        val series = chart.series.single()
        assertEquals(1, series.segments.size)
        assertEquals(1, series.points.size)
    }

    // ---------- 좌표 ----------

    @Test
    fun `가로 좌표는 기간 안에서 0에서 1 사이다`() {
        val chart = ChartBuilder.build(
            listOf(
                trade(today.minusDays(364), 80_000),
                trade(today, 90_000),
            ),
            emptyList(),
            TradePeriod.ONE_YEAR,
            today,
        )
        val points = chart.series.single().points
        assertTrue(points.all { it.x in 0f..1f })
        assertTrue("오래된 거래가 왼쪽", points.first().x < points.last().x)
        assertEquals(1f, points.last().x, 0.02f)
    }

    @Test
    fun `세로 좌표는 금액 순서를 지킨다`() {
        val chart = ChartBuilder.build(
            listOf(
                trade(today.minusDays(30), 60_000),
                trade(today.minusDays(20), 90_000),
                trade(today.minusDays(10), 75_000),
            ),
            emptyList(),
            TradePeriod.ONE_YEAR,
            today,
        )
        val points = chart.series.single().points
        val byAmount = points.sortedBy { it.amountManwon }
        assertTrue(byAmount[0].y < byAmount[1].y)
        assertTrue(byAmount[1].y < byAmount[2].y)
        assertTrue(points.all { it.y in 0f..1f })
    }

    @Test
    fun `매매와 전세가 같은 세로축을 쓴다`() {
        val chart = ChartBuilder.build(
            listOf(trade(today.minusDays(30), 100_000)),
            listOf(jeonse(today.minusDays(20), 50_000)),
            TradePeriod.ONE_YEAR,
            today,
        )
        assertTrue("축은 두 계열을 모두 감싼다", chart.minAmountManwon <= 50_000)
        assertTrue(chart.maxAmountManwon >= 100_000)
    }

    @Test
    fun `층 정보가 점에 실려 툴팁에 쓸 수 있다`() {
        val chart = ChartBuilder.build(
            listOf(trade(today.minusDays(10), 87_500, floor = 15)),
            emptyList(),
            TradePeriod.ONE_YEAR,
            today,
        )
        assertEquals(15, chart.series.single().points.single().floor)
    }

    @Test
    fun `층이 없는 거래도 점으로 남는다`() {
        val chart = ChartBuilder.build(
            listOf(trade(today.minusDays(10), 87_500, floor = null)),
            emptyList(),
            TradePeriod.ONE_YEAR,
            today,
        )
        assertNull(chart.series.single().points.single().floor)
    }

    @Test
    fun `가로축 눈금이 기간 양끝을 덮는다`() {
        val chart = ChartBuilder.build(
            listOf(trade(today.minusDays(10), 87_500)),
            emptyList(),
            TradePeriod.FIVE_YEARS,
            today,
        )
        assertEquals(chart.startDate, chart.xTicks.first().date)
        assertEquals(0f, chart.xTicks.first().position, 1e-6f)
        assertEquals(1f, chart.xTicks.last().position, 1e-6f)
        assertTrue(chart.xTicks.map { it.position }.zipWithNext().all { it.first < it.second })
    }
}
