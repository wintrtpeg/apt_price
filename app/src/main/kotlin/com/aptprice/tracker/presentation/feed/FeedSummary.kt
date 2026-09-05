package com.aptprice.tracker.presentation.feed

import com.aptprice.tracker.core.format.MoneyFormatter
import com.aptprice.tracker.core.time.TradePeriod
import com.aptprice.tracker.domain.model.DealTab
import java.time.LocalDate
import kotlin.math.ceil

/**
 * 피드 맨 위 요약의 한 구간.
 *
 * 기간을 같은 길이로 쪼갠 칸 하나다. 막대 높이는 그 칸에 신고된 건수, 선의 높이는 중간가다.
 */
data class FeedBucket(
    val startDate: LocalDate,
    val endDate: LocalDate,
    /** 이 구간에 신고된 거래 수 (해제 건 제외) */
    val count: Int,
    /** 이 구간의 중간가. 거래가 없으면 null — 선을 잇지 않는다. */
    val medianManwon: Long?,
    /** 막대 높이 (0~1). 최대 건수를 1로 본다. */
    val countRatio: Float,
    /** 선의 높이 (0~1). 거래가 없으면 null. */
    val priceRatio: Float?,
)

/**
 * 피드 맨 위에 얹는 요약.
 *
 * 지금 보고 있는 (지역 × 기간 × 탭) 안에서 **실제로 신고된 거래만** 집계한다.
 * 평균이 아니라 중간가를 쓴다 — 한 건의 초고가 거래가 전체를 끌어올리면
 * 시세를 잘못 읽게 되기 때문이다.
 *
 * 해제된 계약은 성사되지 않은 가격이므로 금액 통계에서 뺀다. 대신 몇 건이 해제됐는지는
 * 따로 알린다 — 신고된 사실 자체는 원자료에 있기 때문이다.
 */
data class FeedSummary(
    /** 금액 통계에 들어간 거래 수 (해제 건 제외) */
    val dealCount: Int,
    /** 그중 해제되어 통계에서 뺀 건수 */
    val canceledCount: Int,
    val medianManwon: Long?,
    val maxManwon: Long?,
    val minManwon: Long?,
    val buckets: List<FeedBucket>,
    val tab: DealTab,
    val period: TradePeriod,
) {
    /** 막대를 그릴 만한가. 한 칸에만 거래가 있으면 추이라고 할 수 없다. */
    val hasChart: Boolean get() = buckets.count { it.count > 0 } >= MIN_BUCKETS_FOR_CHART

    /** 예) `중간가` / 월세 탭에서는 보증금 기준임을 밝힌다. */
    val medianCaption: String
        get() = if (tab == DealTab.MONTHLY) "보증금 중간값" else "중간가"

    val medianLabel: String? get() = medianManwon?.let(MoneyFormatter::formatManwon)
    val maxLabel: String? get() = maxManwon?.let(MoneyFormatter::formatManwon)
    val minLabel: String? get() = minManwon?.let(MoneyFormatter::formatManwon)

    /** 예) `최근 2주 · 매매 128건` */
    val headline: String get() = "${period.label} · ${tab.label} ${dealCount}건"

    private companion object {
        const val MIN_BUCKETS_FOR_CHART = 2
    }
}

/**
 * 피드 목록을 요약으로 접는다.
 *
 * 이미 화면에 보이는 목록([FeedItemUi])만 재료로 쓴다. 그래야 지역·평형대·해제 숨김 같은
 * 필터가 목록과 요약에서 어긋나지 않는다. 새로 조회하지 않으므로 비용도 없다.
 */
object FeedSummaryBuilder {

    /** 구간을 이 개수에 가깝게 나눈다. 폭이 좁은 화면에서도 막대가 뭉개지지 않는 선. */
    private const val TARGET_BUCKETS = 12

    fun build(
        items: List<FeedItemUi>,
        period: TradePeriod,
        tab: DealTab,
        today: LocalDate,
    ): FeedSummary? {
        if (items.isEmpty()) return null

        // 해제 건은 성사되지 않은 가격이라 금액 통계에서 뺀다.
        val settled = items.filterNot { it.canceled }
        val amounts = settled.map { it.sortAmountManwon }

        val range = period.range(today)
        val buckets = bucketize(settled, range.start, range.endInclusive)

        return FeedSummary(
            dealCount = settled.size,
            canceledCount = items.size - settled.size,
            medianManwon = amounts.medianOrNull(),
            maxManwon = amounts.maxOrNull(),
            minManwon = amounts.minOrNull(),
            buckets = buckets,
            tab = tab,
            period = period,
        )
    }

    private fun bucketize(
        items: List<FeedItemUi>,
        from: LocalDate,
        to: LocalDate,
    ): List<FeedBucket> {
        val totalDays = (to.toEpochDay() - from.toEpochDay() + 1).coerceAtLeast(1)
        val bucketDays = ceil(totalDays.toDouble() / TARGET_BUCKETS).toLong().coerceAtLeast(1)
        val bucketCount = ceil(totalDays.toDouble() / bucketDays).toInt().coerceAtLeast(1)

        val grouped = items.groupBy { item ->
            val offset = item.dealDateEpochDay - from.toEpochDay()
            // 구간 밖의 값은 양끝 칸으로 몰아 넣는다. 목록과 건수가 어긋나지 않게.
            (offset / bucketDays).coerceIn(0L, (bucketCount - 1).toLong()).toInt()
        }

        val raw = (0 until bucketCount).map { index ->
            val start = from.plusDays(index * bucketDays)
            val end = minOf(start.plusDays(bucketDays - 1), to)
            val inBucket = grouped[index].orEmpty()
            Triple(start to end, inBucket.size, inBucket.map { it.sortAmountManwon }.medianOrNull())
        }

        val maxCount = raw.maxOf { it.second }
        val medians = raw.mapNotNull { it.third }
        val lowest = medians.minOrNull()
        val highest = medians.maxOrNull()

        return raw.map { (dates, count, median) ->
            FeedBucket(
                startDate = dates.first,
                endDate = dates.second,
                count = count,
                medianManwon = median,
                countRatio = if (maxCount == 0) 0f else count.toFloat() / maxCount,
                priceRatio = median?.let { ratioOf(it, lowest, highest) },
            )
        }
    }

    /**
     * 중간가를 0~1 로 옮긴다.
     *
     * 값이 하나뿐이거나 전부 같으면 가운데(0.5)에 둔다. 0 으로 두면 바닥에 붙어
     * 값이 낮은 것처럼 보이는데, 사실은 비교할 대상이 없는 것이다.
     */
    private fun ratioOf(value: Long, lowest: Long?, highest: Long?): Float {
        if (lowest == null || highest == null) return 0.5f
        val span = highest - lowest
        if (span <= 0L) return 0.5f
        return ((value - lowest).toDouble() / span).toFloat()
    }

    /**
     * 중간값. 평균을 쓰지 않는 이유는 [FeedSummary] 주석에 적었다.
     * 짝수 개면 가운데 두 값의 평균으로 한다.
     */
    private fun List<Long>.medianOrNull(): Long? {
        if (isEmpty()) return null
        val sorted = sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[mid]
        } else {
            (sorted[mid - 1] + sorted[mid]) / 2
        }
    }
}
