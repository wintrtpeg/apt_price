package com.aptprice.tracker.presentation.feed

import com.aptprice.tracker.core.format.MoneyFormatter
import com.aptprice.tracker.domain.model.DealTab

/**
 * 카드 한 장에 들어가는 미니 추이 그래프.
 *
 * **그 단지·그 평형의 실제 신고 건**을 오래된 순으로 이은 것이다. 가로축은 시간이 아니라
 * **거래 순서**다. 카드만 한 폭에 시간축을 넣으면 몇 년 전 한 건과 최근 몇 건이 한쪽에
 * 뭉쳐 아무것도 읽히지 않는다. 순서축이라는 사실은 [caption] 에 적어 화면에도 밝힌다.
 *
 * 점이 2개 미만이면 만들지 않는다 ([of] 가 null 을 준다). 한 점으로는 추이가 아니고,
 * 선을 그으려면 없는 거래를 지어내야 한다.
 */
data class Sparkline(
    /** 0~1 로 옮긴 세로 위치. 오래된 순. */
    val points: List<Float>,
    /** 처음 대비 마지막의 방향 */
    val direction: ChangeDirection,
    /** 예) `최근 5건 · 거래순`. 무엇을 몇 건 이었는지 밝히는 자리다. */
    val caption: String,
) {
    val pointCount: Int get() = points.size
}

object SparklineBuilder {

    /** 카드에 넣을 최대 점 수. 이보다 많으면 최근 것만 남긴다. */
    const val MAX_POINTS = 8

    /** 선을 그리려면 최소한 이만큼은 있어야 한다. */
    const val MIN_POINTS = 2

    /**
     * 오래된 순으로 정렬된 금액 목록을 그래프로 접는다.
     *
     * @param amountsOldestFirst 해제되지 않은 실제 신고 금액. 호출부가 정렬해서 넘긴다.
     * @param amountLabel 금액이 무엇인지 밝혀야 할 때 (월세 탭의 `보증금`). 매매·전세는 null.
     */
    fun of(amountsOldestFirst: List<Long>, amountLabel: String? = null): Sparkline? {
        val recent = amountsOldestFirst.takeLast(MAX_POINTS)
        if (recent.size < MIN_POINTS) return null

        val lowest = recent.min()
        val highest = recent.max()
        val span = highest - lowest

        val points = recent.map { amount ->
            // 값이 전부 같으면 가운데 놓는다. 0 으로 두면 바닥에 붙어 값이 낮은 것처럼
            // 보이지만, 사실은 변동이 없는 것이다.
            if (span <= 0L) 0.5f else ((amount - lowest).toDouble() / span).toFloat()
        }

        val percent = MoneyFormatter.changeRatePercent(recent.last(), recent.first())

        return Sparkline(
            points = points,
            direction = directionOf(percent),
            // 등락률은 적지 않는다. 카드의 등락 배지는 **직전 거래 대비**라 여기(최근 N건
            // 처음 대비 마지막)와 기준이 달라, 나란히 놓으면 서로 다른 두 숫자가 된다.
            caption = buildString {
                amountLabel?.let { append(it).append(' ') }
                append("최근 ${recent.size}건 · 거래순")
            },
        )
    }

    /**
     * 키별 금액 흐름을 카드가 바로 쓸 수 있는 그래프로 접는다.
     * 점이 모자란 키는 아예 빠진다 — 카드가 그래프 자리를 비우기 위한 것이다.
     */
    fun build(seriesByKey: Map<String, List<Long>>, tab: DealTab): Map<String, Sparkline> {
        val label = amountLabelFor(tab)
        return seriesByKey.mapNotNull { (key, amounts) ->
            of(amounts, label)?.let { key to it }
        }.toMap()
    }

    /** 월세 탭의 그래프는 보증금 흐름이다. 밝혀 두지 않으면 월세로 읽힌다. */
    fun amountLabelFor(tab: DealTab): String? = if (tab == DealTab.MONTHLY) "보증금" else null

    private fun directionOf(percent: Double?): ChangeDirection = when {
        percent == null -> ChangeDirection.NONE
        percent > 0.0 -> ChangeDirection.UP
        percent < 0.0 -> ChangeDirection.DOWN
        else -> ChangeDirection.FLAT
    }
}
