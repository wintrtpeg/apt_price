package com.aptprice.tracker.presentation.feed

import com.aptprice.tracker.core.time.TradePeriod
import com.aptprice.tracker.core.time.TradeQueryPlan
import com.aptprice.tracker.core.time.TradeRequestKey
import com.aptprice.tracker.domain.region.RegionCatalog
import com.aptprice.tracker.domain.repository.SyncFailure
import com.aptprice.tracker.domain.repository.SyncReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class FeedUiStateTest {

    private fun report(
        parseFailures: Int = 0,
        failures: List<SyncFailure> = emptyList(),
    ) = SyncReport(
        planned = 10,
        skippedFresh = 0,
        fetched = 10,
        storedRows = 5,
        parseFailures = parseFailures,
        failures = failures,
        abortedBy = null,
    )

    @Test
    fun `동기화 전에도 출처는 표기한다`() {
        val state = FeedUiState()
        assertTrue(state.attributionLabel().contains("국토교통부 실거래가 공개시스템"))
        assertTrue(state.attributionLabel().contains("아직 동기화되지 않음"))
    }

    @Test
    fun `동기화 후에는 기준일시가 출처에 들어간다`() {
        val state = FeedUiState(lastFetchedAt = Instant.parse("2026-09-04T02:20:00Z"))
        val label = state.attributionLabel(ZoneOffset.UTC)
        assertEquals("데이터 출처: 국토교통부 실거래가 공개시스템 (기준일시: 2026-09-04 02:20)", label)
    }

    @Test
    fun `읽지 못한 행이 있으면 숨기지 않고 알린다`() {
        val (parseNotice, partialNotice) = FeedUiState.noticesFrom(report(parseFailures = 3))
        assertNotNull(parseNotice)
        assertTrue(parseNotice!!.contains("3건"))
        assertTrue(parseNotice.contains("임의로 채우지 않습니다"))
        assertNull(partialNotice)
    }

    @Test
    fun `일부 구간을 못 받았으면 목록이 불완전함을 알린다`() {
        val failures = listOf(
            SyncFailure(TradeRequestKey("11680", "202609"), "네트워크 오류"),
            SyncFailure(TradeRequestKey("41135", "202608"), "네트워크 오류"),
        )
        val (_, partialNotice) = FeedUiState.noticesFrom(report(failures = failures))
        assertNotNull(partialNotice)
        assertTrue(partialNotice!!.contains("2개 구간"))
    }

    @Test
    fun `문제가 없으면 알림도 없다`() {
        val (parseNotice, partialNotice) = FeedUiState.noticesFrom(report())
        assertNull(parseNotice)
        assertNull(partialNotice)
    }

    @Test
    fun `빈 결과 문구에 조건이 그대로 담긴다`() {
        val filter = FeedFilter(
            period = TradePeriod.TWO_WEEKS,
            regions = RegionSelection(setOf("11680")),
        )
        val message = FeedUiState.emptyMessage(filter)
        assertTrue(message.contains("강남구"))
        assertTrue(message.contains("최근 2주"))
        assertTrue(message.contains("매매"))
        assertTrue(message.contains("거래 데이터 없음"))
    }

    @Test
    fun `무거운 조회는 규모와 대안을 함께 알린다`() {
        val plan = TradeQueryPlan.of(TradePeriod.FIVE_YEARS, LocalDate.of(2026, 9, 4), RegionCatalog.lawdCodes)
        val prompt = HeavyQueryPrompt.of(plan)

        assertEquals(2196, prompt.requestCount)
        assertTrue(prompt.message.contains("최근 5년"))
        assertTrue(prompt.message.contains("2196"))
        assertTrue(prompt.message.contains("지역을 좁히거나"))
    }

    @Test
    fun `진행률 라벨과 비율이 맞다`() {
        val status = SyncStatus(inProgress = true, completed = 36, total = 72)
        assertEquals(0.5f, status.fraction, 1e-6f)
        assertEquals("실거래가 불러오는 중 · 36 / 72", status.label)
        assertEquals(0f, SyncStatus().fraction, 1e-6f)
    }

    @Test
    fun `목록 개수는 Items 일 때만 센다`() {
        assertEquals(0, FeedUiState().itemCount)
        assertEquals(0, FeedUiState(content = FeedContent.Empty("없음")).itemCount)
        assertEquals(0, FeedUiState(content = FeedContent.Error("실패", true)).itemCount)
    }
}
