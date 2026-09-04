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
     *
     * 여기서의 평은 전용면적을 그대로 환산한 값이다. 시장에서 부르는 평형대와는
     * 기준이 다르므로, 화면에는 [formatWithBucket] 을 쓴다.
     */
    fun formatWithPyeong(areaM2: Double): String = "${formatM2(areaM2)} (${formatPyeong(areaM2)})"

    /**
     * 화면에 쓰는 면적 표기. 원자료 그대로의 전용면적과 시장 호칭을 함께 보여 준다.
     * 예) `84.97㎡ · 30평대`
     */
    fun formatWithBucket(areaM2: Double): String =
        "${formatM2(areaM2)} · ${AreaBucket.of(areaM2).label}"

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
 * 평형대. 시장에서 아파트를 부르는 이름(20평대·30평대…)에 맞춘 구간이다.
 *
 * ## 구간을 전용면적으로 정한 이유
 * 사람들이 말하는 "30평대"는 공급면적(전용 + 주거공용) 기준 호칭이다. 반면 국토교통부
 * 실거래가 자료에는 **전용면적만 있고 공급면적이 없다.** 공급면적을 추정해 만들어 내는
 * 것은 이 앱의 원칙에 어긋나므로, 대신 각 호칭에 해당하는 **전용면적 구간**을 직접 잡았다.
 *
 * 널리 쓰이는 대응 관계를 따른다.
 * - 전용 59㎡ → 20평대
 * - 전용 84㎡ → 30평대  (이른바 국민평형)
 * - 전용 114㎡ → 40평대
 *
 * 화면에는 언제나 원자료 그대로의 전용면적(㎡)을 함께 표시한다.
 */
enum class AreaBucket(val label: String, val description: String) {
    /** 전용 50㎡ 미만 */
    UNDER_20("10평대 이하", "전용 50㎡ 미만"),

    /** 전용 50㎡ 이상 66㎡ 미만 — 통상 전용 59㎡ */
    PYEONG_20("20평대", "전용 50 ~ 66㎡"),

    /** 전용 66㎡ 이상 99㎡ 미만 — 통상 전용 84㎡ (국민평형) */
    PYEONG_30("30평대", "전용 66 ~ 99㎡"),

    /** 전용 99㎡ 이상 132㎡ 미만 — 통상 전용 114㎡ */
    PYEONG_40("40평대", "전용 99 ~ 132㎡"),

    /** 전용 132㎡ 이상 */
    OVER_50("50평대 이상", "전용 132㎡ 이상"),
    ;

    companion object {
        private const val PYEONG_20_MIN = 50.0
        private const val PYEONG_30_MIN = 66.0
        private const val PYEONG_40_MIN = 99.0
        private const val OVER_50_MIN = 132.0

        fun of(areaM2: Double): AreaBucket = when {
            areaM2 < PYEONG_20_MIN -> UNDER_20
            areaM2 < PYEONG_30_MIN -> PYEONG_20
            areaM2 < PYEONG_40_MIN -> PYEONG_30
            areaM2 < OVER_50_MIN -> PYEONG_40
            else -> OVER_50
        }
    }
}
