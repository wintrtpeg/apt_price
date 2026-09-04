package com.aptprice.tracker.regression

import com.aptprice.tracker.core.time.TradePeriod
import com.aptprice.tracker.core.time.TradeQueryPlan
import com.aptprice.tracker.data.remote.api.ServiceKeyProvider
import com.aptprice.tracker.data.repository.FakeMolitApiService
import com.aptprice.tracker.data.repository.FakeRentDao
import com.aptprice.tracker.data.repository.FakeServiceKeyStore
import com.aptprice.tracker.data.repository.FakeSyncStateDao
import com.aptprice.tracker.data.repository.FakeTradeDao
import com.aptprice.tracker.data.repository.TradeRepositoryImpl
import com.aptprice.tracker.domain.model.AptRent
import com.aptprice.tracker.domain.model.AptTrade
import com.aptprice.tracker.presentation.detail.DetailUiState
import com.aptprice.tracker.presentation.feed.FeedBuilder
import com.aptprice.tracker.presentation.feed.FeedFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 앱이 켜자마자 종료되던 두 원인에 대한 회귀 테스트.
 *
 * 둘 다 실기기에서만 드러났다. 로컬에서 Compose 를 실행할 수 없어 놓쳤던 것들이다.
 */
class CrashRegressionTest {

    private val today = LocalDate.of(2026, 9, 4)

    // ---------- 원인 1: 목록 키 중복 ----------

    private fun trade(
        apt: String = "같은값테스트",
        day: Int = 3,
        amount: Long = 87_500,
        floor: Int? = 10,
        area: Double = 84.97,
    ) = AptTrade(
        lawdCd = "11680", umdNm = "역삼동", aptName = apt, jibun = "101",
        exclusiveAreaM2 = area, floor = floor, buildYear = 2005,
        dealDate = LocalDate.of(2026, 9, day), canceled = false, canceledDate = null,
        dealAmountManwon = amount, dealingGbn = null, registerDate = null,
    )

    @Test
    fun `완전히 같은 값의 거래가 여러 건이어도 목록 키가 겹치지 않는다`() {
        // 대단지에서 같은 날 같은 평형 같은 층 같은 금액으로 두 건이 신고되는 일은 실제로 있다.
        // 원자료에 동(棟) 정보가 없어 구분할 방법이 없다.
        // 키가 겹치면 Compose 의 LazyColumn 이 예외를 던져 앱이 즉시 종료된다.
        val identical = List(3) { trade() }

        val items = FeedBuilder.build(identical, FeedFilter(), today)

        assertEquals("세 건 모두 목록에 남아야 한다", 3, items.size)
        assertEquals("키가 모두 달라야 한다", 3, items.map { it.id }.toSet().size)
    }

    @Test
    fun `층이 없는 같은 거래도 키가 겹치지 않는다`() {
        val identical = List(2) { trade(floor = null) }
        val items = FeedBuilder.build(identical, FeedFilter(), today)
        assertEquals(2, items.map { it.id }.toSet().size)
    }

    @Test
    fun `많은 거래 중 중복이 섞여도 키는 모두 유일하다`() {
        val deals = buildList {
            repeat(5) { add(trade(day = 1)) }
            repeat(3) { add(trade(day = 2, amount = 90_000)) }
            add(trade(day = 3, amount = 95_000))
            repeat(2) { add(trade(apt = "다른단지", day = 1)) }
        }
        val items = FeedBuilder.build(deals, FeedFilter(), today)

        assertEquals(11, items.size)
        assertEquals("중복 키가 하나라도 있으면 앱이 죽는다", 11, items.map { it.id }.toSet().size)
    }

    @Test
    fun `거래 이력 표의 키도 겹치지 않는다`() {
        val rows = DetailUiState.historyOf(
            trades = List(3) { trade() },
            rents = List(2) {
                AptRent(
                    lawdCd = "11680", umdNm = "역삼동", aptName = "같은값테스트", jibun = null,
                    exclusiveAreaM2 = 84.97, floor = 5, buildYear = 2005,
                    dealDate = LocalDate.of(2026, 9, 3), canceled = false, canceledDate = null,
                    depositManwon = 50_000, monthlyRentManwon = 0,
                    contractType = null, contractTerm = null,
                    previousDepositManwon = null, previousMonthlyRentManwon = null,
                )
            },
            peakAmountManwon = 87_500,
        )

        assertEquals(5, rows.size)
        assertEquals(5, rows.map { it.id }.toSet().size)
    }

    // ---------- 원인 2: 잡지 않은 예외 ----------

    /** Retrofit 의 HttpException 처럼 IOException 이 아닌 런타임 예외. */
    private class HttpLikeException(code: Int) : RuntimeException("HTTP $code Server Error")

    private fun repository(api: FakeMolitApiService) = TradeRepositoryImpl(
        api = api,
        serviceKey = ServiceKeyProvider(FakeServiceKeyStore("TEST_KEY"), buildConfigKey = ""),
        tradeDao = FakeTradeDao(),
        rentDao = FakeRentDao(),
        syncStateDao = FakeSyncStateDao(),
        ioDispatcher = Dispatchers.Unconfined,
        clock = Clock.fixed(Instant.parse("2026-09-04T12:00:00Z"), ZoneOffset.UTC),
    )

    private val emptyXml = """
        <response><header><resultCode>03</resultCode></header>
        <body><items/><totalCount>0</totalCount></body></response>
    """.trimIndent()

    @Test
    fun `IOException 이 아닌 예외가 나도 앱이 죽지 않는다`() = runBlocking {
        // Retrofit 은 non-2xx 응답에 HttpException 을 던진다. 이것은 IOException 이 아니다.
        // 잡지 않으면 async 밖으로 나가 viewModelScope 의 미처리 예외가 되고 앱이 종료된다.
        val api = FakeMolitApiService(
            { _, _, _ -> throw HttpLikeException(500) },
            { _, _, _ -> emptyXml },
        )
        val plan = TradeQueryPlan.of(TradePeriod.TWO_WEEKS, today, listOf("11680"))

        val report = repository(api).sync(plan)

        // 실패는 보고서에 남되, 예외가 밖으로 새어 나가지 않는다.
        assertTrue("매매 구간 2개가 실패로 기록되어야 한다", report.failures.size >= 2)
        assertTrue(report.failures.all { it.message.contains("조회 실패") })
    }

    @Test
    fun `한 구간이 터져도 나머지 구간은 계속 받아온다`() = runBlocking {
        val api = FakeMolitApiService(
            { _, dealYmd, _ ->
                if (dealYmd == "202609") throw HttpLikeException(503) else emptyXml
            },
            { _, _, _ -> emptyXml },
        )
        val plan = TradeQueryPlan.of(TradePeriod.TWO_WEEKS, today, listOf("11680"))

        val report = repository(api).sync(plan)

        assertEquals("실패한 구간은 하나뿐", 1, report.failures.size)
        assertTrue("나머지는 정상 처리", report.fetched >= 3)
    }

    @Test
    fun `네트워크 오류는 여전히 네트워크 오류로 구분된다`() = runBlocking {
        val api = FakeMolitApiService(
            { _, _, _ -> throw IOException("연결 끊김") },
            { _, _, _ -> emptyXml },
        )
        val plan = TradeQueryPlan.of(TradePeriod.TWO_WEEKS, today, listOf("11680"))

        val report = repository(api).sync(plan)

        assertNotNull(report.failures.firstOrNull { it.message.contains("네트워크 오류") })
    }
}
