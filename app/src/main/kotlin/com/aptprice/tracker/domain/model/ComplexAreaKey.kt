package com.aptprice.tracker.domain.model

import java.util.Base64
import java.util.Locale

/**
 * 단지 + 전용면적을 가리키는 키. 상세 화면의 시계열이 이 단위로 묶인다.
 *
 * 형식: `LAWD_CD|법정동|단지명|전용면적(소수 2자리)`
 *
 * 숫자 서식에 [Locale.US] 를 고정해서 쓴다. 기본 로케일을 쓰면 소수 구분자가 쉼표인
 * 기기에서 `84,97` 이 되어 같은 평형이 다른 키를 갖게 된다. 이 키는 Room 에 저장되므로
 * 기기 로케일이 바뀌면 저장된 행을 더 이상 찾지 못하게 된다.
 */
@JvmInline
value class ComplexAreaKey(val raw: String) {

    /** 평형을 뺀 단지 단위 키. */
    val complexKey: String get() = raw.substringBeforeLast(SEPARATOR)

    val lawdCd: String get() = raw.substringBefore(SEPARATOR)

    val umdNm: String
        get() = raw.split(SEPARATOR).getOrElse(1) { "" }

    /**
     * 단지명. 단지명 자체에 구분자가 들어 있어도 되도록 앞 2개와 마지막 1개를 떼고 남긴다.
     */
    val aptName: String
        get() {
            val parts = raw.split(SEPARATOR)
            if (parts.size < 4) return ""
            return parts.subList(2, parts.size - 1).joinToString(SEPARATOR.toString())
        }

    /** 전용면적(㎡). 형식이 어긋나면 null. */
    val areaM2: Double?
        get() = raw.substringAfterLast(SEPARATOR).toDoubleOrNull()

    /**
     * 화면 경로에 실어 보내기 위한 인코딩.
     *
     * 키에는 `|` 와 한글, 공백이 들어갈 수 있다. URL 인코딩은 공백을 `+` 로 바꾸는 방식이
     * 구현마다 달라 왕복이 깨질 수 있으므로, URL 안전 Base64 를 쓴다.
     */
    fun encode(): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray(Charsets.UTF_8))

    override fun toString(): String = raw

    companion object {
        const val SEPARATOR = '|'

        fun of(lawdCd: String, umdNm: String, aptName: String, areaM2: Double): ComplexAreaKey =
            ComplexAreaKey("$lawdCd$SEPARATOR$umdNm$SEPARATOR$aptName$SEPARATOR${formatArea(areaM2)}")

        /** 같은 단지에서 평형만 바꾼 키. 평형 선택 칩이 쓴다. */
        fun ofComplex(complexKey: String, areaM2: Double): ComplexAreaKey =
            ComplexAreaKey("$complexKey$SEPARATOR${formatArea(areaM2)}")

        fun decode(encoded: String): ComplexAreaKey? = runCatching {
            ComplexAreaKey(String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8))
        }.getOrNull()

        /** 키에 쓰는 면적 서식. 기기 로케일에 좌우되지 않는다. */
        fun formatArea(areaM2: Double): String = String.format(Locale.US, "%.2f", areaM2)
    }
}
