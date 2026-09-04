package com.aptprice.tracker.data.remote.throttle

import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * 429 응답을 만났을 때의 동작.
 *
 * 실제로 사용자가 본 오류가 `HTTP 429 Too Many Requests` 였다.
 * 그때 앱은 그대로 실패만 했다. 여기서는 **기다렸다가 다시 보내고**,
 * 그래도 안 되면 사유가 남는 예외로 올린다.
 *
 * 가짜 okhttp 타입을 쓰지 않고 실제 `Request`/`Response` 를 만들어 넣는다.
 * 스텁으로는 인터셉터가 실제 응답 객체를 제대로 다루는지 알 수 없다.
 */
class ThrottleInterceptorTest {

    private val request = Request.Builder().url("https://apis.data.go.kr/1613000/").build()

    /** 닫혔는지 알 수 있는 본문. 실패한 응답을 닫지 않으면 연결이 샌다. */
    private class TrackingBody : ResponseBody() {
        var closed = false
            private set

        override fun contentType(): MediaType? = null
        override fun contentLength(): Long = 0L
        override fun source(): BufferedSource = Buffer()
        override fun close() {
            closed = true
            super.close()
        }
    }

    private fun response(code: Int, retryAfter: String? = null, body: TrackingBody = TrackingBody()) =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("test $code")
            .apply { retryAfter?.let { header("Retry-After", it) } }
            .body(body)
            .build()

    /** 미리 정해 둔 응답을 차례로 내려주는 체인. */
    private class FakeChain(private val responses: MutableList<Response>) : Interceptor.Chain {
        var requestCount = 0
            private set

        private val request = Request.Builder().url("https://apis.data.go.kr/1613000/").build()

        override fun request(): Request = request

        override fun proceed(request: Request): Response {
            requestCount++
            return responses.removeAt(0)
        }

        // 인터셉터가 쓰지 않는 나머지 계약. 불리면 테스트가 잘못된 것이다.
        override fun connection(): Connection? = null
        override fun call(): Call = OkHttpClient().newCall(request)
        override fun connectTimeoutMillis(): Int = 0
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun readTimeoutMillis(): Int = 0
        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun writeTimeoutMillis(): Int = 0
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    }

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
        val chain = FakeChain(mutableListOf(response(200)))

        val result = interceptor().intercept(chain)

        assertEquals(200, result.code)
        assertEquals(1, chain.requestCount)
    }

    @Test
    fun `429 를 만나면 기다렸다가 다시 보낸다`() {
        val slept = mutableListOf<Long>()
        val chain = FakeChain(mutableListOf(response(429), response(200)))

        val result = interceptor(slept).intercept(chain)

        assertEquals(200, result.code)
        assertEquals("한 번 더 보냈어야 한다", 2, chain.requestCount)
        assertTrue("재시도 전에 실제로 기다려야 한다", slept.any { it > 0 })
    }

    @Test
    fun `재시도를 다 써도 429 면 사유가 남는 예외로 올린다`() {
        val chain = FakeChain(MutableList(3) { response(429) })

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
        val chain = FakeChain(mutableListOf(response(429, retryAfter = "7")))

        val error = assertThrows(MolitHttpException::class.java) {
            interceptor(maxRetries = 0).intercept(chain)
        }

        assertEquals(7L, error.retryAfterSeconds)
    }

    @Test
    fun `429 가 아닌 실패는 다시 보내지 않는다`() {
        val chain = FakeChain(mutableListOf(response(500)))

        val error = assertThrows(MolitHttpException::class.java) {
            interceptor().intercept(chain)
        }

        assertEquals(500, error.code)
        assertTrue(error.isServerError)
        assertEquals("서버 오류에 재시도를 퍼부으면 상황만 나빠진다", 1, chain.requestCount)
    }

    @Test
    fun `404 는 없는 서비스로 구분된다`() {
        val chain = FakeChain(mutableListOf(response(404)))

        val error = assertThrows(MolitHttpException::class.java) {
            interceptor().intercept(chain)
        }

        // 저장소는 404 일 때만 다른 매매 엔드포인트를 시도한다. 이 구분이 그 근거다.
        assertTrue(error.isNotFound)
        assertFalse(error.isRateLimited)
    }

    @Test
    fun `실패한 응답은 반드시 닫는다`() {
        val body = TrackingBody()
        val chain = FakeChain(mutableListOf(response(503, body = body)))

        assertThrows(MolitHttpException::class.java) { interceptor().intercept(chain) }

        assertTrue("응답을 닫지 않으면 연결이 샌다", body.closed)
    }

    @Test
    fun `재시도 중에 버리는 429 응답도 닫는다`() {
        val discarded = TrackingBody()
        val chain = FakeChain(
            mutableListOf(response(429, body = discarded), response(200)),
        )

        interceptor().intercept(chain)

        assertTrue("다시 보내기 전에 앞선 응답을 닫아야 한다", discarded.closed)
    }
}
