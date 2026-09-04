package com.aptprice.tracker.core.time

import java.time.LocalDate

/** 실거래가 API 호출 한 건을 가리키는 키. (시군구 코드 × 계약월) */
data class TradeRequestKey(
    val lawdCd: String,
    val dealYmd: String,
)

/**
 * 조회 기간과 대상 지역으로부터 **실제로 몇 번 API 를 때려야 하는지** 미리 계산한다.
 *
 * 실거래가 API 는 (시군구 1개 × 계약월 1개) 단위로만 응답하므로 호출 수는
 * `대상 지역 수 × 개월 수` 로 곱해진다. 기간을 5년까지 넓힐 수 있게 되면서
 * 이 값이 순식간에 커지기 때문에, 호출 전에 규모를 알고 넘어가야 한다.
 *
 * 예) 전체 36개 지역 × 최근 5년(61개월) = 2,196회 (엔드포인트 1개 기준)
 *     매매·전월세 둘 다 받으면 4,392회
 *
 * 공공데이터포털 개발계정은 일일 트래픽 한도가 있으므로, 긴 기간을 고를 때는
 * 지역을 좁히거나 캐시된 구간을 재사용해야 한다. ([isHeavy] 참고)
 */
data class TradeQueryPlan(
    val period: TradePeriod,
    val range: ClosedRange<LocalDate>,
    /** 조회 대상 시군구 코드 (LAWD_CD) */
    val lawdCodes: List<String>,
    /** 구간을 덮는 계약월 목록 (YYYYMM) */
    val dealYmdCodes: List<String>,
) {
    val regionCount: Int get() = lawdCodes.size
    val monthCount: Int get() = dealYmdCodes.size

    /** 엔드포인트 1개(매매 또는 전월세) 기준 호출 횟수. */
    val requestCount: Int get() = regionCount * monthCount

    /** 매매 + 전월세를 모두 받을 때의 호출 횟수. */
    val requestCountAllEndpoints: Int get() = requestCount * ENDPOINT_COUNT

    /** 호출량이 커서 사용자에게 알리거나 지역을 좁혀야 하는 수준인가. */
    val isHeavy: Boolean get() = requestCount > HEAVY_REQUEST_THRESHOLD

    /**
     * 실제로 보내야 하는 호출 목록. 지역 → 월 순으로 정렬된다.
     * Step 2 의 캐시 계층이 이 키 단위로 저장/조회한다.
     */
    fun requestKeys(): List<TradeRequestKey> =
        lawdCodes.flatMap { lawdCd ->
            dealYmdCodes.map { dealYmd -> TradeRequestKey(lawdCd, dealYmd) }
        }

    /** 이미 캐시된 키를 뺀 나머지 — 실제 네트워크 호출이 필요한 것만. */
    fun pendingRequestKeys(cached: Set<TradeRequestKey>): List<TradeRequestKey> =
        requestKeys().filterNot { it in cached }

    /** 지역을 좁힌 새 계획. 긴 기간을 고를 때 호출량을 줄이는 수단. */
    fun narrowedTo(lawdCodes: List<String>): TradeQueryPlan =
        copy(lawdCodes = lawdCodes.distinct())

    /** 화면에 그대로 띄울 수 있는 호출량 안내 문구. */
    fun volumeNotice(): String =
        "${period.label} · ${regionCount}개 지역 = 최대 ${requestCount}회 조회 " +
            "(${monthCount}개월 × ${regionCount}개 지역)"

    companion object {
        /** 매매(getRTMSDataSvcAptTradeDev) + 전월세(getRTMSDataSvcAptRent) */
        const val ENDPOINT_COUNT = 2

        /**
         * 이 횟수를 넘으면 "무거운 조회" 로 본다.
         *
         * 36개 지역 × 6개월(=216회) 부터 걸리도록 잡은 값이다. 절대적인 한계가 아니라
         * 사용자에게 알리고 지역을 좁히도록 유도하는 기준선이다.
         */
        const val HEAVY_REQUEST_THRESHOLD = 200

        fun of(
            period: TradePeriod,
            today: LocalDate,
            lawdCodes: List<String>,
        ): TradeQueryPlan {
            val range = period.range(today)
            return TradeQueryPlan(
                period = period,
                range = range,
                lawdCodes = lawdCodes.distinct(),
                dealYmdCodes = TradeDateWindow.dealYmdCodes(range),
            )
        }

        /** 단지 상세 차트용 — 지역 하나만 보므로 5년이어도 호출량이 작다. */
        fun forSingleRegion(
            period: TradePeriod,
            today: LocalDate,
            lawdCd: String,
        ): TradeQueryPlan = of(period, today, listOf(lawdCd))
    }
}
