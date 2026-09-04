package com.aptprice.tracker.data.remote.api

import java.net.URLEncoder

/**
 * 공공데이터포털 인증키 공급자.
 *
 * ## 인코딩을 직접 하는 이유
 * 포털은 인증키를 Encoding/Decoding 두 가지로 발급한다. 이 앱은 **Decoding 키**를 받아
 * 여기서 한 번만 퍼센트 인코딩한다. OkHttp 의 기본 쿼리 인코딩은 `+` 를 그대로 통과시키는데,
 * 서버가 이를 공백으로 해석해 인증이 깨지는 문제가 잘 알려져 있다. 그래서 여기서 `+` `/` `=`
 * 까지 확실히 인코딩한 뒤 `@Query(encoded = true)` 로 넘긴다.
 *
 * Encoding 키를 넣으면 이중 인코딩이 되어 인증에 실패한다. local.properties 에는 반드시
 * **일반 인증키(Decoding)** 를 넣을 것.
 */
class ServiceKeyProvider(
    private val rawKey: String,
) {
    /** 인증키가 설정되어 있는가. 없으면 조회를 시도하지 않는다. */
    val isConfigured: Boolean = rawKey.isNotBlank()

    /** 요청에 그대로 붙일 수 있는 퍼센트 인코딩된 인증키. */
    val encodedKey: String by lazy {
        if (!isConfigured) "" else URLEncoder.encode(rawKey, Charsets.UTF_8.name())
    }
}
