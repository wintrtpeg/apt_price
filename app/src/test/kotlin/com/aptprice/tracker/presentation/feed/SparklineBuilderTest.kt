package com.aptprice.tracker.presentation.feed

import com.aptprice.tracker.domain.model.DealTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 카드 미니 그래프의 접기 규칙.
 *
 * 여기서 지키려는 것은 하나다 — **없는 거래를 그리지 않는다.**
 * 점이 모자라면 그래프를 만들지 않고, 값이 없는 자리를 채워 넣지 않는다.
 */
class SparklineBuilderTest {

    @Test
    fun `거래가 한 건뿐이면 그래프를 만들지 않는다`() {
        // 한 점으로는 추이가 아니다. 선을 그으려면 없는 거래를 지어내야 한다.
        assertNull(SparklineBuilder.of(listOf(87_500L)))
    }

    @Test
    fun `거래가 없으면 그래프를 만들지 않는다`() {
        assertNull(SparklineBuilder.of(emptyList()))
    }

    @Test
    fun `두 건이면 그린다`() {
        val spark = SparklineBuilder.of(listOf(80_000L, 90_000L))
        assertNotNull(spark)
        assertEquals(2, spark!!.pointCount)
    }

    @Test
    fun `최저가는 바닥 최고가는 천장에 놓는다`() {
        val spark = SparklineBuilder.of(listOf(80_000L, 100_000L, 90_000L))!!

        assertEquals(0f, spark.points[0], 0.0001f)
        assertEquals(1f, spark.points[1], 0.0001f)
        assertEquals(0.5f, spark.points[2], 0.0001f)
    }

    @Test
    fun `값이 모두 같으면 바닥이 아니라 가운데에 놓는다`() {
        // 0 으로 두면 값이 낮은 것처럼 보인다. 사실은 변동이 없는 것이다.
        val spark = SparklineBuilder.of(listOf(87_500L, 87_500L, 87_500L))!!

        assertTrue(spark.points.all { it == 0.5f })
        assertEquals(ChangeDirection.FLAT, spark.direction)
    }

    @Test
    fun `점이 많으면 최근 것만 남긴다`() {
        val amounts = (1..30).map { it * 1_000L }

        val spark = SparklineBuilder.of(amounts)!!

        assertEquals(SparklineBuilder.MAX_POINTS, spark.pointCount)
        // 남은 것이 최근 8건인지 — 오래된 쪽이 잘렸으면 최저점은 23,000 이다.
        assertEquals("최근 ${SparklineBuilder.MAX_POINTS}건 · 거래순", spark.caption)
        assertEquals(0f, spark.points.first(), 0.0001f)
        assertEquals(1f, spark.points.last(), 0.0001f)
    }

    @Test
    fun `오르면 상승 내리면 하락`() {
        assertEquals(ChangeDirection.UP, SparklineBuilder.of(listOf(80_000L, 90_000L))!!.direction)
        assertEquals(ChangeDirection.DOWN, SparklineBuilder.of(listOf(90_000L, 80_000L))!!.direction)
    }

    @Test
    fun `방향은 중간이 아니라 처음과 마지막으로 정한다`() {
        // 중간에 치솟았다가 처음보다 낮게 끝나면 하락이다.
        val spark = SparklineBuilder.of(listOf(90_000L, 120_000L, 80_000L))!!

        assertEquals(ChangeDirection.DOWN, spark.direction)
    }

    @Test
    fun `설명에 등락률을 적지 않는다`() {
        // 카드의 등락 배지는 '직전 거래 대비' 라 기준이 다르다.
        // 나란히 놓으면 서로 다른 두 숫자가 되어 읽는 사람을 헷갈리게 한다.
        val spark = SparklineBuilder.of(listOf(80_000L, 90_000L))!!

        assertFalse(spark.caption, spark.caption.contains("%"))
        assertEquals("최근 2건 · 거래순", spark.caption)
    }

    @Test
    fun `가로축이 시간이 아니라는 것을 설명에 밝힌다`() {
        // 카드만 한 폭에 시간축을 넣으면 옛 거래와 최근 거래가 한쪽에 뭉친다.
        // 순서축으로 그리는 대신, 순서축이라는 사실을 화면에 적는다.
        val spark = SparklineBuilder.of(listOf(80_000L, 90_000L, 85_000L))!!

        assertTrue(spark.caption, spark.caption.contains("거래순"))
    }

    @Test
    fun `월세 탭은 보증금 흐름이라고 밝힌다`() {
        // 월세 탭에서 그리는 값은 보증금이다. 밝혀 두지 않으면 월세로 읽힌다.
        assertEquals("보증금", SparklineBuilder.amountLabelFor(DealTab.MONTHLY))
        assertNull(SparklineBuilder.amountLabelFor(DealTab.SALE))
        assertNull(SparklineBuilder.amountLabelFor(DealTab.JEONSE))

        val spark = SparklineBuilder.of(listOf(5_000L, 10_000L), amountLabel = "보증금")!!
        assertEquals("보증금 최근 2건 · 거래순", spark.caption)
    }

    @Test
    fun `점이 모자란 키는 아예 빠진다`() {
        val series = mapOf(
            "둘있는키" to listOf(80_000L, 90_000L),
            "하나뿐인키" to listOf(87_500L),
            "빈키" to emptyList<Long>(),
        )

        val built = SparklineBuilder.build(series, DealTab.SALE)

        // 카드가 그래프 자리를 아예 두지 않도록, 빈 그래프를 남기지 않는다.
        assertEquals(setOf("둘있는키"), built.keys)
    }

    @Test
    fun `build 는 탭에 맞는 금액 설명을 붙인다`() {
        val series = mapOf("키" to listOf(5_000L, 10_000L))

        assertEquals("최근 2건 · 거래순", SparklineBuilder.build(series, DealTab.JEONSE)["키"]!!.caption)
        assertEquals("보증금 최근 2건 · 거래순", SparklineBuilder.build(series, DealTab.MONTHLY)["키"]!!.caption)
    }
}
