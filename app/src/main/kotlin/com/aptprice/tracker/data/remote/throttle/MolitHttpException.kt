package com.aptprice.tracker.data.remote.throttle

import java.io.IOException

/**
 * 2xx 가 아닌 응답. 상태 코드를 그대로 들고 있어 호출부가 판단할 수 있다.
 *
 * Retrofit 의 `HttpException` 은 상태 코드를 문자열로만 알려 주고 `IOException` 도
 * 아니라서 다루기 번거롭다. 인터셉터에서 이 예외로 바꿔 던진다.
 */
class MolitHttpException(
    val code: Int,
    /** 서버가 `Retry-After` 로 알려 준 초. 없으면 null. */
    val retryAfterSeconds: Long? = null,
) : IOException("HTTP $code") {

    /** 요청이 몰려 막힌 상태. 잠시 뒤 다시 시도하면 된다. */
    val isRateLimited: Boolean get() = code == TOO_MANY_REQUESTS

    /** 해당 서비스가 없다. 다른 서비스로 갈아타야 한다. */
    val isNotFound: Boolean get() = code == NOT_FOUND

    /** 서버 쪽 문제. 다시 시도할 만하다. */
    val isServerError: Boolean get() = code in 500..599

    companion object {
        const val TOO_MANY_REQUESTS = 429
        const val NOT_FOUND = 404
    }
}
