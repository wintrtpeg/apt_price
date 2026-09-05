package com.aptprice.tracker.data.remote.throttle

import java.io.IOException

/**
 * 2xx 가 아닌 응답. 상태 코드를 그대로 들고 있어 호출부가 판단할 수 있다.
 *
 * Retrofit 의 `HttpException` 은 상태 코드를 문자열로만 알려 주고 `IOException` 도
 * 아니라서 다루기 번거롭다. 인터셉터에서 이 예외로 바꿔 던진다.
 *
 * **본문을 반드시 함께 싣는다.** 공공데이터포털은 실제 사유를 상태 코드가 아니라
 * 본문 XML(`<returnAuthMsg>SERVICE_ACCESS_DENIED_ERROR</...>`)에 담아 보낸다.
 * 본문을 버리면 화면에 `HTTP 403` 만 남아 사용자가 무엇을 해야 하는지 알 수 없다.
 */
class MolitHttpException(
    val code: Int,
    /** 서버가 `Retry-After` 로 알려 준 초. 없으면 null. */
    val retryAfterSeconds: Long? = null,
    /** 오류 응답 본문. 사유가 여기 들어 있다. */
    val body: String? = null,
) : IOException(messageOf(code, body)) {

    /** 요청이 몰려 막힌 상태. 잠시 뒤 다시 시도하면 된다. */
    val isRateLimited: Boolean get() = code == TOO_MANY_REQUESTS

    /** 해당 서비스가 없다. 다른 서비스로 갈아타야 한다. */
    val isNotFound: Boolean get() = code == NOT_FOUND

    /**
     * 인증키는 있지만 **이 서비스를 쓸 권한이 없다.**
     *
     * 공공데이터포털은 활용신청하지 않은(또는 승인 대기 중인) API 를 부르면 403 을 준다.
     * 매매 자료는 상세·기본 두 서비스로 나뉘어 있어, 한쪽이 막혀 있으면
     * 다른 쪽은 열려 있을 수 있다. 그래서 이 경우도 엔드포인트를 갈아타 본다.
     */
    val isAccessDenied: Boolean get() = code == FORBIDDEN || code == UNAUTHORIZED

    /** 서버 쪽 문제. 다시 시도할 만하다. */
    val isServerError: Boolean get() = code in 500..599

    companion object {
        const val TOO_MANY_REQUESTS = 429
        const val NOT_FOUND = 404
        const val FORBIDDEN = 403
        const val UNAUTHORIZED = 401

        /** 본문에 사유가 있으면 함께 남긴다. 로그에서 상태 코드만 보이는 일을 막는다. */
        private fun messageOf(code: Int, body: String?): String {
            val summary = body?.let { summarize(it) }
            return if (summary.isNullOrBlank()) "HTTP $code" else "HTTP $code: $summary"
        }

        /** XML 태그를 걷어내고 사람이 읽을 만한 길이로 줄인다. */
        private fun summarize(body: String): String =
            body.replace(Regex("<[^>]*>"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(MAX_SUMMARY_LENGTH)

        private const val MAX_SUMMARY_LENGTH = 200
    }
}
