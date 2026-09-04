package com.aptprice.tracker.presentation.feed

import com.aptprice.tracker.core.attribution.DataSourceAttribution
import com.aptprice.tracker.core.format.DateFormatter
import com.aptprice.tracker.core.time.TradeQueryPlan
import com.aptprice.tracker.domain.repository.SyncReport
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** 목록 영역이 지금 무엇을 보여줘야 하는가. */
sealed interface FeedContent {

    /** 첫 로딩 — 스켈레톤을 보여준다. */
    data object Loading : FeedContent

    /** 보여줄 거래가 있다. */
    data class Items(val items: List<FeedItemUi>) : FeedContent

    /**
     * 조회는 성공했지만 해당 조건에 신고된 거래가 없다.
     * 오류가 아니며, 빈 목록을 지어낸 값으로 채우지 않는다.
     */
    data class Empty(val message: String) : FeedContent

    /** 조회 자체가 실패했다. 원인을 그대로 보여준다. */
    data class Error(
        val message: String,
        val retryable: Boolean,
        /** 인증키 문제인가. 다시 시도가 아니라 설정 화면으로 안내해야 한다. */
        val needsServiceKey: Boolean = false,
    ) : FeedContent
}

/**
 * 긴 기간 + 넓은 지역을 고르면 조회 횟수가 수천 회가 된다.
 * 바로 실행하지 않고 사용자에게 규모를 알리고 확인을 받는다.
 */
data class HeavyQueryPrompt(
    val requestCount: Int,
    val message: String,
) {
    companion object {
        fun of(plan: TradeQueryPlan) = HeavyQueryPrompt(
            requestCount = plan.requestCount,
            message = buildString {
                append(plan.volumeNotice())
                append("\n공공데이터포털 일일 조회 한도가 있으니, ")
                append("지역을 좁히거나 기간을 줄이면 훨씬 빠릅니다.")
            },
        )
    }
}

/** 동기화 진행 상태 (화면 상단 진행 표시줄). */
data class SyncStatus(
    val inProgress: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
) {
    val fraction: Float get() = if (total == 0) 0f else completed.toFloat() / total
    val label: String get() = "실거래가 불러오는 중 · $completed / $total"
}

/** 메인 피드 화면 전체 상태. */
data class FeedUiState(
    val filter: FeedFilter = FeedFilter(),
    val content: FeedContent = FeedContent.Loading,
    val sync: SyncStatus = SyncStatus(),
    val isRefreshing: Boolean = false,
    /** 마지막으로 국토부 자료를 받아온 시각. 없으면 아직 동기화 전. */
    val lastFetchedAt: Instant? = null,
    /** 확인이 필요한 무거운 조회. null 이면 확인 대기 없음. */
    val heavyQueryPrompt: HeavyQueryPrompt? = null,
    /** 읽지 못한 행이 있었을 때의 알림. 파서 점검 신호이므로 숨기지 않는다. */
    val parseFailureNotice: String? = null,
    /** 일부 구간을 받아오지 못했을 때의 알림. */
    val partialSyncNotice: String? = null,
) {
    /** 화면 하단에 항상 노출하는 출처 라벨. */
    fun attributionLabel(zone: ZoneId = ZoneId.systemDefault()): String =
        lastFetchedAt
            ?.let { DataSourceAttribution.label(LocalDateTime.ofInstant(it, zone)) }
            ?: DataSourceAttribution.LABEL_NOT_SYNCED

    val itemCount: Int get() = (content as? FeedContent.Items)?.items?.size ?: 0

    companion object {

        /** 동기화 결과를 화면 알림 문구로 옮긴다. 실패를 숨기지 않는다. */
        fun noticesFrom(report: SyncReport): Pair<String?, String?> {
            val parseNotice = report.parseFailures.takeIf { it > 0 }?.let {
                "국토교통부 응답 중 ${it}건을 읽지 못해 목록에서 제외했습니다. " +
                    "값을 임의로 채우지 않습니다."
            }
            // 개수만 알려 주면 원인을 알 수 없다. 가장 많이 나온 사유를 함께 보여 준다.
            val partialNotice = report.failures.takeIf { it.isNotEmpty() }?.let { failures ->
                val (reason, count) = failures
                    .groupingBy { it.message }
                    .eachCount()
                    .maxByOrNull { it.value }!!
                val firstOfKind = failures.first { it.message == reason }.key
                buildString {
                    append("${failures.size}개 구간을 불러오지 못했습니다.")
                    appendLine()
                    append("사유(${count}건): $reason")
                    appendLine()
                    append("예: ${firstOfKind.lawdCd} / ${firstOfKind.dealYmd}")
                }
            }
            return parseNotice to partialNotice
        }

        /** 조회 결과가 없을 때 보여줄 문구. */
        fun emptyMessage(filter: FeedFilter): String =
            "${filter.regions.summaryLabel()} · ${filter.period.label} 기준 " +
                "${filter.tab.label} ${DataSourceAttribution.EMPTY_RESULT}"
    }
}
