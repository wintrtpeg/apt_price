package com.aptprice.tracker.data.remote.throttle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 요청 간격 조절.
 *
 * 사용자가 실제로 겪은 증상은 매매 탭에서만 `HTTP 429 Too Many Requests` 가 나는 것이었다.
 * 원인은 엔드포인트가 아니라 **요청을 너무 빨리 보낸 것**이었다. 그 회귀를 여기서 막는다.
 */
class RequestThrottleTest {

    @Test
    fun `첫 요청은 기다리지 않는다`() {
        val throttle = RequestThrottle()
        assertEquals(0L, throttle.waitMillis(lastRequestAtMillis = 0L, nowMillis = 1_000L))
    }

    @Test
    fun `간격이 지나지 않았으면 남은 만큼만 기다린다`() {
        val throttle = RequestThrottle(initialIntervalMillis = 100L)

        assertEquals(70L, throttle.waitMillis(lastRequestAtMillis = 1_000L, nowMillis = 1_030L))
        assertEquals(0L, throttle.waitMillis(lastRequestAtMillis = 1_000L, nowMillis = 1_100L))
        assertEquals("이미 지났으면 음수가 되면 안 된다", 0L, throttle.waitMillis(1_000L, 5_000L))
    }

    @Test
    fun `429 를 받으면 간격이 늘어난다`() {
        val throttle = RequestThrottle(initialIntervalMillis = 100L, maxIntervalMillis = 3_000L)
        assertEquals(100L, throttle.currentIntervalMillis())

        throttle.onRateLimited()
        assertEquals(300L, throttle.currentIntervalMillis())

        throttle.onRateLimited()
        assertEquals(900L, throttle.currentIntervalMillis())
    }

    @Test
    fun `아무리 막혀도 최대 간격을 넘지 않는다`() {
        val throttle = RequestThrottle(initialIntervalMillis = 100L, maxIntervalMillis = 500L)
        repeat(10) { throttle.onRateLimited() }
        assertEquals(500L, throttle.currentIntervalMillis())
    }

    @Test
    fun `성공이 이어지면 간격이 다시 좁아진다`() {
        val throttle = RequestThrottle(initialIntervalMillis = 100L)
        throttle.onRateLimited()
        assertEquals(300L, throttle.currentIntervalMillis())

        // 연속 성공이 기준선에 닿기 전에는 그대로 둔다.
        repeat(RequestThrottle.RECOVERY_STREAK - 1) { throttle.onSuccess() }
        assertEquals(300L, throttle.currentIntervalMillis())

        throttle.onSuccess()
        assertEquals(100L, throttle.currentIntervalMillis())
    }

    @Test
    fun `한 번이라도 막히면 연속 성공 기록이 초기화된다`() {
        val throttle = RequestThrottle(initialIntervalMillis = 100L)
        throttle.onRateLimited()
        repeat(RequestThrottle.RECOVERY_STREAK - 1) { throttle.onSuccess() }

        throttle.onRateLimited()
        // 다시 막혔으므로 남아 있던 성공 기록으로 곧장 좁혀지면 안 된다.
        throttle.onSuccess()
        assertEquals(900L, throttle.currentIntervalMillis())
    }

    @Test
    fun `기본 간격 아래로는 좁히지 않는다`() {
        val throttle = RequestThrottle(initialIntervalMillis = 100L)
        repeat(RequestThrottle.RECOVERY_STREAK * 5) { throttle.onSuccess() }
        assertEquals(100L, throttle.currentIntervalMillis())
    }

    @Test
    fun `재시도 대기는 시도할수록 길어진다`() {
        val throttle = RequestThrottle()
        val first = throttle.retryDelayMillis(attempt = 0)
        val second = throttle.retryDelayMillis(attempt = 1)
        val third = throttle.retryDelayMillis(attempt = 2)

        assertEquals(RequestThrottle.RATE_LIMIT_BASE_DELAY_MILLIS, first)
        assertEquals(first * 2, second)
        assertEquals(first * 4, third)
    }

    @Test
    fun `서버가 알려 준 Retry-After 를 존중한다`() {
        val throttle = RequestThrottle()
        // 서버가 5초를 말했으면 우리 지수 backoff 보다 그 값을 따른다.
        assertEquals(5_000L, throttle.retryDelayMillis(attempt = 0, retryAfterSeconds = 5))
    }

    @Test
    fun `Retry-After 가 터무니없이 길어도 상한을 둔다`() {
        val throttle = RequestThrottle(maxIntervalMillis = 3_000L)
        val delay = throttle.retryDelayMillis(attempt = 0, retryAfterSeconds = 86_400)
        assertTrue("하루를 기다리게 두면 앱이 멈춘 것과 같다", delay <= 12_000L)
    }

    @Test
    fun `쓸모없는 Retry-After 는 무시한다`() {
        val throttle = RequestThrottle()
        // 0 이나 음수는 값이 없는 것과 같게 다룬다.
        assertEquals(
            throttle.retryDelayMillis(attempt = 1),
            throttle.retryDelayMillis(attempt = 1, retryAfterSeconds = 0),
        )
    }
}
