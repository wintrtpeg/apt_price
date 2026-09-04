package com.aptprice.tracker.core.format

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * 국토교통부 실거래가 API 의 금액 필드는 모두 **만원 단위 문자열**(천 단위 콤마 포함)이다.
 * 예) 거래금액 `"87,500"` → 8억 7,500만원 / 보증금 `"50,000"` → 5억
 *
 * 앱 내부에서는 이 값을 `Long` 만원 단위로만 다루고, 표시 직전에만 문자열로 바꾼다.
 * 파싱에 실패하면 `null` 을 반환한다. **추정치를 대신 채워 넣지 않는다.**
 */
object MoneyFormatter {

    private const val MAN_PER_EOK = 10_000L

    /**
     * API 응답의 금액 문자열을 만원 단위 [Long] 으로 변환한다.
     *
     * 콤마·공백·비가시 공백을 제거한 뒤 숫자만 남는 경우에만 값을 돌려주며,
     * 빈 값이거나 숫자가 아니면 `null` 을 반환해 "거래 데이터 없음" 으로 처리하게 한다.
     */
    fun parseManwon(raw: String?): Long? {
        if (raw == null) return null
        val cleaned = raw.trim().replace(",", "").replace(" ", "").replace(" ", "")
        if (cleaned.isEmpty()) return null
        val negative = cleaned.startsWith("-")
        val digits = if (negative) cleaned.substring(1) else cleaned
        if (digits.isEmpty() || digits.any { !it.isDigit() }) return null
        val value = digits.toLongOrNull() ?: return null
        return if (negative) -value else value
    }

    /**
     * 만원 단위 금액을 한국식 억/만원 표기로 변환한다.
     *
     * - `87_500` → `"8억 7,500만원"`
     * - `80_000` → `"8억"`
     * - `5_000`  → `"5,000만원"`
     * - `0`      → `"0원"`
     * - `-3_000` → `"-3,000만원"`
     */
    fun formatManwon(manwon: Long): String {
        if (manwon == 0L) return "0원"
        val sign = if (manwon < 0) "-" else ""
        val absValue = abs(manwon)
        val eok = absValue / MAN_PER_EOK
        val man = absValue % MAN_PER_EOK
        return when {
            eok > 0L && man > 0L -> "$sign${withComma(eok)}억 ${withComma(man)}만원"
            eok > 0L -> "$sign${withComma(eok)}억"
            else -> "$sign${withComma(man)}만원"
        }
    }

    /** 파싱 실패(null)를 안전하게 처리하는 표시용 헬퍼. */
    fun formatManwonOrNull(manwon: Long?): String? = manwon?.let(::formatManwon)

    /**
     * 차트 축·배지처럼 폭이 좁은 곳을 위한 축약 표기.
     *
     * - `87_500` → `"8.8억"`
     * - `120_000` → `"12억"`
     * - `7_500` → `"7,500만"`
     */
    fun formatCompact(manwon: Long, decimals: Int = 1): String {
        if (manwon == 0L) return "0"
        val sign = if (manwon < 0) "-" else ""
        val absValue = abs(manwon)
        if (absValue < MAN_PER_EOK) return "$sign${withComma(absValue)}만"

        val scale = when {
            decimals <= 0 -> 1L
            else -> generateSequence(1L) { it * 10 }.elementAt(decimals)
        }
        val scaled = (absValue.toDouble() / MAN_PER_EOK * scale).roundToLong()
        val whole = scaled / scale
        val fraction = scaled % scale
        return if (fraction == 0L) {
            "$sign${withComma(whole)}억"
        } else {
            val fractionText = fraction.toString().padStart(decimals, '0').trimEnd('0')
            "$sign${withComma(whole)}.${fractionText}억"
        }
    }

    /**
     * 월세 표기. 보증금과 월세액을 분리해서 보여준다.
     * 예) 보증금 10,000만원 / 월세 120만원 → `"1억 / 120만원"`
     */
    fun formatMonthlyRent(depositManwon: Long, monthlyManwon: Long): String =
        "${formatManwon(depositManwon)} / ${formatManwon(monthlyManwon)}"

    /**
     * 직전 거래 대비 등락률. 기준값이 0 이거나 없으면 `null` (= 비교 불가, 표시하지 않음).
     */
    fun changeRatePercent(currentManwon: Long, previousManwon: Long?): Double? {
        if (previousManwon == null || previousManwon == 0L) return null
        return (currentManwon - previousManwon).toDouble() / previousManwon * 100.0
    }

    /** 등락률 표기. 예) `+3.4%`, `-1.2%`, `0.0%` */
    fun formatChangeRate(percent: Double): String {
        val rounded = (percent * 10).roundToLong() / 10.0
        val sign = when {
            rounded > 0 -> "+"
            else -> ""
        }
        return "$sign${String.format(java.util.Locale.US, "%.1f", rounded)}%"
    }

    private fun withComma(value: Long): String {
        val text = value.toString()
        if (text.length <= 3) return text
        return text.reversed()
            .chunked(3)
            .joinToString(",")
            .reversed()
    }
}
