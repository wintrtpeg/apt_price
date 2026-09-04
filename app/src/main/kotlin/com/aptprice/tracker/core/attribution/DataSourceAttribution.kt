package com.aptprice.tracker.core.attribution

import com.aptprice.tracker.core.format.DateFormatter
import java.time.LocalDateTime

/**
 * 데이터 출처 표기 및 데이터 무결성 문구를 한 곳에서 관리한다.
 *
 * 이 앱은 국토교통부 실거래가 자료만을 근거로 화면을 그린다.
 * 값이 없거나 조회에 실패한 경우 **추정치를 만들어 채우지 않고** 아래 문구를 그대로 노출한다.
 */
object DataSourceAttribution {

    /** 원 데이터 제공기관 */
    const val PROVIDER = "국토교통부 실거래가 공개시스템"

    /** 데이터 전달 경로 */
    const val CHANNEL = "공공데이터포털(data.go.kr) Open API"

    /** 사용 API 명 */
    val API_NAMES = listOf(
        "국토교통부_아파트 매매 실거래자료 (getRTMSDataSvcAptTradeDev)",
        "국토교통부_아파트 전월세 자료 (getRTMSDataSvcAptRent)",
    )

    /**
     * 모든 목록 카드/상세 화면 하단에 노출하는 출처 라벨.
     * 예) `데이터 출처: 국토교통부 실거래가 공개시스템 (기준일시: 2026-09-04 11:20)`
     */
    fun label(fetchedAt: LocalDateTime): String =
        "데이터 출처: $PROVIDER (기준일시: ${DateFormatter.formatIsoDateTime(fetchedAt)})"

    /** 아직 한 번도 동기화되지 않은 상태의 출처 라벨. */
    const val LABEL_NOT_SYNCED = "데이터 출처: $PROVIDER (아직 동기화되지 않음)"

    /** 조회 결과가 0건일 때. 임의의 값을 채우지 않는다. */
    const val EMPTY_RESULT = "거래 데이터 없음"

    /** 해당 기간에 신고된 실거래가 없을 때. */
    const val NOT_REPORTED = "국토교통부 미신고 건"

    /** 네트워크/서버 오류로 값을 알 수 없을 때. */
    const val UNAVAILABLE = "실거래가 정보를 불러오지 못했습니다"

    /** 인증키가 설정되지 않았을 때. */
    const val MISSING_SERVICE_KEY =
        "공공데이터포털 인증키가 설정되지 않아 실거래가를 조회할 수 없습니다"

    /** 계약이 해제(취소)된 건에 붙는 배지. */
    const val CANCELED_BADGE = "계약 해제"

    /**
     * 실거래가 신고 기한 안내.
     * 부동산 거래신고 등에 관한 법률상 계약일로부터 30일 이내 신고이므로,
     * 최근 거래일수록 아직 신고되지 않은 건이 있을 수 있다는 점을 사용자에게 알린다.
     */
    const val REPORTING_DELAY_NOTICE =
        "실거래 신고 기한은 계약일로부터 30일이므로, 최근 계약 건은 아직 반영되지 않았을 수 있습니다."
}
