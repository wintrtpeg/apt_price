package com.aptprice.tracker.data.remote.throttle

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.atomic.AtomicLong

/**
 * 요청 간격을 지키고, 429 를 만나면 잠시 기다렸다 다시 보낸다.
 *
 * 속도 제한은 전송 계층의 일이므로 여기서 처리한다. 저장소는 429 를 신경 쓰지 않아도 된다.
 * 여기서도 해결되지 않으면 [MolitHttpException] 으로 올려보내 화면에 사유가 남게 한다.
 */
class ThrottleInterceptor(
    private val throttle: RequestThrottle = RequestThrottle(),
    private val maxRetries: Int = DEFAULT_MAX_RETRIES,
    private val sleep: (Long) -> Unit = { millis -> if (millis > 0) Thread.sleep(millis) },
    private val now: () -> Long = System::currentTimeMillis,
) : Interceptor {

    private val lastRequestAt = AtomicLong(0L)

    override fun intercept(chain: Interceptor.Chain): Response {
        repeat(maxRetries + 1) { attempt ->
            sleep(throttle.waitMillis(lastRequestAt.get(), now()))
            lastRequestAt.set(now())

            val response = chain.proceed(chain.request())
            when {
                response.isSuccessful -> {
                    throttle.onSuccess()
                    return response
                }

                response.code == MolitHttpException.TOO_MANY_REQUESTS -> {
                    val retryAfter = response.header("Retry-After")?.toLongOrNull()
                    response.close()
                    throttle.onRateLimited()
                    if (attempt == maxRetries) {
                        throw MolitHttpException(response.code, retryAfter)
                    }
                    sleep(throttle.retryDelayMillis(attempt, retryAfter))
                }

                else -> {
                    val code = response.code
                    response.close()
                    throw MolitHttpException(code)
                }
            }
        }
        // repeat 가 끝났다는 것은 429 로 모두 소진했다는 뜻이다.
        throw MolitHttpException(MolitHttpException.TOO_MANY_REQUESTS)
    }

    private companion object {
        const val DEFAULT_MAX_RETRIES = 3
    }
}
