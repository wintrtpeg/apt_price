package com.aptprice.tracker.data.remote.throttle

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 429 응답을 만났을 때의 동작.
 *
 * 실제로 사용자가 본 오류가 `HTTP 429 Too Many Requests` 였다.
 * 그때 앱은 그대로 실패만 했다. 여기서는 **기다렸다가 다시 보내고**,
 * 그래도 안 되면 사유가 남는 예외로 올린다.
 */
class ThrottleInterceptorTest {

    /** 미리 정해 둔 응답을 차례로 내려주는 가짜 체인. */
    private class Chain(private val responses: MutableList<Response>) : Interceptor.Chain {
        val requestCount get() = served
        private var served = 0
        override fun request(): Request = Request()
        override fun proceed(request: Request): Response {
            served++
            return responses.removeAt(0)
        }
    }

    private fun ok() = Response(200)
    private fun tooMany(retryAfter: String? = null) =
        Response(429, retryAfter?.let { mapOf("Retry-After" to it) } ?: emptyMap())

    private fun interceptor(
        slept: MutableList<Long> = mutableListOf(),
        maxRetries: Int = 3,
    ) = ThrottleInterceptor(
        throttle = RequestThrottle(initialIntervalMillis = 10L, maxIntervalMillis = 100L),
        maxRetries = maxRetries,
        // 테스트에서 실제로 잠들지 않는다. 얼마나 기다리려 했는지만 기록한다.
        sleep = { millis -> slept += millis },
        now = { 0L },
    )

    @Test
    fun `성공하면 그대로 돌려준다`() {
        val chain = Chain(mutableListOf(ok()))

        val response = interceptor().intercept(chain)

        assertEquals(200, response.code)
        assertEquals(1, chain.requestCount)
    }

    @Test
    fun `429 를 만나면 기다렸다가 다시 보낸다`() {
        val slept = mutableListOf<Long>()
        val chain = Chain(mutableListOf(tooMany(), ok()))

        val response = interceptor(slept).intercept(chain)

        assertEquals(200, response.code)
        assertEquals("한 번 더 보냈어야 한다", 2, chain.requestCount)
        assertTrue("재시도 전에 실제로 기다려야 한다", slept.any { it > 0 })
    }

    @Test
    fun `재시도를 다 써도 429 면 사유가 남는 예외로 올린다`() {
        val chain = Chain(MutableList(3) { tooMany() })

        val error = assertThrows(MolitHttpException::class.java) {
            interceptor(maxRetries = 2).intercept(chain)
        }

        assertEquals(429, error.code)
        assertTrue(error.isRateLimited)
        assertEquals("처음 1회 + 재시도 2회", 3, chain.requestCount)
        // 화면에 그대로 뜨는 문구다. 원인을 감추지 않는다.
        assertTrue(error.message!!.contains("429"))
    }

    @Test
    fun `서버가 알려 준 Retry-After 를 예외에 담아 올린다`() {
        val chain = Chain(mutableListOf(tooMany(retryAfter = "7")))

        val error = assertThrows(MolitHttpException::class.java) {
            interceptor(maxRetries = 0).intercept(chain)
        }

        assertEquals(7L, error.retryAfterSeconds)
    }

    @Test
    fun `429 가 아닌 실패는 다시 보내지 않는다`() {
        val chain = Chain(mutableListOf(Response(500)))

        val error = assertThrows(MolitHttpException::class.java) {
            interceptor().intercept(chain)
        }

        assertEquals(500, error.code)
        assertTrue(error.isServerError)
        assertEquals("서버 오류에 재시도를 퍼부으면 상황만 나빠진다", 1, chain.requestCount)
    }

    @Test
    fun `404 는 없는 서비스로 구분된다`() {
        val chain = Chain(mutableListOf(Response(404)))

        val error = assertThrows(MolitHttpException::class.java) {
            interceptor().intercept(chain)
        }

        // 저장소는 404 일 때만 다른 매매 엔드포인트를 시도한다. 이 구분이 그 근거다.
        assertTrue(error.isNotFound)
        assertTrue(!error.isRateLimited)
    }

    @Test
    fun `실패한 응답은 반드시 닫는다`() {
        val failed = Response(503)
        val chain = Chain(mutableListOf(failed))

        assertThrows(MolitHttpException::class.java) { interceptor().intercept(chain) }

        assertTrue("응답을 닫지 않으면 연결이 샌다", failed.closed)
    }
}
