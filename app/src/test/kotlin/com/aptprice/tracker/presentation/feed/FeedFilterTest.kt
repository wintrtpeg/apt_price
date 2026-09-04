package com.aptprice.tracker.presentation.feed

import com.aptprice.tracker.core.format.AreaBucket
import com.aptprice.tracker.core.time.TradePeriod
import com.aptprice.tracker.core.time.TradeQueryPlan
import com.aptprice.tracker.domain.region.RegionCatalog
import com.aptprice.tracker.domain.region.RegionGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FeedFilterTest {

    @Test
    fun `기본 선택은 대상 지역 전체다`() {
        val all = RegionSelection.all()
        assertTrue(all.isAll)
        assertEquals(36, all.codes().size)
        assertEquals("전체", all.summaryLabel())
    }

    @Test
    fun `그룹 단위로 켜고 끌 수 있다`() {
        val seoulOnly = RegionSelection(emptySet()).toggleGroup(RegionGroup.SEOUL)
        assertEquals(25, seoulOnly.codes().size)
        assertTrue(seoulOnly.isGroupFullySelected(RegionGroup.SEOUL))
        assertFalse(seoulOnly.isGroupFullySelected(RegionGroup.SEONGNAM))
        assertEquals("서울 전체", seoulOnly.summaryLabel())

        val off = seoulOnly.toggleGroup(RegionGroup.SEOUL)
        assertTrue(off.isEmpty)
        assertEquals("지역 없음", off.summaryLabel())
    }

    @Test
    fun `개별 구를 켜고 끌 수 있다`() {
        val gangnam = RegionSelection(emptySet()).toggleRegion("11680")
        assertTrue(gangnam.contains("11680"))
        assertEquals(listOf("11680"), gangnam.codes())
        assertEquals("강남구", gangnam.summaryLabel())

        val two = gangnam.toggleRegion("11650")
        assertEquals(2, two.codes().size)
        assertEquals("서초구 외 1곳", two.summaryLabel())

        assertFalse(two.toggleRegion("11680").contains("11680"))
    }

    @Test
    fun `그룹 일부만 선택된 상태를 구분한다`() {
        val partial = RegionSelection(setOf("11680"))
        assertTrue(partial.isGroupPartiallySelected(RegionGroup.SEOUL))
        assertFalse(partial.isGroupFullySelected(RegionGroup.SEOUL))
    }

    @Test
    fun `조회 코드는 카탈로그 순서를 지킨다`() {
        val shuffled = RegionSelection(setOf("41590", "11110", "11680"))
        assertEquals(listOf("11110", "11680", "41590"), shuffled.codes())
    }

    @Test
    fun `그룹 전체와 낱개 선택이 섞이면 둘 다 요약에 나온다`() {
        val mixed = RegionSelection(emptySet())
            .toggleGroup(RegionGroup.SEONGNAM)
            .toggleRegion("11680")
        assertEquals("성남 전체 · 강남구", mixed.summaryLabel())
    }

    @Test
    fun `평형대 필터를 토글할 수 있다`() {
        val filter = FeedFilter()
        assertTrue("비어 있으면 전부 통과", filter.acceptsBucket(AreaBucket.PYEONG_40))

        val small = filter.toggleAreaBucket(AreaBucket.PYEONG_20)
        assertTrue(small.acceptsBucket(AreaBucket.PYEONG_20))
        assertFalse(small.acceptsBucket(AreaBucket.PYEONG_40))

        assertTrue("다시 끄면 제한이 사라진다", small.toggleAreaBucket(AreaBucket.PYEONG_20).acceptsBucket(AreaBucket.PYEONG_40))
    }

    @Test
    fun `지역을 좁히면 조회 횟수가 줄어든다`() {
        val today = LocalDate.of(2026, 9, 4)

        val all = FeedFilter(period = TradePeriod.FIVE_YEARS, regions = RegionSelection.all())
        val allPlan = TradeQueryPlan.of(all.period, today, all.regions.codes())
        assertEquals(61 * 36, allPlan.requestCount)
        assertTrue(allPlan.isHeavy)

        val narrowed = all.copy(regions = RegionSelection(setOf("41135")))
        val narrowPlan = TradeQueryPlan.of(narrowed.period, today, narrowed.regions.codes())
        assertEquals(61, narrowPlan.requestCount)
        assertFalse(narrowPlan.isHeavy)
    }

    @Test
    fun `기본 필터는 최근 2주 매매이고 지역은 고르지 않은 상태다`() {
        val filter = FeedFilter()
        assertEquals(TradePeriod.TWO_WEEKS, filter.period)
        assertEquals(com.aptprice.tracker.domain.model.DealTab.SALE, filter.tab)
        assertEquals(FeedSort.LATEST, filter.sort)
        assertTrue(filter.areaBuckets.isEmpty())

        // 36개 지역을 기본으로 두면 앱을 켜자마자 2천여 회를 조회해 429 로 막힌다.
        // 볼 지역은 사용자가 고르고 확인을 눌러 적용한다.
        assertTrue("기본은 선택 없음이다", filter.regions.isEmpty)
        assertFalse(filter.regions.isAll)
        assertEquals(0, TradeQueryPlan.of(filter.period, LocalDate.of(2026, 9, 4), filter.regions.codes()).requestCount)
    }

    @Test
    fun `아무것도 고르지 않은 상태는 지역 없음으로 요약된다`() {
        assertEquals("지역 없음", RegionSelection.none().summaryLabel())
        assertTrue(RegionSelection.none().isEmpty)
    }

    @Test
    fun `선택 가능한 코드는 모두 카탈로그에 있다`() {
        RegionSelection.all().codes().forEach {
            assertTrue("$it 가 카탈로그에 없다", RegionCatalog.byLawdCd(it) != null)
        }
    }
}
