package com.aptprice.tracker.data.repository

import com.aptprice.tracker.data.mapper.toEntity
import com.aptprice.tracker.data.remote.api.ServiceKeyProvider
import com.aptprice.tracker.domain.model.AptRent
import com.aptprice.tracker.domain.model.AptTrade
import com.aptprice.tracker.domain.model.DealTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 카드 미니 그래프에 넣을 금액 흐름을 저장소가 어떻게 뽑는가.
 *
 * 목록과 다른 규칙이 둘 있다 — 기간으로 자르지 않고, 카드 수만큼 조회하지 않는다.
 */
class AmountSeriesTest {

    private val clock: Clock = Clock.fixed(Instant.parse("2026-09-04T12:00:00Z"), ZoneOffset.UTC)

    private val rentDao = FakeRentDao()
    private val tradeDao = FakeTradeDao(rentRows = { rentDao.rows })

    private val repository = TradeRepositoryImpl(
        api = FakeMolitApiService({ _, _, _ -> "" }, { _, _, _ -> "" }),
        serviceKey = ServiceKeyProvider(FakeServiceKeyStore("TEST_KEY"), buildConfigKey = ""),
        tradeDao = tradeDao,
        rentDao = rentDao,
        syncStateDao = FakeSyncStateDao(),
        ioDispatcher = Dispatchers.Unconfined,
        clock = clock,
    )

    private fun trade(
        date: LocalDate,
        amount: Long,
        apt: String = "샘플아파트",
        canceled: Boolean = false,
    ) = AptTrade(
        lawdCd = "11680", umdNm = "역삼동", aptName = apt, jibun = "101",
        exclusiveAreaM2 = 84.97, floor = 10, buildYear = 2005,
        dealDate = date, canceled = canceled, canceledDate = if (canceled) date else null,
        dealAmountManwon = amount, dealingGbn = null, registerDate = null,
    )

    private fun rent(date: LocalDate, deposit: Long, monthly: Long) = AptRent(
        lawdCd = "11680", umdNm = "역삼동", aptName = "샘플아파트", jibun = "101",
        exclusiveAreaM2 = 84.97, floor = 3, buildYear = 2005,
        dealDate = date, canceled = false, canceledDate = null,
        depositManwon = deposit, monthlyRentManwon = monthly,
        contractType = null, contractTerm = null,
        previousDepositManwon = null, previousMonthlyRentManwon = null,
    )

    private fun putTrades(vararg deals: AptTrade) = runBlocking {
        tradeDao.insertAll(deals.map { it.toEntity("202609") })
    }

    private fun putRents(vararg deals: AptRent) = runBlocking {
        rentDao.insertAll(deals.map { it.toEntity("202609") })
    }

    private fun key(apt: String = "샘플아파트") =
        trade(LocalDate.of(2026, 9, 1), 1, apt).complexAreaKey

    @Test
    fun `오래된 순으로 준다`() = runBlocking {
        putTrades(
            trade(LocalDate.of(2026, 9, 3), 90_000),
            trade(LocalDate.of(2026, 9, 1), 80_000),
            trade(LocalDate.of(2026, 9, 2), 85_000),
        )

        val series = repository.observeAmountSeries(listOf(key()), DealTab.SALE).first()

        // 순서가 뒤집히면 오른 것을 내렸다고 그린다.
        assertEquals(listOf(80_000L, 85_000L, 90_000L), series.getValue(key()))
    }

    @Test
    fun `해제된 매매는 넣지 않는다`() = runBlocking {
        putTrades(
            trade(LocalDate.of(2026, 9, 1), 80_000),
            trade(LocalDate.of(2026, 9, 2), 200_000, canceled = true),
            trade(LocalDate.of(2026, 9, 3), 85_000),
        )

        val series = repository.observeAmountSeries(listOf(key()), DealTab.SALE).first()

        // 성사되지 않은 값이라, 넣으면 있지도 않은 급등락이 그려진다.
        assertEquals(listOf(80_000L, 85_000L), series.getValue(key()))
    }

    @Test
    fun `목록 기간 밖의 거래도 넣는다`() = runBlocking {
        // 기본 목록은 2주치다. 그 창으로 자르면 대부분 한 점만 남아 그릴 것이 없다.
        putTrades(
            trade(LocalDate.of(2023, 4, 10), 60_000),
            trade(LocalDate.of(2026, 9, 3), 90_000),
        )

        val series = repository.observeAmountSeries(listOf(key()), DealTab.SALE).first()

        assertEquals(listOf(60_000L, 90_000L), series.getValue(key()))
    }

    @Test
    fun `전세 탭은 전세 보증금만 월세 탭은 월세 보증금만`() = runBlocking {
        putRents(
            rent(LocalDate.of(2026, 9, 1), deposit = 50_000, monthly = 0),
            rent(LocalDate.of(2026, 9, 2), deposit = 60_000, monthly = 0),
            rent(LocalDate.of(2026, 9, 3), deposit = 10_000, monthly = 120),
        )

        val jeonse = repository.observeAmountSeries(listOf(key()), DealTab.JEONSE).first()
        val monthly = repository.observeAmountSeries(listOf(key()), DealTab.MONTHLY).first()

        assertEquals(listOf(50_000L, 60_000L), jeonse.getValue(key()))
        // 월세 탭이 그리는 값은 보증금이다 (목록·요약이 쓰는 비교값과 같다).
        assertEquals(listOf(10_000L), monthly.getValue(key()))
    }

    @Test
    fun `카드가 여럿이어도 조회는 한 번이다`() = runBlocking {
        putTrades(
            trade(LocalDate.of(2026, 9, 1), 80_000, apt = "가단지"),
            trade(LocalDate.of(2026, 9, 2), 85_000, apt = "나단지"),
            trade(LocalDate.of(2026, 9, 3), 90_000, apt = "다단지"),
        )
        val keys = listOf(key("가단지"), key("나단지"), key("다단지"))

        val series = repository.observeAmountSeries(keys, DealTab.SALE).first()

        assertEquals(3, series.size)
        // 카드마다 조회하면 목록이 100장일 때 쿼리도 100번이다.
        assertEquals(1, tradeDao.sparkQueries.size)
        assertEquals(keys, tradeDao.sparkQueries.single())
    }

    @Test
    fun `같은 키를 여러 번 넘겨도 한 번만 조회한다`() = runBlocking {
        // 같은 단지·평형의 거래가 카드 여러 장으로 나오는 것은 흔한 일이다.
        putTrades(
            trade(LocalDate.of(2026, 9, 1), 80_000),
            trade(LocalDate.of(2026, 9, 3), 90_000),
        )

        repository.observeAmountSeries(listOf(key(), key(), key()), DealTab.SALE).first()

        assertEquals(listOf(key()), tradeDao.sparkQueries.single())
    }

    @Test
    fun `키가 아주 많아도 SQLite 변수 한도를 넘기지 않는다`() = runBlocking {
        // 5년치 서울 전역이면 카드가 수만 장이다. 그대로 IN 절에 넣으면
        // "too many SQL variables" 로 터진다 (안드로이드 SQLite 는 999개까지).
        val keys = (1..5_000).map { "11680|역삼동|단지$it|84.97" }

        repository.observeAmountSeries(keys, DealTab.SALE).first()

        val asked = tradeDao.sparkQueries.single()
        assertEquals(TradeRepositoryImpl.MAX_SERIES_KEYS, asked.size)
        assertTrue("한도는 SQLite 상한보다 낮아야 한다", asked.size < 999)
        // 앞에서부터, 즉 화면에 먼저 보이는 카드부터 그린다.
        assertEquals(keys.take(TradeRepositoryImpl.MAX_SERIES_KEYS), asked)
    }

    @Test
    fun `보여줄 카드가 없으면 조회하지 않는다`() = runBlocking {
        val series = repository.observeAmountSeries(emptyList(), DealTab.SALE).first()

        assertTrue(series.isEmpty())
        assertTrue(tradeDao.sparkQueries.isEmpty())
        assertTrue(rentDao.sparkQueries.isEmpty())
    }
}
