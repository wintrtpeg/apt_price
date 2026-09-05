package com.aptprice.tracker.presentation.feed

import com.aptprice.tracker.core.time.TradePeriod
import com.aptprice.tracker.domain.model.DealTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FeedSummaryBuilderTest {

    private val today = LocalDate.of(2026, 9, 14)

    private fun item(
        day: Int,
        amount: Long,
        canceled: Boolean = false,
        month: Int = 9,
    ) = FeedItemUi(
        id = "$month-$day-$amount-$canceled",
        complexAreaKey = "11680|역삼동|테스트|84.97",
        aptName = "테스트",
        regionLabel = "강남구 역삼동",
        areaLabel = "84.97㎡",
        areaBucketLabel = "30평대",
        floorLabel = null,
        dateLabel = "$month.$day",
        relativeDateLabel = "",
        priceLabel = "",
        priceSubLabel = null,
        changeLabel = null,
        changeDirection = ChangeDirection.NONE,
        buildYearLabel = null,
        canceled = canceled,
        dealDateEpochDay = LocalDate.of(2026, month, day).toEpochDay(),
        sortAmountManwon = amount,
    )

    private fun build(items: List<FeedItemUi>, period: TradePeriod = TradePeriod.TWO_WEEKS) =
        FeedSummaryBuilder.build(items, period, DealTab.SALE, today)

    @Test
    fun `거래가 없으면 요약도 없다`() {
        // 빈 요약 카드를 0 으로 채워 띄우면 "0원" 처럼 읽힌다. 아예 만들지 않는다.
        assertNull(build(emptyList()))
    }

    @Test
    fun `건수와 최고 최저 중간가를 집계한다`() {
        val summary = build(
            listOf(item(3, 80_000), item(5, 100_000), item(7, 120_000)),
        )!!

        assertEquals(3, summary.dealCount)
        assertEquals(100_000L, summary.medianManwon)
        assertEquals(120_000L, summary.maxManwon)
        assertEquals(80_000L, summary.minManwon)
    }

    @Test
    fun `짝수 개면 가운데 두 값의 평균을 중간가로 쓴다`() {
        val summary = build(listOf(item(3, 80_000), item(4, 90_000)))!!
        assertEquals(85_000L, summary.medianManwon)
    }

    @Test
    fun `평균이 아니라 중간가다`() {
        // 한 건의 초고가 거래가 시세를 끌어올려 잘못 읽히는 것을 막는다.
        val items = listOf(item(3, 80_000), item(4, 82_000), item(5, 500_000))
        val summary = build(items)!!

        assertEquals("가운데 값", 82_000L, summary.medianManwon)
        val average = items.sumOf { it.sortAmountManwon } / items.size
        assertTrue("평균($average)을 쓰면 시세가 부풀려진다", summary.medianManwon!! < average)
    }

    @Test
    fun `해제된 계약은 금액 통계에서 빠지되 건수는 알린다`() {
        val summary = build(
            listOf(item(3, 80_000), item(5, 900_000, canceled = true)),
        )!!

        // 성사되지 않은 가격이 최고가로 올라오면 안 된다.
        assertEquals(1, summary.dealCount)
        assertEquals(1, summary.canceledCount)
        assertEquals(80_000L, summary.maxManwon)
        assertEquals(80_000L, summary.medianManwon)
    }

    @Test
    fun `거래가 없는 구간은 중간가가 없다`() {
        val summary = build(listOf(item(1, 80_000), item(14, 90_000)))!!

        val empty = summary.buckets.filter { it.count == 0 }
        assertTrue("빈 구간이 있어야 하는 조건이다", empty.isNotEmpty())
        // 없는 구간의 값을 앞뒤에서 끌어와 채우면 그건 지어낸 시세다.
        empty.forEach {
            assertNull(it.medianManwon)
            assertNull(it.priceRatio)
            assertEquals(0f, it.countRatio, 0.0001f)
        }
    }

    @Test
    fun `구간의 건수 합이 목록 건수와 같다`() {
        val items = (1..14).map { item(it, 80_000L + it * 100) }
        val summary = build(items)!!

        assertEquals(items.size, summary.buckets.sumOf { it.count })
    }

    @Test
    fun `긴 기간도 칸 수가 폭주하지 않는다`() {
        val items = listOf(item(1, 80_000, month = 3), item(14, 90_000))
        val summary = build(items, TradePeriod.FIVE_YEARS)!!

        // 5년을 하루 한 칸으로 나누면 1800칸이 된다. 화면에 그릴 수 없다.
        assertTrue("칸이 너무 많다: ${summary.buckets.size}", summary.buckets.size <= 14)
        assertTrue(summary.buckets.isNotEmpty())
    }

    @Test
    fun `구간이 기간을 빠짐없이 이어서 덮는다`() {
        val summary = build(listOf(item(3, 80_000)), TradePeriod.THREE_MONTHS)!!
        val range = TradePeriod.THREE_MONTHS.range(today)

        assertEquals(range.start, summary.buckets.first().startDate)
        assertEquals("마지막 칸이 오늘을 넘지 않는다", range.endInclusive, summary.buckets.last().endDate)
        summary.buckets.zipWithNext { a, b ->
            assertEquals("칸 사이에 틈이 있다", a.endDate.plusDays(1), b.startDate)
        }
    }

    @Test
    fun `막대 높이는 가장 많은 칸을 기준으로 한다`() {
        val items = listOf(item(1, 80_000)) + (2..4).map { item(2, 80_000L + it) }
        val summary = build(items)!!

        val busiest = summary.buckets.maxByOrNull { it.count }!!
        assertEquals(1f, busiest.countRatio, 0.0001f)
        summary.buckets.forEach {
            assertTrue("막대 높이가 0~1 을 벗어났다: ${it.countRatio}", it.countRatio in 0f..1f)
        }
    }

    @Test
    fun `값이 하나뿐이면 선을 가운데 둔다`() {
        // 0 으로 두면 바닥에 붙어 값이 낮은 것처럼 보인다. 사실은 비교 대상이 없는 것이다.
        val summary = build(listOf(item(3, 80_000)))!!
        val drawn = summary.buckets.single { it.count > 0 }
        assertEquals(0.5f, drawn.priceRatio!!, 0.0001f)
    }

    @Test
    fun `거래가 한 칸에만 있으면 추이로 보지 않는다`() {
        val oneBucket = build(listOf(item(3, 80_000), item(3, 82_000)))!!
        assertFalse("한 칸짜리는 추이가 아니다", oneBucket.hasChart)

        val spread = build(listOf(item(1, 80_000), item(14, 90_000)))!!
        assertTrue(spread.hasChart)
    }

    @Test
    fun `월세 탭은 보증금 기준임을 밝힌다`() {
        val summary = FeedSummaryBuilder.build(
            listOf(item(3, 10_000)),
            TradePeriod.TWO_WEEKS,
            DealTab.MONTHLY,
            today,
        )!!
        assertTrue(summary.medianCaption.contains("보증금"))
    }
}
