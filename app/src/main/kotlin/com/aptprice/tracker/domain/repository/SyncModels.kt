package com.aptprice.tracker.domain.repository

import com.aptprice.tracker.core.time.TradeRequestKey
import com.aptprice.tracker.data.remote.parser.MolitApiError

/** 동기화 진행 상황. 화면의 진행 표시줄이 구독한다. */
data class SyncProgress(
    val completed: Int,
    val total: Int,
    /** 방금 처리한 구간 (지역 × 계약월) */
    val current: TradeRequestKey?,
) {
    val fraction: Float get() = if (total == 0) 1f else completed.toFloat() / total
}

/** 구간 하나를 받아오지 못한 사유. */
data class SyncFailure(
    val key: TradeRequestKey,
    val message: String,
)

/**
 * 동기화 결과 보고.
 *
 * 몇 건을 실제로 받아왔고, 몇 건을 캐시로 건너뛰었고, 몇 건이 실패했는지 그대로 담는다.
 * 실패를 숨기고 성공한 척하지 않는다.
 */
data class SyncReport(
    /** 계획된 전체 구간 수 */
    val planned: Int,
    /** 캐시가 아직 유효해 건너뛴 구간 수 */
    val skippedFresh: Int,
    /** 실제로 받아온 구간 수 */
    val fetched: Int,
    /** 저장한 거래 행 수 */
    val storedRows: Int,
    /** 읽지 못해 제외한 행 수 (파서 점검 신호) */
    val parseFailures: Int,
    /** 받아오지 못한 구간 */
    val failures: List<SyncFailure>,
    /**
     * 동기화를 중단시킨 오류. 인증키 오류나 트래픽 초과처럼
     * 계속 시도해도 소용없는 경우에만 채워진다.
     */
    val abortedBy: MolitApiError?,
) {
    val isComplete: Boolean get() = abortedBy == null && failures.isEmpty()

    companion object {
        /** 인증키가 없어 아무것도 하지 않은 경우. */
        fun notConfigured(planned: Int, error: MolitApiError) = SyncReport(
            planned = planned,
            skippedFresh = 0,
            fetched = 0,
            storedRows = 0,
            parseFailures = 0,
            failures = emptyList(),
            abortedBy = error,
        )
    }
}
