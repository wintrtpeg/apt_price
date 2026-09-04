package com.aptprice.tracker.domain.model

import java.time.LocalDate

/**
 * 아파트 검색 결과 한 줄.
 *
 * **이미 받아온 자료 안에서만 찾는다.** 국토교통부 API 는 단지명으로 조회하는 기능을
 * 제공하지 않고 (시군구 × 계약월) 단위로만 응답하므로, 전국 단지를 검색하려면 전국을
 * 통째로 받아와야 한다. 그건 조회 한도에 걸린다.
 *
 * 따라서 검색은 "지역과 기간을 골라 받아 둔 자료" 를 대상으로 한다.
 * 화면에서도 그 사실을 그대로 알린다.
 */
data class ComplexSummary(
    val complexKey: String,
    val aptName: String,
    val lawdCd: String,
    val umdNm: String,
    /** 예) `강남구 역삼동` */
    val regionLabel: String,
    /** 받아온 자료 기준 가장 최근 계약일 */
    val latestDealDate: LocalDate,
    /**
     * 가장 최근 거래의 전용면적(㎡).
     *
     * 상세 화면은 (단지 + 평형) 하나를 열도록 되어 있어 평형이 정해져야 한다.
     * 임의로 고르지 않고 **가장 최근에 거래된 평형**을 시작점으로 삼는다.
     * 다른 평형은 상세 화면의 평형 칩에서 바로 바꿀 수 있다.
     */
    val latestAreaM2: Double,
    /** 받아온 자료 안에서 이 단지의 거래 건수. 검색 결과의 신뢰도를 가늠하는 값이다. */
    val dealCount: Int,
) {
    /** 상세 화면으로 넘길 (단지 + 평형) 키. */
    fun openKey(): ComplexAreaKey = ComplexAreaKey.ofComplex(complexKey, latestAreaM2)
}
