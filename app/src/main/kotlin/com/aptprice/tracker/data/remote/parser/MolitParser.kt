package com.aptprice.tracker.data.remote.parser

import com.aptprice.tracker.domain.model.AptRent
import com.aptprice.tracker.domain.model.AptTrade

/**
 * 국토교통부 실거래가 응답(XML) → 도메인 모델.
 *
 * 읽지 못한 행은 버리되 [MolitPage.failures] 에 사유가 남는다.
 * 조회 결과가 없는 달은 오류가 아니라 빈 페이지로 돌아온다.
 *
 * @throws MolitApiException 인증키 오류·트래픽 초과 등 조회 자체가 실패한 경우
 */
object MolitParser {

    fun parseTrades(xml: String, lawdCd: String): MolitPage<AptTrade> =
        MolitResponse.parse(xml) { AptTradeParser.parseRow(it, lawdCd) }

    fun parseRents(xml: String, lawdCd: String): MolitPage<AptRent> =
        MolitResponse.parse(xml) { AptRentParser.parseRow(it, lawdCd) }

    /**
     * 오류 응답 본문에서 사유만 읽는다. 오류 형태가 아니면 null.
     *
     * 2xx 가 아닌 응답에 쓴다. `HTTP 403` 만으로는 사용자가 할 수 있는 일이 없지만,
     * 본문에는 `SERVICE_ACCESS_DENIED_ERROR` 처럼 실제 사유가 들어 있다.
     */
    fun parseError(xml: String): MolitApiError? = MolitResponse.errorOf(xml)
}
