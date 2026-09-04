package com.aptprice.tracker.data.remote.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.net.URLEncoder

/**
 * 인증키를 기기에 보관하는 저장소.
 *
 * 앱에서 직접 입력받아 저장하므로 **APK 안에 인증키를 넣지 않아도 된다.**
 * 공개 저장소에서 APK 를 배포하더라도 키가 함께 유출되지 않는다.
 */
interface ServiceKeyStore {
    /** 저장된 키. 없으면 빈 문자열. */
    val keyFlow: Flow<String>

    suspend fun save(key: String)

    suspend fun clear()
}

/**
 * 공공데이터포털 인증키 공급자.
 *
 * 키를 찾는 순서
 * 1. 앱에서 입력해 기기에 저장한 키 ([ServiceKeyStore])
 * 2. 빌드 시 주입된 키 (`local.properties` → `BuildConfig.MOLIT_SERVICE_KEY`)
 *
 * PC 에서 빌드할 때는 2번이 그대로 동작하고, APK 만 받아 쓰는 경우 1번으로 채운다.
 *
 * ## 인코딩을 직접 하는 이유
 * 포털은 인증키를 Encoding/Decoding 두 가지로 발급한다. 이 앱은 **Decoding 키**를 받아
 * 여기서 한 번만 퍼센트 인코딩한다. OkHttp 의 기본 쿼리 인코딩은 `+` 를 그대로 통과시키는데,
 * 서버가 이를 공백으로 해석해 인증이 깨지는 문제가 잘 알려져 있다. 그래서 여기서 `+` `/` `=`
 * 까지 확실히 인코딩한 뒤 `@Query(encoded = true)` 로 넘긴다.
 *
 * Encoding 키를 넣으면 이중 인코딩이 되어 인증에 실패한다.
 */
class ServiceKeyProvider(
    private val store: ServiceKeyStore,
    /** 빌드 시 주입된 키. 없으면 빈 문자열. */
    private val buildConfigKey: String,
) {

    /** 지금 쓸 수 있는 원본 키. 없으면 빈 문자열. */
    suspend fun rawKey(): String = store.keyFlow.first().ifBlank { buildConfigKey }.trim()

    /**
     * 요청에 그대로 붙일 수 있는 퍼센트 인코딩된 키.
     * 설정된 키가 없으면 `null` — 호출부는 조회를 시도하지 않아야 한다.
     */
    suspend fun encodedKey(): String? = rawKey().takeIf { it.isNotBlank() }?.let(::encode)

    /** 키가 설정되어 있는가. 설정 화면과 피드가 함께 구독한다. */
    val isConfigured: Flow<Boolean> =
        store.keyFlow.map { it.isNotBlank() || buildConfigKey.isNotBlank() }

    /** 사용자가 입력한 키를 저장한다. 앞뒤 공백은 붙여넣기 사고가 잦아 정리한다. */
    suspend fun save(key: String) = store.save(key.trim())

    suspend fun clear() = store.clear()

    companion object {
        /** 순수 함수로 두어 테스트할 수 있게 한다. */
        fun encode(rawKey: String): String =
            URLEncoder.encode(rawKey, Charsets.UTF_8.name())

        /**
         * 붙여넣은 값이 인증키로 보이는지 간단히 살핀다.
         *
         * 형식을 엄밀히 검증할 수는 없다. 실제 유효성은 조회해 봐야 알 수 있으므로,
         * 여기서는 명백히 잘못된 입력만 걸러 내고 나머지는 통과시킨다.
         */
        fun looksLikeKey(input: String): KeyFormatHint {
            val trimmed = input.trim()
            return when {
                trimmed.isEmpty() -> KeyFormatHint.EMPTY
                trimmed.length < MIN_KEY_LENGTH -> KeyFormatHint.TOO_SHORT
                // 퍼센트 인코딩된 문자가 보이면 Encoding 키를 붙여넣은 것이다.
                ENCODED_MARKER.containsMatchIn(trimmed) -> KeyFormatHint.LOOKS_ENCODED
                trimmed.any { it.isWhitespace() } -> KeyFormatHint.HAS_WHITESPACE
                else -> KeyFormatHint.OK
            }
        }

        private const val MIN_KEY_LENGTH = 20
        private val ENCODED_MARKER = Regex("%[0-9A-Fa-f]{2}")
    }
}

/** 입력값에 대한 안내. 저장을 막는 것은 [EMPTY] 뿐이다. */
enum class KeyFormatHint(val message: String?) {
    OK(null),
    EMPTY("인증키를 입력해 주세요"),
    TOO_SHORT("인증키가 너무 짧습니다. 전체를 붙여넣었는지 확인해 주세요"),
    LOOKS_ENCODED(
        "Encoding 키로 보입니다(%2B 같은 문자 포함). " +
            "일반 인증키(Decoding) 를 넣어야 인증에 성공합니다",
    ),
    HAS_WHITESPACE("키 중간에 공백이 있습니다. 줄바꿈까지 복사되지 않았는지 확인해 주세요"),
    ;

    val isBlocking: Boolean get() = this == EMPTY
}
