package com.aptprice.tracker.presentation.search

import com.aptprice.tracker.domain.model.AptRent
import com.aptprice.tracker.domain.model.AptTrade
import com.aptprice.tracker.presentation.feed.FakeTradeRepository
import com.aptprice.tracker.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun trade(apt: String, day: Int = 3, area: Double = 84.97) = AptTrade(
        lawdCd = "11680", umdNm = "역삼동", aptName = apt, jibun = "101",
        exclusiveAreaM2 = area, floor = 10, buildYear = 2005,
        dealDate = LocalDate.of(2026, 9, day), canceled = false, canceledDate = null,
        dealAmountManwon = 87_500, dealingGbn = null, registerDate = null,
    )

    private fun jeonse(apt: String, day: Int = 4) = AptRent(
        lawdCd = "11680", umdNm = "역삼동", aptName = apt, jibun = null,
        exclusiveAreaM2 = 59.99, floor = 3, buildYear = 2005,
        dealDate = LocalDate.of(2026, 9, day), canceled = false, canceledDate = null,
        depositManwon = 50_000, monthlyRentManwon = 0,
        contractType = null, contractTerm = null,
        previousDepositManwon = null, previousMonthlyRentManwon = null,
    )

    /** 디바운스를 지나 검색이 실제로 도는 지점까지 시간을 밀어 준다. */
    private fun kotlinx.coroutines.test.TestScope.settle() = advanceTimeBy(400)

    @Test
    fun `한 글자로는 찾지 않는다`() = runTest {
        val repo = FakeTradeRepository()
        repo.emit(listOf(trade("래미안대치팰리스")))
        val vm = SearchViewModel(repo)

        vm.onQueryChange("래")
        settle()

        assertTrue(vm.uiState.value.results.isEmpty())
        assertTrue("몇 글자가 더 필요한지 알려 준다", vm.uiState.value.tooShort)
    }

    @Test
    fun `두 글자부터 결과가 나온다`() = runTest {
        val repo = FakeTradeRepository()
        repo.emit(listOf(trade("래미안대치팰리스"), trade("은마아파트")))
        val vm = SearchViewModel(repo)

        vm.onQueryChange("래미")
        settle()

        val results = vm.uiState.value.results
        assertEquals(1, results.size)
        assertEquals("래미안대치팰리스", results.single().aptName)
        assertFalse(vm.uiState.value.tooShort)
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun `타이핑 중에는 마지막 검색어로만 찾는다`() = runTest {
        val repo = FakeTradeRepository()
        repo.emit(listOf(trade("래미안대치팰리스")))
        val vm = SearchViewModel(repo)

        vm.onQueryChange("래")
        vm.onQueryChange("래미")
        vm.onQueryChange("래미안")
        settle()

        // 글자마다 조회하면 429 로 가는 길이다. 디바운스가 실제로 걸러야 한다.
        assertEquals(listOf("래미안"), repo.searchedQueries)
    }

    @Test
    fun `매매가 없고 전세만 있는 단지도 찾는다`() = runTest {
        val repo = FakeTradeRepository()
        repo.emit(listOf(jeonse("힐스테이트")))
        val vm = SearchViewModel(repo)

        vm.onQueryChange("힐스")
        settle()

        assertEquals("힐스테이트", vm.uiState.value.results.single().aptName)
    }

    @Test
    fun `찾지 못하면 지어내지 않고 이유를 알린다`() = runTest {
        val repo = FakeTradeRepository()
        repo.emit(listOf(trade("래미안대치팰리스")))
        val vm = SearchViewModel(repo)

        vm.onQueryChange("없는단지")
        settle()

        assertTrue(vm.uiState.value.results.isEmpty())
        assertEquals(SearchViewModel.NOT_FOUND, vm.uiState.value.message)
        // 받아온 자료 안에서만 찾는다는 사실을 감추지 않는다.
        assertTrue(vm.uiState.value.message!!.contains("지역과 기간"))
    }

    @Test
    fun `고르고 조회를 눌러야 열린다`() = runTest {
        val repo = FakeTradeRepository()
        repo.emit(listOf(trade("래미안대치팰리스")))
        val vm = SearchViewModel(repo)
        vm.onQueryChange("래미")
        settle()

        assertFalse("고르기 전에는 조회할 수 없다", vm.uiState.value.canOpen)

        vm.select(vm.uiState.value.results.single())

        assertTrue(vm.uiState.value.canOpen)
        assertNotNull(vm.uiState.value.selected)
    }

    @Test
    fun `같은 단지를 다시 누르면 선택이 풀린다`() = runTest {
        val repo = FakeTradeRepository()
        repo.emit(listOf(trade("래미안대치팰리스")))
        val vm = SearchViewModel(repo)
        vm.onQueryChange("래미")
        settle()
        val summary = vm.uiState.value.results.single()

        vm.select(summary)
        vm.select(summary)

        assertNull(vm.uiState.value.selected)
        assertFalse(vm.uiState.value.canOpen)
    }

    @Test
    fun `상세로 넘길 키에 평형이 들어 있다`() = runTest {
        val repo = FakeTradeRepository()
        repo.emit(listOf(trade("래미안대치팰리스", area = 84.97)))
        val vm = SearchViewModel(repo)
        vm.onQueryChange("래미")
        settle()

        val key = vm.uiState.value.results.single().openKey()

        // 평형이 없으면 상세 화면이 시계열을 그릴 단위를 정하지 못한다.
        assertEquals(84.97, key.areaM2!!, 0.001)
        assertEquals("래미안대치팰리스", key.aptName)
        assertEquals("11680", key.lawdCd)
    }

    @Test
    fun `검색어를 지우면 결과와 선택이 함께 사라진다`() = runTest {
        val repo = FakeTradeRepository()
        repo.emit(listOf(trade("래미안대치팰리스")))
        val vm = SearchViewModel(repo)
        vm.onQueryChange("래미")
        settle()
        vm.select(vm.uiState.value.results.single())

        vm.onQueryChange("")

        assertTrue(vm.uiState.value.results.isEmpty())
        assertNull(vm.uiState.value.selected)
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun `결과에서 사라진 단지는 선택이 유지되지 않는다`() = runTest {
        val repo = FakeTradeRepository()
        repo.emit(listOf(trade("래미안대치팰리스"), trade("래미안강남")))
        val vm = SearchViewModel(repo)
        vm.onQueryChange("래미")
        settle()
        vm.select(vm.uiState.value.results.first { it.aptName == "래미안강남" })

        // 검색어를 좁혀 그 단지가 목록에서 빠졌다.
        vm.onQueryChange("래미안대치")
        settle()

        assertNull("없는 단지를 고른 채로 조회를 누르면 엉뚱한 화면이 열린다", vm.uiState.value.selected)
    }
}
