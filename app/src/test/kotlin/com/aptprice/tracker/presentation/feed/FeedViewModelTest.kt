package com.aptprice.tracker.presentation.feed

import com.aptprice.tracker.core.format.AreaBucket
import com.aptprice.tracker.core.time.TradePeriod
import com.aptprice.tracker.core.time.TradeRequestKey
import com.aptprice.tracker.data.remote.parser.MolitApiError
import com.aptprice.tracker.domain.model.AptRent
import com.aptprice.tracker.domain.model.AptTrade
import com.aptprice.tracker.domain.model.DealTab
import com.aptprice.tracker.domain.region.RegionGroup
import com.aptprice.tracker.domain.repository.SyncFailure
import com.aptprice.tracker.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock: Clock = Clock.fixed(Instant.parse("2026-09-04T12:00:00Z"), ZoneOffset.UTC)

    private fun trade(apt: String = "뷰모델테스트", day: Int = 3, amount: Long = 87_500, area: Double = 84.97) =
        AptTrade(
            lawdCd = "11680", umdNm = "역삼동", aptName = apt, jibun = "101",
            exclusiveAreaM2 = area, floor = 10, buildYear = 2005,
            dealDate = LocalDate.of(2026, 9, day), canceled = false, canceledDate = null,
            dealAmountManwon = amount, dealingGbn = null, registerDate = null,
        )

    private fun jeonse(apt: String = "전세건") = AptRent(
        lawdCd = "11680", umdNm = "역삼동", aptName = apt, jibun = null,
        exclusiveAreaM2 = 84.97, floor = 3, buildYear = 2005,
        dealDate = LocalDate.of(2026, 9, 3), canceled = false, canceledDate = null,
        depositManwon = 50_000, monthlyRentManwon = 0,
        contractType = null, contractTerm = null,
        previousDepositManwon = null, previousMonthlyRentManwon = null,
    )

    private fun viewModel(repository: FakeTradeRepository) = FeedViewModel(repository, clock)

    @Test
    fun `시작하면 기본 조건으로 조회한다`() = runTest {
        val repo = FakeTradeRepository()
        val vm = viewModel(repo)

        assertEquals(1, repo.syncedPlans.size)
        assertEquals(TradePeriod.TWO_WEEKS, repo.syncedPlans.single().period)
        assertEquals(36, repo.syncedPlans.single().regionCount)
        assertEquals(TradePeriod.TWO_WEEKS, vm.uiState.value.filter.period)
        assertEquals(DealTab.SALE, vm.uiState.value.filter.tab)
    }

    @Test
    fun `받아온 거래가 카드로 바뀐다`() = runTest {
        val repo = FakeTradeRepository()
        repo.fetchedAt = Instant.parse("2026-09-04T12:00:00Z")
        val vm = viewModel(repo)

        repo.emit(listOf(trade(day = 1, amount = 80_000), trade(day = 3, amount = 88_000)))

        val content = vm.uiState.value.content as FeedContent.Items
        assertEquals(2, content.items.size)
        // 최신순이 기본이고, 직전 거래와 비교한 등락률이 붙는다.
        assertEquals("+10.0%", content.items.first().changeLabel)
    }

    @Test
    fun `탭을 바꾸면 해당 유형만 보인다`() = runTest {
        val repo = FakeTradeRepository()
        repo.fetchedAt = Instant.now(clock)
        val vm = viewModel(repo)
        repo.emit(listOf(trade(), jeonse()))

        assertEquals(1, vm.uiState.value.itemCount)
        assertEquals("뷰모델테스트", (vm.uiState.value.content as FeedContent.Items).items.single().aptName)

        vm.selectTab(DealTab.JEONSE)
        assertEquals("전세건", (vm.uiState.value.content as FeedContent.Items).items.single().aptName)
        assertEquals(DealTab.JEONSE, repo.observedQueries.last().third)
    }

    @Test
    fun `정렬 변경은 다시 조회하지 않는다`() = runTest {
        val repo = FakeTradeRepository()
        repo.fetchedAt = Instant.now(clock)
        val vm = viewModel(repo)
        repo.emit(listOf(trade(apt = "가", amount = 50_000), trade(apt = "나", amount = 120_000)))
        val syncCountBefore = repo.syncedPlans.size

        vm.selectSort(FeedSort.PRICE_DESC)

        assertEquals("정렬은 네트워크 조회가 필요 없다", syncCountBefore, repo.syncedPlans.size)
        val items = (vm.uiState.value.content as FeedContent.Items).items
        assertEquals(listOf(120_000L, 50_000L), items.map { it.sortAmountManwon })
    }

    @Test
    fun `평형대 필터도 다시 조회하지 않는다`() = runTest {
        val repo = FakeTradeRepository()
        repo.fetchedAt = Instant.now(clock)
        val vm = viewModel(repo)
        repo.emit(listOf(trade(area = 39.72), trade(area = 84.97)))
        val syncCountBefore = repo.syncedPlans.size

        vm.toggleAreaBucket(AreaBucket.SMALL)

        assertEquals(syncCountBefore, repo.syncedPlans.size)
        assertEquals(1, vm.uiState.value.itemCount)
    }

    @Test
    fun `기간을 바꾸면 다시 조회한다`() = runTest {
        val repo = FakeTradeRepository()
        val vm = viewModel(repo)
        val before = repo.syncedPlans.size

        vm.selectPeriod(TradePeriod.THREE_MONTHS)

        assertEquals(before + 1, repo.syncedPlans.size)
        assertEquals(TradePeriod.THREE_MONTHS, repo.syncedPlans.last().period)
    }

    @Test
    fun `무거운 조회는 확인을 받기 전에는 시작하지 않는다`() = runTest {
        val repo = FakeTradeRepository()
        val vm = viewModel(repo)
        val before = repo.syncedPlans.size

        // 전체 36개 지역 × 5년 = 2,196회
        vm.selectPeriod(TradePeriod.FIVE_YEARS)

        assertEquals("확인 전에는 조회하지 않는다", before, repo.syncedPlans.size)
        val prompt = vm.uiState.value.heavyQueryPrompt
        assertNotNull(prompt)
        assertEquals(2196, prompt!!.requestCount)
    }

    @Test
    fun `확인하면 무거운 조회를 시작한다`() = runTest {
        val repo = FakeTradeRepository()
        val vm = viewModel(repo)
        vm.selectPeriod(TradePeriod.FIVE_YEARS)
        val before = repo.syncedPlans.size

        vm.confirmHeavyQuery()

        assertEquals(before + 1, repo.syncedPlans.size)
        assertEquals(TradePeriod.FIVE_YEARS, repo.syncedPlans.last().period)
        assertNull(vm.uiState.value.heavyQueryPrompt)
    }

    @Test
    fun `취소하면 조회하지 않고 대화상자만 닫힌다`() = runTest {
        val repo = FakeTradeRepository()
        val vm = viewModel(repo)
        vm.selectPeriod(TradePeriod.FIVE_YEARS)
        val before = repo.syncedPlans.size

        vm.dismissHeavyQuery()

        assertEquals(before, repo.syncedPlans.size)
        assertNull(vm.uiState.value.heavyQueryPrompt)
        // 기간 선택 자체는 유지된다.
        assertEquals(TradePeriod.FIVE_YEARS, vm.uiState.value.filter.period)
    }

    @Test
    fun `지역을 좁혀 가벼워지면 확인 없이 바로 조회한다`() = runTest {
        val repo = FakeTradeRepository()
        val vm = viewModel(repo)
        vm.selectPeriod(TradePeriod.FIVE_YEARS)
        assertNotNull("전체 지역 5년은 무겁다", vm.uiState.value.heavyQueryPrompt)
        val before = repo.syncedPlans.size

        // 성남 3곳만 남긴다. 3 × 61 = 183회로 기준선(200) 아래다.
        vm.toggleRegionGroup(RegionGroup.SEOUL)
        vm.toggleRegionGroup(RegionGroup.YONGIN)
        vm.toggleRegionGroup(RegionGroup.SUWON)
        vm.toggleRegionGroup(RegionGroup.DONGTAN)

        assertEquals(3, vm.uiState.value.filter.regions.codes().size)
        assertEquals(183, vm.currentRequestCount())
        assertNull("가벼워졌으면 확인 요청이 남아 있으면 안 된다", vm.uiState.value.heavyQueryPrompt)
        assertEquals("가벼워진 시점에 조회가 시작된다", before + 1, repo.syncedPlans.size)
        assertEquals(3, repo.syncedPlans.last().regionCount)
    }

    @Test
    fun `새로고침은 확인 없이 바로 조회한다`() = runTest {
        val repo = FakeTradeRepository()
        val vm = viewModel(repo)
        vm.selectPeriod(TradePeriod.FIVE_YEARS)
        vm.dismissHeavyQuery()
        val before = repo.syncedPlans.size

        vm.refresh()

        assertEquals(before + 1, repo.syncedPlans.size)
        assertFalse(vm.uiState.value.isRefreshing)
    }

    @Test
    fun `읽지 못한 행이 있으면 화면에 알린다`() = runTest {
        val repo = FakeTradeRepository()
        repo.report = repo.report.copy(
            parseFailures = 4,
            failures = listOf(SyncFailure(TradeRequestKey("11680", "202609"), "네트워크 오류")),
        )
        val vm = viewModel(repo)

        val state = vm.uiState.value
        assertNotNull(state.parseFailureNotice)
        assertTrue(state.parseFailureNotice!!.contains("4건"))
        assertNotNull(state.partialSyncNotice)
        assertTrue(state.partialSyncNotice!!.contains("1개 구간"))

        vm.dismissNotices()
        assertNull(vm.uiState.value.parseFailureNotice)
        assertNull(vm.uiState.value.partialSyncNotice)
    }

    @Test
    fun `조회했는데 거래가 없으면 없다고 표시한다`() = runTest {
        val repo = FakeTradeRepository()
        repo.fetchedAt = Instant.parse("2026-09-04T12:00:00Z")
        val vm = viewModel(repo)

        val content = vm.uiState.value.content
        assertTrue("빈 목록을 지어낸 값으로 채우지 않는다", content is FeedContent.Empty)
        assertTrue((content as FeedContent.Empty).message.contains("거래 데이터 없음"))
    }

    @Test
    fun `조회에 실패하면 없음이 아니라 오류로 표시한다`() = runTest {
        val repo = FakeTradeRepository()
        repo.report = repo.report.copy(
            abortedBy = MolitApiError(
                code = "30",
                message = "SERVICE_KEY_IS_NOT_REGISTERED_ERROR",
                kind = MolitApiError.Kind.INVALID_SERVICE_KEY,
            ),
        )
        val vm = viewModel(repo)

        val content = vm.uiState.value.content
        assertTrue("실패를 '거래 없음' 으로 뭉개지 않는다", content is FeedContent.Error)
        content as FeedContent.Error
        assertTrue(content.message.contains("인증키"))
        assertFalse("인증키 오류는 재시도해도 소용없다", content.retryable)
    }

    @Test
    fun `동기화가 끝나면 기준일시가 출처에 반영된다`() = runTest {
        val repo = FakeTradeRepository()
        repo.fetchedAt = Instant.parse("2026-09-04T02:20:00Z")
        val vm = viewModel(repo)

        assertEquals(
            "데이터 출처: 국토교통부 실거래가 공개시스템 (기준일시: 2026-09-04 02:20)",
            vm.uiState.value.attributionLabel(ZoneOffset.UTC),
        )
        assertFalse(vm.uiState.value.sync.inProgress)
    }
}
