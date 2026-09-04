package com.aptprice.tracker.core.format

import kotlin.math.roundToInt

/**
 * 전용면적(㎡) ↔ 평 변환 및 평형대 분류.
 *
 * 국토교통부 실거래가 API 의 `전용면적` 은 ㎡ 단위 실수 문자열이다.
 * 작업지시서 기준에 따라 `평 = ㎡ × 0.3025` 로 환산한다.
 */
object AreaFormatter {

    /** 1㎡ = 0.3025평 (1평 = 3.3058㎡). */
    const val PYEONG_PER_SQUARE_METER = 0.3025

    /** API 의 전용면적 문자열을 ㎡ 실수로 변환한다. 실패 시 null. */
    fun parseAreaM2(raw: String?): Double? {
        val cleaned = raw?.trim()?.replace(",", "") ?: return null
        if (cleaned.isEmpty()) return null
        val value = cleaned.toDoubleOrNull() ?: return null
        return if (value > 0.0) value else null
    }

    /** ㎡ → 평 (반올림하지 않은 원값). */
    fun toPyeong(areaM2: Double): Double = areaM2 * PYEONG_PER_SQUARE_METER

    /** `"84.97㎡"` — 소수점 2자리, 불필요한 0 은 제거. */
    fun formatM2(areaM2: Double): String = "${trimDecimals(areaM2, 2)}㎡"

    /** `"25.7평"` — 소수점 1자리. */
    fun formatPyeong(areaM2: Double): String = "${trimDecimals(toPyeong(areaM2), 1)}평"

    /**
     * 목록 카드용 병기 표기.
     * 예) `84.97㎡ (25.7평)`
     */
    fun formatWithPyeong(areaM2: Double): String = "${formatM2(areaM2)} (${formatPyeong(areaM2)})"

    /**
     * 단지 상세의 평형 선택 칩 라벨.
     * 같은 단지 안에서 평형을 구분하는 용도이므로 정수 평으로 반올림한다.
     * 예) 84.97㎡ → `"26평"`
     *
     * 주의: 여기서 말하는 평은 **전용면적 기준**이다.
     * 시장에서 흔히 쓰는 "34평"은 공급면적(전용 + 주거공용) 기준이라 값이 다르다.
     * (전용 84.97㎡ = 전용 26평 ≈ 시장 통칭 34평)
     * 국토교통부 실거래가 API 는 전용면적만 제공하므로 공급면적은 알 수 없다.
     * 시장 통칭 평형을 맞추겠다고 공급면적을 임의로 추정해 만들어 내지 말 것.
     */
    fun formatPyeongChip(areaM2: Double): String = "${toPyeong(areaM2).roundToInt()}평"

    /** 평형대 분류. 필터 칩(소형/중형/대형)에 사용한다. */
    fun bucketOf(areaM2: Double): AreaBucket = AreaBucket.of(areaM2)

    private fun trimDecimals(value: Double, decimals: Int): String {
        val text = String.format(java.util.Locale.US, "%.${decimals}f", value)
        return if (text.contains('.')) text.trimEnd('0').trimEnd('.') else text
    }
}

/**
 * 전용면적 기준 평형대.
 *
 * 경계값은 임의로 정한 값이 아니라 국내 주택 제도의 기준을 따른다.
 * - 60㎡: 「주택법 시행령」상 소형주택 판단 기준으로 통용되는 면적
 * - 85㎡: 국민주택규모(전용 85㎡ 이하)
 */
enum class AreaBucket(val label: String, val description: String) {
    /** 전용 60㎡ 미만 (약 18평 미만) */
    SMALL("소형", "전용 60㎡ 미만"),

    /** 전용 60㎡ 이상 85㎡ 이하 — 국민주택규모 */
    MEDIUM("중형", "전용 60㎡ ~ 85㎡"),

    /** 전용 85㎡ 초과 */
    LARGE("대형", "전용 85㎡ 초과"),
    ;

    companion object {
        private const val SMALL_MAX_EXCLUSIVE = 60.0
        private const val MEDIUM_MAX_INCLUSIVE = 85.0

        fun of(areaM2: Double): AreaBucket = when {
            areaM2 < SMALL_MAX_EXCLUSIVE -> SMALL
            areaM2 <= MEDIUM_MAX_INCLUSIVE -> MEDIUM
            else -> LARGE
        }
    }
}
