package com.aptprice.tracker.data.repository

import com.aptprice.tracker.core.time.TradePeriod
import com.aptprice.tracker.core.time.TradeQueryPlan
import com.aptprice.tracker.data.local.entity.SyncEndpoint
import com.aptprice.tracker.data.local.entity.SyncStateEntity
import com.aptprice.tracker.data.remote.api.ServiceKeyProvider
import com.aptprice.tracker.data.remote.parser.MolitApiError
import com.aptprice.tracker.domain.model.DealTab
import com.aptprice.tracker.domain.repository.SyncProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * 동기화 오케스트레이션 테스트.
 *
 * 응답 XML 은 파서 픽스처와 같은 성격의 테스트 입력이며 실제 실거래 데이터가 아니다.
 */
class TradeRepositoryImplTest {

    private val now: Instant = Instant.parse("2026-09-04T12:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    private val rentDao = FakeRentDao()

    // 단지 검색은 매매·전월세를 합쳐 찾으므로 매매 DAO 가 전월세 표도 볼 수 있어야 한다.
    private val tradeDao = FakeTradeDao(rentRows = { rentDao.rows })
    private val syncStateDao = FakeSyncStateDao()

    private fun tradeXml(aptName: String, umdNm: String, sggCd: String, amount: String = "50,000") = """
        <response><header><resultCode>000</resultCode></header><body><items>
          <item>
            <aptNm>$aptName</aptNm><dealAmount>$amount</dealAmount>
            <dealYear>2026</dealYear><dealMonth>9</dealMonth><dealDay>1</dealDay>
            <excluUseAr>84.97</excluUseAr><floor>5</floor>
            <sggCd>$sggCd</sggCd><umdNm>$umdNm</umdNm>
          </item>
        </items><numOfRows>1000</numOfRows><pageNo>1</pageNo><totalCount>1</totalCount></body></response>
    """.trimIndent()

    private val emptyXml = """
        <response><header><resultCode>03</resultCode><resultMsg>NODATA_ERROR</resultMsg></header>
        <body><items/><numOfRows>10</numOfRows><pageNo>1</pageNo><totalCount>0</totalCount></body></response>
    """.trimIndent()

    private val invalidKeyXml = """
        <OpenAPI_ServiceResponse><cmmMsgHeader>
          <returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg>
          <returnReasonCode>30</returnReasonCode>
        </cmmMsgHeader></OpenAPI_ServiceResponse>
    """.trimIndent()

    private fun repository(
        api: FakeMolitApiService,
        key: String = "TEST_KEY",
    ) = TradeRepositoryImpl(
        api = api,
        serviceKey = ServiceKeyProvider(FakeServiceKeyStore(key), buildConfigKey = ""),
        tradeDao = tradeDao,
        rentDao = rentDao,
        syncStateDao = syncStateDao,
        ioDispatcher = Dispatchers.Unconfined,
        clock = clock,
    )

    private fun api(
        trade: (String, String, Int) -> String = { _, _, _ -> emptyXml },
        rent: (String, String, Int) -> String = { _, _, _ -> emptyXml },
    ) = FakeMolitApiService(trade, rent)

    @Test
    fun `인증키가 없으면 API 를 한 번도 부르지 않는다`() = runBlocking {
        val api = api()
        val plan = TradeQueryPlan.of(TradePeriod.TWO_WEEKS, clock.today(), listOf("11680"))

        val report = repository(api, key = "").sync(plan)

        assertEquals(0, api.totalCalls)
        assertEquals(0, report.fetched)
        assertNotNull(report.abortedBy)
        assertEquals(MolitApiError.Kind.INVALID_SERVICE_KEY, report.abortedBy!!.kind)
        // 조회하지 못했다고 해서 가짜 행을 넣지 않는다.
        assertTrue(tradeDao.rows.isEmpty())
    }

    @Test
    fun `구간마다 매매와 전월세를 각각 부른다`() = runBlocking {
        val api = api(trade = { _, _, _ -> tradeXml("동기화테스트", "역삼동", "11680") })
        // 2주 = 2개월(202608, 202609) × 1지역 × 2엔드포인트 = 4회
        val plan = TradeQueryPlan.of(TradePeriod.TWO_WEEKS, clock.today(), listOf("11680"))

        val report = repository(api).sync(plan)

        assertEquals(2, api.tradeCalls.size)
        assertEquals(2, api.rentCalls.size)
        assertEquals(4, report.planned)
        assertEquals(4, report.fetched)
        assertEquals(0, report.skippedFresh)
        assertEquals(2, report.storedRows)
        assertTrue(report.isComplete)
    }

    @Test
    fun `캐시가 유효한 구간은 다시 부르지 않는다`() = runBlocking {
        // 202608 은 방금 받아온 것으로 기록해 둔다.
        listOf(SyncEndpoint.TRADE, SyncEndpoint.RENT).forEach { endpoint ->
            syncStateDao.upsert(
                SyncStateEntity(
                    lawdCd = "11680",
                    dealYmd = "202608",
                    endpoint = endpoint.name,
                    fetchedAtEpochMillis = now.toEpochMilli(),
                    rowCount = 0,
                    totalCount = 0,
                    failureCount = 0,
                ),
            )
        }
        val api = api()
        val plan = TradeQueryPlan.of(TradePeriod.TWO_WEEKS, clock.today(), listOf("11680"))

        val report = repository(api).sync(plan)

        assertEquals(2, report.skippedFresh)
        assertEquals(2, report.fetched)
        // 202609 만 다시 받는다.
        assertTrue(api.tradeCalls.all { it.second == "202609" })
        assertTrue(api.rentCalls.all { it.second == "202609" })
    }

    @Test
    fun `인증키 오류를 만나면 남은 구간을 포기한다`() = runBlocking {
        val api = api(trade = { _, _, _ -> invalidKeyXml }, rent = { _, _, _ -> invalidKeyXml })
        // 5년 × 1지역 = 61개월 × 2엔드포인트 = 122 구간
        val plan = TradeQueryPlan.of(TradePeriod.FIVE_YEARS, clock.today(), listOf("11680"))
        assertEquals(122, plan.requestCount * 2)

        val report = repository(api).sync(plan)

        assertNotNull("중단 사유가 남아야 한다", report.abortedBy)
        assertEquals(MolitApiError.Kind.INVALID_SERVICE_KEY, report.abortedBy!!.kind)
        // 122 구간을 전부 때리지 않고 일찍 멈춘다.
        assertTrue(
            "호출이 ${api.totalCalls}회로, 전체(122)를 다 때렸다",
            api.totalCalls < 122,
        )
        assertFalse(report.isComplete)
    }

    @Test
    fun `동탄이 아닌 화성시 행은 저장하지 않는다`() = runBlocking {
        val api = api(
            trade = { _, dealYmd, _ ->
                if (dealYmd == "202609") {
                    tradeXml("봉담아파트", "봉담읍", "41590")
                } else {
                    tradeXml("반송아파트", "반송동", "41590")
                }
            },
        )
        val plan = TradeQueryPlan.of(TradePeriod.TWO_WEEKS, clock.today(), listOf("41590"))

        repository(api).sync(plan)

        assertEquals(1, tradeDao.rows.size)
        assertEquals("반송동", tradeDao.rows[0].umdNm)
        assertTrue(tradeDao.rows.none { it.umdNm == "봉담읍" })
    }

    @Test
    fun `읽지 못한 행 수가 보고서에 남는다`() = runBlocking {
        val brokenXml = """
            <response><header><resultCode>000</resultCode></header><body><items>
              <item><aptNm>금액없음</aptNm><dealAmount></dealAmount>
                <dealYear>2026</dealYear><dealMonth>9</dealMonth><dealDay>1</dealDay>
                <excluUseAr>84.97</excluUseAr><sggCd>11680</sggCd><umdNm>역삼동</umdNm></item>
            </items><numOfRows>1000</numOfRows><pageNo>1</pageNo><totalCount>1</totalCount></body></response>
        """.trimIndent()
        val api = api(trade = { _, _, _ -> brokenXml })
        val plan = TradeQueryPlan.of(TradePeriod.TWO_WEEKS, clock.today(), listOf("11680"))

        val report = repository(api).sync(plan)

        assertEquals("두 달치 각각 1건씩 실패", 2, report.parseFailures)
        assertEquals(0, report.storedRows)
        assertTrue(tradeDao.rows.isEmpty())
        assertEquals(2, syncStateDao.withParseFailures().size)
    }

    @Test
    fun `아직 오지 않은 달은 조회하지 않는다`() = runBlocking {
        val api = api()
        // 계약월 목록에 미래 달을 억지로 섞는다.
        val base = TradeQueryPlan.of(TradePeriod.TWO_WEEKS, clock.today(), listOf("11680"))
        val plan = base.copy(dealYmdCodes = base.dealYmdCodes + listOf("202610", "202611"))

        repository(api).sync(plan)

        assertTrue(api.tradeCalls.none { it.second == "202610" || it.second == "202611" })
        assertEquals(2, api.tradeCalls.size)
    }

    @Test
    fun `다음 페이지가 있으면 이어서 받는다`() = runBlocking {
        val pagedXml = { pageNo: Int ->
            """
            <response><header><resultCode>000</resultCode></header><body><items>
              <item><aptNm>페이지$pageNo</aptNm><dealAmount>10,000</dealAmount>
                <dealYear>2026</dealYear><dealMonth>9</dealMonth><dealDay>1</dealDay>
                <excluUseAr>84.97</excluUseAr><sggCd>11680</sggCd><umdNm>역삼동</umdNm></item>
            </items><numOfRows>1</numOfRows><pageNo>$pageNo</pageNo><totalCount>3</totalCount></body></response>
            """.trimIndent()
        }
        val api = api(trade = { _, _, pageNo -> pagedXml(pageNo) })
        val plan = TradeQueryPlan.of(TradePeriod.TWO_WEEKS, clock.today(), listOf("11680"))

        repository(api).sync(plan)

        // 계약월당 3페이지 × 2개월 = 6회
        assertEquals(6, api.tradeCalls.size)
        assertEquals(listOf(1, 2, 3), api.tradeCalls.filter { it.second == "202609" }.map { it.third })
        assertEquals(6, tradeDao.rows.size)
    }

    @Test
    fun `진행률은 뒤로 가지 않고 끝에서 전체와 같아진다`() = runBlocking {
        val api = api(trade = { _, _, _ -> tradeXml("진행률테스트", "역삼동", "11680") })
        val plan = TradeQueryPlan.of(TradePeriod.THREE_MONTHS, clock.today(), listOf("11680", "41135"))

        val seen = mutableListOf<SyncProgress>()
        val report = repository(api).sync(plan) { synchronized(seen) { seen += it } }

        assertTrue(seen.isNotEmpty())
        val completedValues = seen.map { it.completed }
        assertEquals("진행률이 뒤로 갔다: $completedValues", completedValues.sorted(), completedValues)
        assertEquals(report.planned, seen.last().total)
        assertEquals(report.planned, seen.last().completed)
        assertEquals(1f, seen.last().fraction, 1e-6f)
    }

    @Test
    fun `같은 달을 다시 받으면 이전 행을 지우고 새로 넣는다`() = runBlocking {
        var amount = "50,000"
        val api = api(trade = { _, _, _ -> tradeXml("갱신테스트", "역삼동", "11680", amount) })
        val plan = TradeQueryPlan.of(TradePeriod.TWO_WEEKS, clock.today(), listOf("11680"))
        val repository = repository(api)

        repository.sync(plan)
        assertEquals(2, tradeDao.rows.size)
        assertTrue(tradeDao.rows.all { it.dealAmountManwon == 50_000L })

        // 캐시 기록을 지워 다시 받게 하고, 금액이 바뀐 응답을 준다.
        syncStateDao.clear()
        amount = "62,000"
        repository.sync(plan)

        // 행이 쌓이지 않고 갈아끼워진다.
        assertEquals(2, tradeDao.rows.size)
        assertTrue(tradeDao.rows.all { it.dealAmountManwon == 62_000L })
    }

    @Test
    fun `단지 검색은 매매와 전월세를 합쳐 찾는다`() = runBlocking {
        val rentXml = """
            <response><header><resultCode>000</resultCode></header><body><items>
              <item><aptNm>힐스테이트역삼</aptNm><deposit>50,000</deposit><monthlyRent>0</monthlyRent>
                <dealYear>2026</dealYear><dealMonth>9</dealMonth><dealDay>2</dealDay>
                <excluUseAr>59.99</excluUseAr><sggCd>11680</sggCd><umdNm>역삼동</umdNm></item>
            </items><numOfRows>1000</numOfRows><pageNo>1</pageNo><totalCount>1</totalCount></body></response>
        """.trimIndent()
        val api = api(
            trade = { _, dealYmd, _ ->
                if (dealYmd == "202609") tradeXml("래미안역삼", "역삼동", "11680") else emptyXml
            },
            rent = { _, dealYmd, _ -> if (dealYmd == "202609") rentXml else emptyXml },
        )
        val repository = repository(api)
        repository.sync(TradeQueryPlan.of(TradePeriod.TWO_WEEKS, clock.today(), listOf("11680")))

        val fromTrade = repository.searchComplexes("래미안").first()
        // 매매가 한 건도 없고 전세만 있는 단지도 검색에 나와야 한다.
        val fromRent = repository.searchComplexes("힐스").first()

        assertEquals(1, fromTrade.size)
        assertEquals("래미안역삼", fromTrade.single().aptName)
        assertEquals(1, fromRent.size)
        assertEquals("힐스테이트역삼", fromRent.single().aptName)
    }

    @Test
    fun `검색 결과에 지역과 최근 거래 평형이 함께 온다`() = runBlocking {
        val api = api(
            trade = { _, dealYmd, _ ->
                if (dealYmd == "202609") tradeXml("래미안역삼", "역삼동", "11680") else emptyXml
            },
        )
        val repository = repository(api)
        repository.sync(TradeQueryPlan.of(TradePeriod.TWO_WEEKS, clock.today(), listOf("11680")))

        val found = repository.searchComplexes("래미안").first().single()

        assertEquals("강남구 역삼동", found.regionLabel)
        // 상세 화면은 (단지 + 평형) 단위라 평형이 정해져 있어야 열린다.
        assertEquals(84.97, found.latestAreaM2, 0.001)
        assertEquals(1, found.dealCount)
        assertEquals(84.97, found.openKey().areaM2!!, 0.001)
    }

    @Test
    fun `한 글자로는 검색하지 않는다`() = runBlocking {
        val api = api(
            trade = { _, dealYmd, _ ->
                if (dealYmd == "202609") tradeXml("래미안역삼", "역삼동", "11680") else emptyXml
            },
        )
        val repository = repository(api)
        repository.sync(TradeQueryPlan.of(TradePeriod.TWO_WEEKS, clock.today(), listOf("11680")))

        assertTrue(repository.searchComplexes("래").first().isEmpty())
        assertTrue(repository.searchComplexes(" ").first().isEmpty())
        assertTrue(repository.searchComplexes("").first().isEmpty())
    }

    @Test
    fun `대상 지역 밖의 단지는 검색에도 나오지 않는다`() = runBlocking {
        // 화성시(41590) 중 동탄 관할이 아닌 동. 목록에서 걸러지듯 검색에서도 걸러져야 한다.
        val api = api(
            trade = { _, dealYmd, _ ->
                if (dealYmd == "202609") tradeXml("래미안봉담", "봉담읍", "41590") else emptyXml
            },
        )
        val repository = repository(api)
        repository.sync(TradeQueryPlan.of(TradePeriod.TWO_WEEKS, clock.today(), listOf("41590")))

        assertTrue(repository.searchComplexes("래미안").first().isEmpty())
    }

    @Test
    fun `캐시에서 탭별로 읽어 온다`() = runBlocking {
        val rentXml = """
            <response><header><resultCode>000</resultCode></header><body><items>
              <item><aptNm>전세건</aptNm><deposit>50,000</deposit><monthlyRent>0</monthlyRent>
                <dealYear>2026</dealYear><dealMonth>9</dealMonth><dealDay>1</dealDay>
                <excluUseAr>84.97</excluUseAr><sggCd>11680</sggCd><umdNm>역삼동</umdNm></item>
              <item><aptNm>월세건</aptNm><deposit>10,000</deposit><monthlyRent>120</monthlyRent>
                <dealYear>2026</dealYear><dealMonth>9</dealMonth><dealDay>2</dealDay>
                <excluUseAr>59.99</excluUseAr><sggCd>11680</sggCd><umdNm>역삼동</umdNm></item>
            </items><numOfRows>1000</numOfRows><pageNo>1</pageNo><totalCount>2</totalCount></body></response>
        """.trimIndent()
        val api = api(
            trade = { _, dealYmd, _ ->
                if (dealYmd == "202609") tradeXml("매매건", "역삼동", "11680") else emptyXml
            },
            rent = { _, dealYmd, _ -> if (dealYmd == "202609") rentXml else emptyXml },
        )
        val plan = TradeQueryPlan.of(TradePeriod.TWO_WEEKS, clock.today(), listOf("11680"))
        val repository = repository(api)
        repository.sync(plan)

        val sales = repository.observeDeals(TradePeriod.TWO_WEEKS, listOf("11680"), DealTab.SALE).first()
        val jeonse = repository.observeDeals(TradePeriod.TWO_WEEKS, listOf("11680"), DealTab.JEONSE).first()
        val monthly = repository.observeDeals(TradePeriod.TWO_WEEKS, listOf("11680"), DealTab.MONTHLY).first()

        assertEquals(1, sales.size)
        assertEquals("매매건", sales[0].aptName)
        assertEquals(1, jeonse.size)
        assertEquals("전세건", jeonse[0].aptName)
        assertEquals(1, monthly.size)
        assertEquals("월세건", monthly[0].aptName)
    }

    @Test
    fun `아무것도 받지 않았으면 기준일시가 없다`() = runBlocking {
        val api = api()
        val repository = repository(api, key = "")
        assertNull(repository.lastFetchedAt())
    }

    @Test
    fun `받아온 뒤에는 기준일시가 남는다`() = runBlocking {
        val api = api()
        val plan = TradeQueryPlan.of(TradePeriod.TWO_WEEKS, clock.today(), listOf("11680"))
        val repository = repository(api)
        repository.sync(plan)

        assertEquals(now.toEpochMilli(), repository.lastFetchedAt()?.toEpochMilli())
    }

    private fun Clock.today() = java.time.LocalDate.now(this)
}
