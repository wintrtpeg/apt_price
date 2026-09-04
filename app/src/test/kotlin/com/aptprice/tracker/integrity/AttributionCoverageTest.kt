package com.aptprice.tracker.integrity

import com.aptprice.tracker.core.attribution.DataSourceAttribution
import com.aptprice.tracker.core.time.TradePeriod
import com.aptprice.tracker.presentation.detail.DetailUiState
import com.aptprice.tracker.presentation.feed.FeedContent
import com.aptprice.tracker.presentation.feed.FeedFilter
import com.aptprice.tracker.presentation.feed.FeedUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

/**
 * 출처 표기 커버리지.
 *
 * 작업지시서 2.2 는 모든 화면에 데이터 출처를 명시하도록 한다.
 * 로딩 중이든 오류든 결과가 없든, **어떤 상태에서도 출처가 사라지지 않아야** 한다.
 */
class AttributionCoverageTest {

    private val provider = DataSourceAttribution.PROVIDER

    /** 피드 화면이 가질 수 있는 모든 상태. */
    private fun everyFeedState(): List<Pair<String, FeedUiState>> {
        val contents = listOf(
            "로딩" to FeedContent.Loading,
            "목록" to FeedContent.Items(emptyList()),
            "결과 없음" to FeedContent.Empty(FeedUiState.emptyMessage(FeedFilter())),
            "오류" to FeedContent.Error("조회 실패", retryable = true),
        )
        val fetchedAt = listOf(
            "동기화 전" to null,
            "동기화 후" to Instant.parse("2026-09-04T02:20:00Z"),
        )
        return contents.flatMap { (contentName, content) ->
            fetchedAt.map { (syncName, instant) ->
                "$contentName / $syncName" to FeedUiState(content = content, lastFetchedAt = instant)
            }
        }
    }

    private fun everyDetailState(): List<Pair<String, DetailUiState>> = listOf(
        "로딩" to DetailUiState(isLoading = true),
        "결과 없음" to DetailUiState(
            isLoading = false,
            emptyMessage = DetailUiState.emptyMessageFor(TradePeriod.ONE_YEAR),
        ),
        "동기화 후" to DetailUiState(lastFetchedAt = Instant.parse("2026-09-04T02:20:00Z")),
    )

    @Test
    fun `피드 화면은 어떤 상태에서도 출처를 표기한다`() {
        everyFeedState().forEach { (name, state) ->
            val label = state.attributionLabel(ZoneOffset.UTC)
            assertTrue("[$name] 출처 문구가 비어 있다", label.isNotBlank())
            assertTrue("[$name] 제공기관이 없다: $label", label.contains(provider))
        }
    }

    @Test
    fun `상세 화면은 어떤 상태에서도 출처를 표기한다`() {
        everyDetailState().forEach { (name, state) ->
            val label = state.attributionLabel(ZoneOffset.UTC)
            assertTrue("[$name] 출처 문구가 비어 있다", label.isNotBlank())
            assertTrue("[$name] 제공기관이 없다: $label", label.contains(provider))
        }
    }

    @Test
    fun `동기화한 화면은 기준일시까지 표기한다`() {
        val feed = FeedUiState(lastFetchedAt = Instant.parse("2026-09-04T02:20:00Z"))
        val detail = DetailUiState(lastFetchedAt = Instant.parse("2026-09-04T02:20:00Z"))

        listOf(feed.attributionLabel(ZoneOffset.UTC), detail.attributionLabel(ZoneOffset.UTC))
            .forEach { label ->
                assertTrue("기준일시가 없다: $label", label.contains("기준일시"))
                assertTrue("기준일시 값이 없다: $label", label.contains("2026-09-04 02:20"))
            }
    }

    @Test
    fun `동기화 전에는 기준일시를 지어내지 않는다`() {
        val label = FeedUiState(lastFetchedAt = null).attributionLabel(ZoneOffset.UTC)
        assertTrue(label.contains("아직 동기화되지 않음"))
        // 받아온 적 없는데 지금 시각을 기준일시처럼 적으면 거짓말이 된다.
        assertFalse("기준일시를 지어냈다: $label", label.contains("기준일시"))
    }

    @Test
    fun `값 없음을 알리는 문구가 모두 준비되어 있다`() {
        listOf(
            DataSourceAttribution.EMPTY_RESULT,
            DataSourceAttribution.NOT_REPORTED,
            DataSourceAttribution.UNAVAILABLE,
            DataSourceAttribution.MISSING_SERVICE_KEY,
            DataSourceAttribution.CANCELED_BADGE,
            DataSourceAttribution.REPORTING_DELAY_NOTICE,
        ).forEach { message ->
            assertTrue("빈 문구가 있다", message.isNotBlank())
        }
    }

    @Test
    fun `사용하는 API 가 명시되어 있다`() {
        assertTrue(DataSourceAttribution.API_NAMES.size >= 2)
        assertTrue(DataSourceAttribution.API_NAMES.any { it.contains("getRTMSDataSvcAptTradeDev") })
        assertTrue(DataSourceAttribution.API_NAMES.any { it.contains("getRTMSDataSvcAptRent") })
        assertTrue(DataSourceAttribution.CHANNEL.contains("data.go.kr"))
    }

    @Test
    fun `빈 결과 문구는 조회 조건을 함께 알려 준다`() {
        val message = FeedUiState.emptyMessage(FeedFilter())
        assertTrue(message.contains(DataSourceAttribution.EMPTY_RESULT))
        // 어떤 조건으로 조회했는지 모르면 "없다" 는 말이 쓸모가 없다.
        assertTrue(message.contains("최근 2주"))
        assertTrue(message.contains("매매"))
    }
}
