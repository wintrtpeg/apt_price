package com.aptprice.tracker.presentation.feed

import com.aptprice.tracker.core.format.AreaBucket
import com.aptprice.tracker.domain.model.AptRent
import com.aptprice.tracker.domain.model.AptTrade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FeedBuilderTest {

    private val today = LocalDate.of(2026, 9, 4)

    private fun trade(
        apt: String = "피드테스트아파트",
        area: Double = 84.97,
        day: Int = 3,
        amount: Long = 87_500,
        floor: Int? = 10,
        canceled: Boolean = false,
        lawdCd: String = "11680",
        umdNm: String = "역삼동",
        buildYear: Int? = 2005,
    ) = AptTrade(
        lawdCd = lawdCd,
        umdNm = umdNm,
        aptName = apt,
        jibun = "101",
        exclusiveAreaM2 = area,
        floor = floor,
        buildYear = buildYear,
        dealDate = LocalDate.of(2026, 9, day),
        canceled = canceled,
        canceledDate = if (canceled) LocalDate.of(2026, 9, day + 1) else null,
        dealAmountManwon = amount,
        dealingGbn = "중개거래",
        registerDate = null,
    )

    private fun rent(
        apt: String = "전월세테스트",
        deposit: Long = 50_000,
        monthly: Long = 0,
        day: Int = 3,
        area: Double = 84.97,
    ) = AptRent(
        lawdCd = "41135",
        umdNm = "정자동",
        aptName = apt,
        jibun = "200",
        exclusiveAreaM2 = area,
        floor = 7,
        buildYear = 2018,
        dealDate = LocalDate.of(2026, 9, day),
        canceled = false,
        canceledDate = null,
        depositManwon = deposit,
        monthlyRentManwon = monthly,
        contractType = "신규",
        contractTerm = "25.09~27.09",
        previousDepositManwon = null,
        previousMonthlyRentManwon = null,
    )

    // ---------- 카드 표시 ----------

    @Test
    fun `카드에 단지 지역 면적 층 계약일 금액이 모두 담긴다`() {
        val item = FeedBuilder.build(listOf(trade()), FeedFilter(), today).single()

        assertEquals("피드테스트아파트", item.aptName)
        assertEquals("강남구 역삼동", item.regionLabel)
        assertEquals("84.97㎡ · 30평대", item.areaLabel)
        assertEquals("30평대", item.areaBucketLabel)
        assertEquals("10층", item.floorLabel)
        assertEquals("09.03 (목)", item.dateLabel)
        assertEquals("어제", item.relativeDateLabel)
        assertEquals("8억 7,500만원", item.priceLabel)
        assertEquals("2005년 준공", item.buildYearLabel)
        assertFalse(item.canceled)
    }

    @Test
    fun `원자료에 없는 값은 비워 두고 만들어 내지 않는다`() {
        val item = FeedBuilder
            .build(listOf(trade(floor = null, buildYear = null)), FeedFilter(), today)
            .single()

        assertNull(item.floorLabel)
        assertNull(item.buildYearLabel)
    }

    @Test
    fun `전세는 보증금만 월세는 보증금과 월세를 나눠 보여준다`() {
        val jeonse = FeedBuilder.build(listOf(rent(deposit = 50_000)), FeedFilter(), today).single()
        assertEquals("5억", jeonse.priceLabel)
        assertNull(jeonse.priceSubLabel)

        val monthly = FeedBuilder
            .build(listOf(rent(deposit = 10_000, monthly = 120)), FeedFilter(), today)
            .single()
        assertEquals("1억 / 120만원", monthly.priceLabel)
        assertEquals("보증금 1억 · 월세 120만원", monthly.priceSubLabel)
    }

    // ---------- 등락률 ----------

    @Test
    fun `직전 거래가 없으면 등락률을 표시하지 않는다`() {
        val item = FeedBuilder.build(listOf(trade()), FeedFilter(), today).single()

        assertNull("기준값이 없으면 만들어 내지 않는다", item.changeLabel)
        assertEquals(ChangeDirection.NONE, item.changeDirection)
    }

    @Test
    fun `같은 단지 같은 평형의 직전 거래와 비교한다`() {
        val items = FeedBuilder.build(
            listOf(trade(day = 1, amount = 80_000), trade(day = 3, amount = 88_000)),
            FeedFilter(),
            today,
        )

        val latest = items.first { it.dealDateEpochDay == LocalDate.of(2026, 9, 3).toEpochDay() }
        assertEquals("+10.0%", latest.changeLabel)
        assertEquals(ChangeDirection.UP, latest.changeDirection)

        val oldest = items.first { it.dealDateEpochDay == LocalDate.of(2026, 9, 1).toEpochDay() }
        assertNull(oldest.changeLabel)
    }

    @Test
    fun `하락은 방향이 DOWN 이다`() {
        val items = FeedBuilder.build(
            listOf(trade(day = 1, amount = 100_000), trade(day = 3, amount = 90_000)),
            FeedFilter(),
            today,
        )
        val latest = items.first { it.dealDateEpochDay == LocalDate.of(2026, 9, 3).toEpochDay() }
        assertEquals("-10.0%", latest.changeLabel)
        assertEquals(ChangeDirection.DOWN, latest.changeDirection)
    }

    @Test
    fun `평형이 다르면 서로 기준이 되지 않는다`() {
        val items = FeedBuilder.build(
            listOf(trade(day = 1, area = 59.99, amount = 60_000), trade(day = 3, area = 84.97, amount = 88_000)),
            FeedFilter(),
            today,
        )
        assertTrue("평형이 다르므로 비교 대상이 아니다", items.all { it.changeLabel == null })
    }

    @Test
    fun `단지가 다르면 서로 기준이 되지 않는다`() {
        val items = FeedBuilder.build(
            listOf(trade(apt = "가아파트", day = 1, amount = 60_000), trade(apt = "나아파트", day = 3, amount = 88_000)),
            FeedFilter(),
            today,
        )
        assertTrue(items.all { it.changeLabel == null })
    }

    @Test
    fun `해제된 계약은 다음 거래의 기준이 되지 않는다`() {
        val items = FeedBuilder.build(
            listOf(
                trade(day = 1, amount = 80_000),
                trade(day = 2, amount = 200_000, canceled = true),
                trade(day = 3, amount = 88_000),
            ),
            FeedFilter(),
            today,
        )

        // 9/3 은 해제된 9/2(20억) 이 아니라 9/1(8억) 과 비교되어야 한다.
        val latest = items.first { it.dealDateEpochDay == LocalDate.of(2026, 9, 3).toEpochDay() }
        assertEquals("+10.0%", latest.changeLabel)
    }

    @Test
    fun `해제 건도 목록에는 남고 표시로 구분된다`() {
        val items = FeedBuilder.build(listOf(trade(canceled = true)), FeedFilter(), today)
        assertEquals(1, items.size)
        assertTrue(items.single().canceled)
    }

    @Test
    fun `해제 건을 빼는 옵션이 동작한다`() {
        val items = FeedBuilder.build(
            listOf(trade(day = 1), trade(day = 2, canceled = true)),
            FeedFilter(includeCanceled = false),
            today,
        )
        assertEquals(1, items.size)
        assertFalse(items.single().canceled)
    }

    // ---------- 필터 ----------

    @Test
    fun `평형대 필터가 비어 있으면 전부 통과한다`() {
        val items = FeedBuilder.build(
            listOf(trade(area = 39.72), trade(area = 84.97), trade(area = 134.88)),
            FeedFilter(),
            today,
        )
        assertEquals(3, items.size)
    }

    @Test
    fun `평형대 필터를 걸면 해당 구간만 남는다`() {
        val deals = listOf(trade(area = 39.72), trade(area = 84.97), trade(area = 134.88))

        val small = FeedBuilder.build(deals, FeedFilter(areaBuckets = setOf(AreaBucket.UNDER_20)), today)
        assertEquals(1, small.size)
        assertEquals("10평대 이하", small.single().areaBucketLabel)

        val mixed = FeedBuilder.build(
            deals,
            FeedFilter(areaBuckets = setOf(AreaBucket.UNDER_20, AreaBucket.OVER_50)),
            today,
        )
        assertEquals(2, mixed.size)
    }

    // ---------- 정렬 ----------

    @Test
    fun `기본 정렬은 최신 거래순이다`() {
        val items = FeedBuilder.build(
            listOf(trade(day = 1), trade(day = 4), trade(day = 2)),
            FeedFilter(sort = FeedSort.LATEST),
            today,
        )
        assertEquals(
            listOf(4, 2, 1).map { LocalDate.of(2026, 9, it).toEpochDay() },
            items.map { it.dealDateEpochDay },
        )
    }

    @Test
    fun `금액순 정렬이 동작한다`() {
        val deals = listOf(
            trade(apt = "가", amount = 50_000),
            trade(apt = "나", amount = 120_000),
            trade(apt = "다", amount = 80_000),
        )

        val desc = FeedBuilder.build(deals, FeedFilter(sort = FeedSort.PRICE_DESC), today)
        assertEquals(listOf(120_000L, 80_000L, 50_000L), desc.map { it.sortAmountManwon })

        val asc = FeedBuilder.build(deals, FeedFilter(sort = FeedSort.PRICE_ASC), today)
        assertEquals(listOf(50_000L, 80_000L, 120_000L), asc.map { it.sortAmountManwon })
    }

    @Test
    fun `월세는 보증금 기준으로 정렬한다`() {
        val items = FeedBuilder.build(
            listOf(rent(apt = "가", deposit = 5_000, monthly = 200), rent(apt = "나", deposit = 30_000, monthly = 50)),
            FeedFilter(sort = FeedSort.PRICE_DESC),
            today,
        )
        assertEquals(listOf(30_000L, 5_000L), items.map { it.sortAmountManwon })
    }

    @Test
    fun `목록 키는 거래마다 다르고 같은 거래면 같다`() {
        val a = FeedBuilder.build(listOf(trade(day = 1), trade(day = 2)), FeedFilter(), today)
        assertEquals(2, a.map { it.id }.toSet().size)

        val same = FeedBuilder.build(listOf(trade()), FeedFilter(), today).single()
        val sameAgain = FeedBuilder.build(listOf(trade()), FeedFilter(), today).single()
        assertEquals(same.id, sameAgain.id)
    }

    @Test
    fun `상세 화면으로 넘길 단지 평형 키가 담긴다`() {
        val item = FeedBuilder.build(listOf(trade()), FeedFilter(), today).single()
        assertTrue(item.complexAreaKey.contains("11680"))
        assertTrue(item.complexAreaKey.contains("피드테스트아파트"))
        assertTrue(item.complexAreaKey.contains("84.97"))
    }
}
