package com.aptprice.tracker.presentation.detail

import androidx.lifecycle.SavedStateHandle
import com.aptprice.tracker.core.time.TradePeriod
import com.aptprice.tracker.domain.model.AptRent
import com.aptprice.tracker.domain.model.AptTrade
import com.aptprice.tracker.domain.model.ComplexAreaKey
import com.aptprice.tracker.presentation.feed.FakeTradeRepository
import com.aptprice.tracker.presentation.navigation.Routes
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
class DetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock: Clock = Clock.fixed(Instant.parse("2026-09-04T12:00:00Z"), ZoneOffset.UTC)
    private val key = ComplexAreaKey.of("11680", "역삼동", "상세뷰모델", 84.97)

    private fun trade(daysAgo: Long, amount: Long, area: Double = 84.97, canceled: Boolean = false) =
        AptTrade(
            lawdCd = "11680", umdNm = "역삼동", aptName = "상세뷰모델", jibun = null,
            exclusiveAreaM2 = area, floor = 10, buildYear = 2005,
            dealDate = LocalDate.of(2026, 9, 4).minusDays(daysAgo),
            canceled = canceled, canceledDate = null,
            dealAmountManwon = amount, dealingGbn = null, registerDate = null,
        )

    private fun jeonse(daysAgo: Long, deposit: Long, area: Double = 84.97) = AptRent(
        lawdCd = "11680", umdNm = "역삼동", aptName = "상세뷰모델", jibun = null,
        exclusiveAreaM2 = area, floor = 3, buildYear = 2005,
        dealDate = LocalDate.of(2026, 9, 4).minusDays(daysAgo),
        canceled = false, canceledDate = null,
        depositManwon = deposit, monthlyRentManwon = 0,
        contractType = null, contractTerm = null,
        previousDepositManwon = null, previousMonthlyRentManwon = null,
    )

    private fun viewModel(
        repo: FakeTradeRepository,
        encodedKey: String? = key.encode(),
    ) = DetailViewModel(
        repository = repo,
        clock = clock,
        savedStateHandle = SavedStateHandle(mapOf(Routes.ARG_COMPLEX_AREA_KEY to encodedKey)),
    )

    @Test
    fun `경로 인자에서 단지와 평형을 읽는다`() = runTest {
        val vm = viewModel(FakeTradeRepository())

        val state = vm.uiState.value
        assertEquals("상세뷰모델", state.aptName)
        assertEquals("강남구 역삼동", state.regionLabel)
        assertEquals("84.97㎡ (25.7평)", state.areaLabel)
        assertEquals(TradePeriod.ONE_YEAR, state.period)
    }

    @Test
    fun `키를 읽지 못하면 그렇다고 말하고 조회하지 않는다`() = runTest {
        val repo = FakeTradeRepository()
        val vm = viewModel(repo, encodedKey = "이건 base64 가 아니다")

        assertTrue(repo.syncedPlans.isEmpty())
        assertFalse(vm.uiState.value.isLoading)
        assertNotNull(vm.uiState.value.emptyMessage)
        assertNull(vm.uiState.value.chart)
    }

    @Test
    fun `상세는 지역 하나만 조회하므로 5년이어도 가볍다`() = runTest {
        val repo = FakeTradeRepository()
        val vm = viewModel(repo)
        vm.selectPeriod(TradePeriod.FIVE_YEARS)

        val plan = repo.syncedPlans.last()
        assertEquals(1, plan.regionCount)
        assertEquals("11680", plan.lawdCodes.single())
        assertEquals(61, plan.requestCount)
        assertFalse("단일 지역 5년은 확인이 필요 없다", plan.isHeavy)
    }

    @Test
    fun `매매와 전세가 같은 차트에 그려진다`() = runTest {
        val repo = FakeTradeRepository()
        val vm = viewModel(repo)
        repo.emit(listOf(trade(30, 87_500), trade(10, 92_000), jeonse(20, 50_000)))

        val chart = vm.uiState.value.chart!!
        assertEquals(2, chart.series.size)
        assertEquals(3, chart.pointCount)
        assertNotNull(chart.peak)
        assertEquals(92_000L, chart.peak!!.amountManwon)
    }

    @Test
    fun `거래 이력이 최신순으로 채워진다`() = runTest {
        val repo = FakeTradeRepository()
        val vm = viewModel(repo)
        repo.emit(listOf(trade(30, 87_500), trade(10, 92_000)))

        val history = vm.uiState.value.history
        assertEquals(2, history.size)
        assertEquals("2026-08-05", history.last().dateLabel)
        assertEquals(1, history.count { it.isPeak })
    }

    @Test
    fun `평형 칩이 단지 안의 면적으로 채워진다`() = runTest {
        val repo = FakeTradeRepository()
        val vm = viewModel(repo)
        repo.emit(listOf(trade(10, 87_500, area = 84.97), trade(20, 60_000, area = 59.99)))

        val chips = vm.uiState.value.areaChips
        assertEquals(listOf("59.99㎡ (18.1평)", "84.97㎡ (25.7평)"), chips.map { it.label })
        assertEquals(listOf(false, true), chips.map { it.selected })
    }

    @Test
    fun `평형을 바꾸면 다시 조회하지 않고 화면만 바뀐다`() = runTest {
        val repo = FakeTradeRepository()
        val vm = viewModel(repo)
        repo.emit(listOf(trade(10, 87_500, area = 84.97), trade(20, 60_000, area = 59.99)))
        val syncsBefore = repo.syncedPlans.size

        val smaller = vm.uiState.value.areaChips.first { it.areaM2 == 59.99 }
        vm.selectArea(smaller)

        assertEquals("같은 단지라 이미 받아 둔 데이터로 충분하다", syncsBefore, repo.syncedPlans.size)
        assertEquals(smaller.key.raw, vm.uiState.value.key!!.raw)
        assertEquals("59.99㎡ (18.1평)", vm.uiState.value.areaLabel)
        assertTrue(vm.uiState.value.areaChips.first { it.areaM2 == 59.99 }.selected)
    }

    @Test
    fun `기간을 바꾸면 그 기간을 다시 조회한다`() = runTest {
        val repo = FakeTradeRepository()
        val vm = viewModel(repo)
        val before = repo.syncedPlans.size

        vm.selectPeriod(TradePeriod.THREE_YEARS)

        assertEquals(before + 1, repo.syncedPlans.size)
        assertEquals(TradePeriod.THREE_YEARS, repo.syncedPlans.last().period)
        assertEquals(TradePeriod.THREE_YEARS, vm.uiState.value.period)
    }

    @Test
    fun `같은 기간을 다시 고르면 아무 일도 하지 않는다`() = runTest {
        val repo = FakeTradeRepository()
        val vm = viewModel(repo)
        val before = repo.syncedPlans.size

        vm.selectPeriod(TradePeriod.ONE_YEAR)

        assertEquals(before, repo.syncedPlans.size)
    }

    @Test
    fun `거래가 없으면 차트를 지어내지 않고 없다고 말한다`() = runTest {
        val repo = FakeTradeRepository()
        val vm = viewModel(repo)
        repo.emit(emptyList())

        val state = vm.uiState.value
        assertTrue(state.chart == null || state.chart!!.isEmpty)
        assertNotNull(state.emptyMessage)
        assertTrue(state.emptyMessage!!.contains("거래 데이터 없음"))
        assertTrue(state.history.isEmpty())
    }

    @Test
    fun `해제된 계약은 차트에서 빠지고 이력에는 남는다`() = runTest {
        val repo = FakeTradeRepository()
        val vm = viewModel(repo)
        repo.emit(listOf(trade(30, 87_500), trade(10, 300_000, canceled = true)))

        val state = vm.uiState.value
        assertEquals("차트에는 성사된 거래만", 1, state.chart!!.pointCount)
        assertEquals("이력에는 둘 다", 2, state.history.size)
        assertEquals(1, state.history.count { it.canceled })
        assertEquals(87_500L, state.chart!!.peak!!.amountManwon)
    }

    @Test
    fun `조회 후 기준일시가 출처에 반영된다`() = runTest {
        val repo = FakeTradeRepository()
        repo.fetchedAt = Instant.parse("2026-09-04T02:20:00Z")
        val vm = viewModel(repo)

        assertEquals(
            "데이터 출처: 국토교통부 실거래가 공개시스템 (기준일시: 2026-09-04 02:20)",
            vm.uiState.value.attributionLabel(ZoneOffset.UTC),
        )
    }
}
