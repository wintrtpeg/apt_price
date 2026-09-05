package com.aptprice.tracker.data.repository

import com.aptprice.tracker.core.time.TradePeriod
import com.aptprice.tracker.core.time.TradeQueryPlan
import com.aptprice.tracker.data.remote.api.ServiceKeyProvider
import com.aptprice.tracker.data.remote.throttle.MolitHttpException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 매매 자료는 "상세"와 "기본" 두 서비스로 나뉘어 있고, 활용신청한 쪽에만 접근할 수 있다.
 * 실기기에서 전월세만 조회되고 매매는 전부 실패하던 문제에 대한 회귀 테스트다.
 */
class TradeEndpointFallbackTest {

    private val today = LocalDate.of(2026, 9, 4)

    private val tradeXml = """
        <response><header><resultCode>000</resultCode></header><body><items><item>
        <aptNm>폴백테스트</aptNm><dealAmount>87,500</dealAmount>
        <dealYear>2026</dealYear><dealMonth>9</dealMonth><dealDay>1</dealDay>
        <excluUseAr>84.97</excluUseAr><sggCd>11680</sggCd><umdNm>역삼동</umdNm>
        </item></items><totalCount>1</totalCount></body></response>
    """.trimIndent()

    private val emptyXml = """
        <response><header><resultCode>03</resultCode></header>
        <body><items/><totalCount>0</totalCount></body></response>
    """.trimIndent()

    private class AccessDenied : RuntimeException("HTTP 404 Not Found")

    private fun repository(api: FakeMolitApiService, tradeDao: FakeTradeDao) = TradeRepositoryImpl(
        api = api,
        serviceKey = ServiceKeyProvider(FakeServiceKeyStore("KEY"), buildConfigKey = ""),
        tradeDao = tradeDao,
        rentDao = FakeRentDao(),
        syncStateDao = FakeSyncStateDao(),
        ioDispatcher = Dispatchers.Unconfined,
        clock = Clock.fixed(Instant.parse("2026-09-04T12:00:00Z"), ZoneOffset.UTC),
    )

    @Test
    fun `상세 자료가 열려 있으면 그것만 쓴다`() = runBlocking {
        val api = FakeMolitApiService({ _, _, _ -> tradeXml }, { _, _, _ -> emptyXml })
        val dao = FakeTradeDao()
        val plan = TradeQueryPlan.of(TradePeriod.TWO_WEEKS, today, listOf("11680"))

        val report = repository(api, dao).sync(plan)

        assertTrue(report.failures.isEmpty())
        assertEquals("상세만 부른다", 2, api.detailCalls.size)
        assertEquals("기본은 부르지 않는다", 0, api.basicCalls.size)
        assertEquals(2, dao.rows.size)
    }

    @Test
    fun `상세 자료에 접근할 수 없으면 기본 자료로 넘어간다`() = runBlocking {
        val api = FakeMolitApiService({ _, _, _ -> tradeXml }, { _, _, _ -> emptyXml })
        api.detailFailure = { throw AccessDenied() }
        val dao = FakeTradeDao()
        val plan = TradeQueryPlan.of(TradePeriod.TWO_WEEKS, today, listOf("11680"))

        val report = repository(api, dao).sync(plan)

        assertTrue("기본 자료로 성공해야 한다: ${report.failures.map { it.message }}", report.failures.isEmpty())
        assertTrue("기본 자료를 불렀어야 한다", api.basicCalls.isNotEmpty())
        assertEquals("매매 거래가 저장되어야 한다", 2, dao.rows.size)
        assertEquals("폴백테스트", dao.rows.first().aptName)
    }

    @Test
    fun `한 번 확인된 서비스는 그다음부터 바로 쓴다`() = runBlocking {
        val api = FakeMolitApiService({ _, _, _ -> tradeXml }, { _, _, _ -> emptyXml })
        api.detailFailure = { throw AccessDenied() }
        val dao = FakeTradeDao()
        // 3개월 = 4개월치 구간
        val plan = TradeQueryPlan.of(TradePeriod.THREE_MONTHS, today, listOf("11680"))

        repository(api, dao).sync(plan)

        // 상세는 처음 몇 번만 시도되고, 기본으로 고정된 뒤에는 더 부르지 않는다.
        assertTrue(
            "상세를 계속 부르고 있다 (${api.detailCalls.size}회 / 전체 ${plan.monthCount}구간)",
            api.detailCalls.size < api.basicCalls.size,
        )
        assertEquals(plan.monthCount, dao.rows.size)
    }

    @Test
    fun `둘 다 실패하면 사유가 남는다`() = runBlocking {
        val api = FakeMolitApiService({ _, _, _ -> throw AccessDenied() }, { _, _, _ -> emptyXml })
        val dao = FakeTradeDao()
        val plan = TradeQueryPlan.of(TradePeriod.TWO_WEEKS, today, listOf("11680"))

        val report = repository(api, dao).sync(plan)

        assertEquals(2, report.failures.size)
        assertTrue(
            "어느 API 인지 알 수 있어야 한다: ${report.failures.first().message}",
            report.failures.all { it.message.startsWith("[매매]") },
        )
        assertTrue(report.failures.first().message.contains("AccessDenied"))
    }

    @Test
    fun `429 는 다른 엔드포인트로 넘어가지 않는다`() = runBlocking {
        val api = FakeMolitApiService({ _, _, _ -> tradeXml }, { _, _, _ -> emptyXml })
        api.detailFailure = { throw MolitHttpException(MolitHttpException.TOO_MANY_REQUESTS) }
        val dao = FakeTradeDao()
        val plan = TradeQueryPlan.of(TradePeriod.TWO_WEEKS, today, listOf("11680"))

        val report = repository(api, dao).sync(plan)

        // 이미 요청이 몰려 막힌 상태다. 여기서 다른 서비스까지 부르면 요청량이 두 배가 되어
        // 상황이 더 나빠진다. 폴백은 "그 서비스가 없다(404)" 일 때만 한다.
        assertEquals("막힌 상태에서 다른 서비스를 부르면 안 된다", 0, api.basicCalls.size)
        assertEquals(2, report.failures.size)
        assertTrue(
            "무엇 때문에 실패했는지 화면에 남아야 한다: ${report.failures.first().message}",
            report.failures.all { it.message.contains("429") },
        )
    }

    @Test
    fun `404 는 다른 엔드포인트로 넘어간다`() = runBlocking {
        val api = FakeMolitApiService({ _, _, _ -> tradeXml }, { _, _, _ -> emptyXml })
        api.detailFailure = { throw MolitHttpException(MolitHttpException.NOT_FOUND) }
        val dao = FakeTradeDao()
        val plan = TradeQueryPlan.of(TradePeriod.TWO_WEEKS, today, listOf("11680"))

        val report = repository(api, dao).sync(plan)

        assertTrue("기본 자료로 성공해야 한다: ${report.failures.map { it.message }}", report.failures.isEmpty())
        assertTrue(api.basicCalls.isNotEmpty())
        assertEquals(2, dao.rows.size)
    }

    /**
     * 활용신청하지 않은 API 를 부르면 공공데이터포털이 돌려주는 본문.
     * 상태 코드는 403 이고, 진짜 사유는 이 본문에 들어 있다.
     */
    private val accessDeniedXml = """
        <OpenAPI_ServiceResponse><cmmMsgHeader>
          <errMsg>SERVICE ERROR</errMsg>
          <returnAuthMsg>SERVICE_ACCESS_DENIED_ERROR</returnAuthMsg>
          <returnReasonCode>20</returnReasonCode>
        </cmmMsgHeader></OpenAPI_ServiceResponse>
    """.trimIndent()

    private fun accessDenied(): Nothing =
        throw MolitHttpException(MolitHttpException.FORBIDDEN, body = accessDeniedXml)

    @Test
    fun `상세 자료가 403 이면 기본 자료로 넘어간다`() = runBlocking {
        // 실기기 증상: 전월세는 나오는데 매매만 HTTP 403. 매매 자료는 상세·기본 두 서비스로
        // 나뉘어 있어, 활용신청한 쪽이 기본이면 상세는 403 이 난다. 갈아타야 한다.
        val api = FakeMolitApiService({ _, _, _ -> tradeXml }, { _, _, _ -> emptyXml })
        api.detailFailure = { accessDenied() }
        val dao = FakeTradeDao()
        val plan = TradeQueryPlan.of(TradePeriod.TWO_WEEKS, today, listOf("11680"))

        val report = repository(api, dao).sync(plan)

        assertTrue("기본 자료로 성공해야 한다: ${report.failures.map { it.message }}", report.failures.isEmpty())
        assertTrue("기본 자료를 불렀어야 한다", api.basicCalls.isNotEmpty())
        assertEquals("매매 거래가 저장되어야 한다", 2, dao.rows.size)
    }

    @Test
    fun `둘 다 403 이면 할 일을 알려 준다`() = runBlocking {
        val api = FakeMolitApiService({ _, _, _ -> accessDenied() }, { _, _, _ -> emptyXml })
        val dao = FakeTradeDao()
        val plan = TradeQueryPlan.of(TradePeriod.TWO_WEEKS, today, listOf("11680"))

        val report = repository(api, dao).sync(plan)

        val message = report.failures.first { it.message.startsWith("[매매]") }.message
        // "HTTP 403" 만 띄우면 사용자가 할 수 있는 일이 없다. 무엇을 해야 하는지 적는다.
        assertTrue("무엇을 해야 하는지 없다: $message", message.contains("활용신청"))
        assertTrue("어디서 해야 하는지 없다: $message", message.contains("data.go.kr"))
    }

    @Test
    fun `매매가 막혀도 전월세는 계속 받아온다`() = runBlocking {
        val rentXml = """
            <response><header><resultCode>000</resultCode></header><body><items><item>
            <aptNm>전세건</aptNm><deposit>50,000</deposit><monthlyRent>0</monthlyRent>
            <dealYear>2026</dealYear><dealMonth>9</dealMonth><dealDay>1</dealDay>
            <excluUseAr>84.97</excluUseAr><sggCd>11680</sggCd><umdNm>역삼동</umdNm>
            </item></items><totalCount>1</totalCount></body></response>
        """.trimIndent()
        val api = FakeMolitApiService({ _, _, _ -> accessDenied() }, { _, _, _ -> rentXml })
        val rentDao = FakeRentDao()
        val repository = TradeRepositoryImpl(
            api = api,
            serviceKey = ServiceKeyProvider(FakeServiceKeyStore("KEY"), buildConfigKey = ""),
            tradeDao = FakeTradeDao(),
            rentDao = rentDao,
            syncStateDao = FakeSyncStateDao(),
            ioDispatcher = Dispatchers.Unconfined,
            clock = Clock.fixed(Instant.parse("2026-09-04T12:00:00Z"), ZoneOffset.UTC),
        )
        val plan = TradeQueryPlan.of(TradePeriod.THREE_MONTHS, today, listOf("11680"))

        val report = repository.sync(plan)

        // 매매 권한이 없다고 전월세까지 멈추면, 볼 수 있었던 자료마저 못 보게 된다.
        assertEquals("전월세는 모든 구간이 들어와야 한다", plan.monthCount, rentDao.rows.size)
        assertTrue(report.failures.all { it.message.startsWith("[매매]") })
    }

    @Test
    fun `권한이 없다고 확인되면 같은 API 를 계속 부르지 않는다`() = runBlocking {
        val api = FakeMolitApiService({ _, _, _ -> accessDenied() }, { _, _, _ -> emptyXml })
        val dao = FakeTradeDao()
        // 4개월 구간. 막지 않으면 4구간 × (상세+기본) = 8회를 부른다.
        val plan = TradeQueryPlan.of(TradePeriod.THREE_MONTHS, today, listOf("11680"))

        repository(api, dao).sync(plan)

        val withoutGuard = plan.monthCount * 2
        assertTrue(
            "권한 없음이 확인된 뒤에도 계속 부르고 있다 (${api.tradeCalls.size}회 / 무방비면 ${withoutGuard}회)",
            api.tradeCalls.size < withoutGuard,
        )
    }

    @Test
    fun `전월세는 매매와 무관하게 동작한다`() = runBlocking {
        val rentXml = """
            <response><header><resultCode>000</resultCode></header><body><items><item>
            <aptNm>전세건</aptNm><deposit>50,000</deposit><monthlyRent>0</monthlyRent>
            <dealYear>2026</dealYear><dealMonth>9</dealMonth><dealDay>1</dealDay>
            <excluUseAr>84.97</excluUseAr><sggCd>11680</sggCd><umdNm>역삼동</umdNm>
            </item></items><totalCount>1</totalCount></body></response>
        """.trimIndent()
        val api = FakeMolitApiService({ _, _, _ -> throw AccessDenied() }, { _, _, _ -> rentXml })
        val rentDao = FakeRentDao()
        val repository = TradeRepositoryImpl(
            api = api,
            serviceKey = ServiceKeyProvider(FakeServiceKeyStore("KEY"), buildConfigKey = ""),
            tradeDao = FakeTradeDao(),
            rentDao = rentDao,
            syncStateDao = FakeSyncStateDao(),
            ioDispatcher = Dispatchers.Unconfined,
            clock = Clock.fixed(Instant.parse("2026-09-04T12:00:00Z"), ZoneOffset.UTC),
        )
        val plan = TradeQueryPlan.of(TradePeriod.TWO_WEEKS, today, listOf("11680"))

        val report = repository.sync(plan)

        // 매매만 실패하고 전월세는 정상 — 실기기에서 관찰된 상황 그대로다.
        assertEquals("매매 구간만 실패", 2, report.failures.size)
        assertEquals("전세는 저장된다", 2, rentDao.rows.size)
    }
}
