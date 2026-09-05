package com.aptprice.tracker.data.remote.throttle

import kotlin.math.min

/**
 * 공공데이터포털에 보내는 요청 간격을 조절한다.
 *
 * 포털은 짧은 시간에 요청이 몰리면 `HTTP 429 Too Many Requests` 로 막는다.
 * 한 번 막히기 시작하면 그 뒤 요청도 줄줄이 막히므로, 막힐 때마다 간격을 늘리고
 * 한동안 유지한다. 성공이 이어지면 조금씩 다시 좁힌다.
 *
 * 상태만 계산하는 순수 클래스다. 실제 대기는 호출부가 한다.
 */
class RequestThrottle(
    /** 처음 시작할 요청 간격(ms) */
    private val initialIntervalMillis: Long = DEFAULT_INITIAL_INTERVAL_MILLIS,
    /** 아무리 막혀도 이보다 길게 기다리지는 않는다 */
    private val maxIntervalMillis: Long = DEFAULT_MAX_INTERVAL_MILLIS,
) {
    @Volatile
    private var intervalMillis: Long = initialIntervalMillis

    @Volatile
    private var successStreak: Int = 0

    /** 지금 적용 중인 요청 간격(ms) */
    fun currentIntervalMillis(): Long = intervalMillis

    /**
     * 직전 요청 시각으로부터 얼마나 더 기다려야 하는지.
     * 이미 충분히 지났으면 0.
     */
    fun waitMillis(lastRequestAtMillis: Long, nowMillis: Long): Long {
        if (lastRequestAtMillis <= 0L) return 0L
        val elapsed = nowMillis - lastRequestAtMillis
        return (intervalMillis - elapsed).coerceAtLeast(0L)
    }

    /** 429 를 받았다. 간격을 늘린다. */
    fun onRateLimited() {
        successStreak = 0
        intervalMillis = min(intervalMillis * BACKOFF_FACTOR, maxIntervalMillis)
    }

    /** 요청이 성공했다. 연속으로 잘 되면 간격을 조금 좁힌다. */
    fun onSuccess() {
        successStreak++
        if (successStreak >= RECOVERY_STREAK && intervalMillis > initialIntervalMillis) {
            successStreak = 0
            intervalMillis = (intervalMillis / BACKOFF_FACTOR).coerceAtLeast(initialIntervalMillis)
        }
    }

    /**
     * 429 를 받았을 때 다시 시도하기까지 기다릴 시간.
     *
     * @param attempt 0부터 시작하는 시도 횟수
     * @param retryAfterSeconds 서버가 `Retry-After` 로 알려 준 값. 있으면 그것을 존중한다.
     */
    fun retryDelayMillis(attempt: Int, retryAfterSeconds: Long? = null): Long {
        retryAfterSeconds?.takeIf { it > 0 }?.let {
            return min(it * 1000L, maxIntervalMillis * 4)
        }
        val exponential = RATE_LIMIT_BASE_DELAY_MILLIS * (1L shl attempt.coerceIn(0, 5))
        return min(exponential, maxIntervalMillis * 4)
    }

    companion object {
        /**
         * 기본 요청 간격.
         * 포털이 정확히 어떤 기준으로 막는지 공개되어 있지 않아, 실사용에서 429 가
         * 나지 않는 선으로 보수적으로 잡았다. 막히면 아래 배수로 자동으로 늘어난다.
         */
        const val DEFAULT_INITIAL_INTERVAL_MILLIS = 120L
        const val DEFAULT_MAX_INTERVAL_MILLIS = 3_000L

        /** 429 를 받으면 간격을 이 배수로 늘린다. */
        const val BACKOFF_FACTOR = 3L

        /** 이만큼 연속 성공하면 간격을 다시 좁힌다. */
        const val RECOVERY_STREAK = 20

        /** 429 재시도 기본 대기(ms). 시도할수록 두 배씩. */
        const val RATE_LIMIT_BASE_DELAY_MILLIS = 1_500L
    }
}
