package com.aptprice.tracker.data.remote.parser

/**
 * 국토교통부 / 공공데이터포털이 돌려주는 오류.
 *
 * 두 가지 형태가 있다.
 * 1) 서비스 응답 헤더: `<response><header><resultCode>03</resultCode>...`
 * 2) 포털 게이트웨이 응답: `<OpenAPI_ServiceResponse><cmmMsgHeader><returnReasonCode>30</...`
 *    (인증키 오류, 트래픽 초과 등은 서비스에 닿기 전에 게이트웨이가 막는다)
 */
data class MolitApiError(
    val code: String,
    val message: String,
    val kind: Kind,
) {
    enum class Kind {
        /** 조회 결과 없음. 오류가 아니라 "그 달에 신고된 거래가 없음" 이다. */
        NO_DATA,

        /** 인증키가 등록되지 않았거나 잘못됨 */
        INVALID_SERVICE_KEY,

        /** 일일 트래픽 한도 초과 */
        QUOTA_EXCEEDED,

        /** 활용 기간 만료 */
        EXPIRED,

        /** 그 외 서비스/게이트웨이 오류 */
        SERVICE_ERROR,
    }

    /** 재시도해도 같은 결과가 나오는 오류인가. (인증·한도 문제는 재시도해도 소용없다) */
    val isRetriable: Boolean
        get() = kind == Kind.SERVICE_ERROR

    /** 화면에 그대로 띄울 수 있는 문구. */
    fun userMessage(): String = when (kind) {
        Kind.NO_DATA -> "거래 데이터 없음"
        Kind.INVALID_SERVICE_KEY -> "공공데이터포털 인증키가 올바르지 않습니다 (코드 $code)"
        Kind.QUOTA_EXCEEDED -> "공공데이터포털 일일 조회 한도를 초과했습니다 (코드 $code)"
        Kind.EXPIRED -> "공공데이터포털 활용 기간이 만료되었습니다 (코드 $code)"
        Kind.SERVICE_ERROR -> "국토교통부 실거래가 조회에 실패했습니다 (코드 $code: $message)"
    }

    companion object {
        /** 정상 응답 코드. 서비스에 따라 "00" 또는 "000" 으로 온다. */
        val SUCCESS_CODES = setOf("00", "000")

        /** 조회 결과 없음. */
        val NO_DATA_CODES = setOf("03")

        /**
         * 공공데이터포털 공통 오류코드 → 성격 매핑.
         *
         * 주의: 아래 코드값은 공공데이터포털에 공개된 공통 에러코드 표를 근거로 한 것이다.
         * 실제 응답으로 검증하기 전까지는 확정된 것으로 보지 말 것.
         * 매핑에 없는 코드는 SERVICE_ERROR 로 떨어지므로 동작은 안전하다.
         */
        fun kindOf(code: String): Kind = when (code.trim().trimStart('0').ifEmpty { "0" }) {
            "3" -> Kind.NO_DATA
            "30" -> Kind.INVALID_SERVICE_KEY
            "31" -> Kind.EXPIRED
            "22" -> Kind.QUOTA_EXCEEDED
            else -> Kind.SERVICE_ERROR
        }
    }
}

/** 조회 실패를 호출부까지 전달하는 예외. */
class MolitApiException(val error: MolitApiError) : Exception(error.userMessage())
