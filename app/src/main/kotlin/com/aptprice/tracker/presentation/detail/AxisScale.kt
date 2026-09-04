package com.aptprice.tracker.presentation.detail

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * 축 눈금을 사람이 읽기 좋은 값으로 고른다.
 *
 * 실제 데이터 범위를 그대로 쓰면 `8억 7,532만원` 같은 눈금이 나온다.
 * 1 / 2 / 2.5 / 5 의 배수로 올림·내림해서 읽을 수 있는 값으로 만든다.
 */
object AxisScale {

    private const val DEFAULT_TICK_COUNT = 5

    /** 눈금 값 목록 (오름차순). 아래·위로 데이터 범위를 감싼다. */
    fun niceTicks(
        min: Long,
        max: Long,
        tickCount: Int = DEFAULT_TICK_COUNT,
    ): List<Long> {
        require(tickCount >= 2) { "눈금은 2개 이상이어야 합니다: $tickCount" }

        // 모든 거래가 같은 금액이면 위아래로 약간의 여백을 만든다.
        if (min == max) {
            val pad = (abs(min).takeIf { it > 0 } ?: 1L).coerceAtLeast(1L) / 10
            return listOf(min - pad, min, min + pad)
        }

        val step = niceNumber((max - min).toDouble() / (tickCount - 1), round = true)
        if (step <= 0.0) return listOf(min, max)

        val niceMin = floor(min / step) * step
        val niceMax = ceil(max / step) * step

        val ticks = mutableListOf<Long>()
        var value = niceMin
        // 부동소수 오차로 마지막 눈금이 빠지지 않도록 반 칸 여유를 둔다.
        while (value <= niceMax + step * 0.5) {
            ticks += value.toLong()
            value += step
        }
        return ticks
    }

    /**
     * 1 / 2 / 2.5 / 5 × 10^n 중에서 [value] 에 가까운 값을 고른다.
     * 축 눈금 간격을 정하는 표준적인 방법이다.
     */
    private fun niceNumber(value: Double, round: Boolean): Double {
        if (value <= 0.0) return 0.0
        val exponent = floor(log10(value))
        val fraction = value / 10.0.pow(exponent)

        val niceFraction = if (round) {
            when {
                fraction < 1.5 -> 1.0
                fraction < 3.0 -> 2.0
                fraction < 7.0 -> 5.0
                else -> 10.0
            }
        } else {
            when {
                fraction <= 1.0 -> 1.0
                fraction <= 2.0 -> 2.0
                fraction <= 5.0 -> 5.0
                else -> 10.0
            }
        }
        return niceFraction * 10.0.pow(exponent)
    }

    /** 값을 0..1 로 정규화한다. 범위가 0이면 가운데(0.5)에 둔다. */
    fun normalize(value: Long, min: Long, max: Long): Float =
        if (max == min) 0.5f else ((value - min).toDouble() / (max - min)).toFloat()
}
