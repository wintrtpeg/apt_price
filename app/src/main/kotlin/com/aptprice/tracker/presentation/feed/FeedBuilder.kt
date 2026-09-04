package com.aptprice.tracker.presentation.feed

import com.aptprice.tracker.core.format.AreaFormatter
import com.aptprice.tracker.core.format.DateFormatter
import com.aptprice.tracker.core.format.MoneyFormatter
import com.aptprice.tracker.domain.model.AptDeal
import com.aptprice.tracker.domain.model.AptRent
import com.aptprice.tracker.domain.model.AptTrade
import com.aptprice.tracker.domain.region.RegionCatalog
import java.time.LocalDate

/**
 * 실거래 목록을 피드 카드로 바꾼다. 필터·정렬·등락률 계산이 모두 여기 모여 있다.
 *
 * ## 등락률에 대하여
 * "직전 대비" 는 **같은 단지의 같은 전용면적** 에서 바로 앞선 거래와 비교한 값이다.
 * 비교할 앞선 거래가 화면에 불러온 데이터 안에 없으면 등락률을 표시하지 않는다(`null`).
 * 없는 기준값을 추정해서 만들어 내지 않기 위한 것이다.
 * 계약이 해제된 거래는 성사되지 않은 가격이므로 비교 기준으로 쓰지 않는다.
 */
object FeedBuilder {

    fun build(
        deals: List<AptDeal>,
        filter: FeedFilter,
        today: LocalDate,
    ): List<FeedItemUi> {
        val visible = deals.filter { deal ->
            filter.acceptsBucket(deal.areaBucket) && (filter.includeCanceled || !deal.canceled)
        }

        val baselines = previousAmounts(visible)

        return visible
            .map { deal -> deal.toUi(baselines[deal.identity()], today) }
            .sortedWith(filter.sort.comparator())
    }

    /**
     * 각 거래의 "직전 거래 금액" 을 미리 계산한다.
     *
     * 같은 단지·평형끼리 묶어 계약일 오름차순으로 훑으면서, 바로 앞 거래의 금액을 물려준다.
     * 해제된 계약은 기준값으로 삼지 않고 건너뛴다.
     */
    private fun previousAmounts(deals: List<AptDeal>): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        deals.groupBy { it.complexAreaKey }.forEach { (_, group) ->
            var previous: Long? = null
            group
                .sortedWith(compareBy({ it.dealDate }, { it.comparableAmount() }))
                .forEach { deal ->
                    previous?.let { result[deal.identity()] = it }
                    // 해제된 계약은 실제로 성사되지 않았으므로 다음 거래의 기준이 되지 않는다.
                    if (!deal.canceled) previous = deal.comparableAmount()
                }
        }
        return result
    }

    private fun AptDeal.toUi(previousAmount: Long?, today: LocalDate): FeedItemUi {
        val region = RegionCatalog.byLawdCd(lawdCd)
        val changePercent = MoneyFormatter.changeRatePercent(comparableAmount(), previousAmount)

        return FeedItemUi(
            id = identity(),
            complexAreaKey = complexAreaKey,
            aptName = aptName,
            regionLabel = listOfNotNull(region?.displayName, umdNm).joinToString(" "),
            areaLabel = AreaFormatter.formatWithPyeong(exclusiveAreaM2),
            areaBucketLabel = areaBucket.label,
            floorLabel = floor?.let { "${it}층" },
            dateLabel = DateFormatter.formatFeedDate(dealDate),
            relativeDateLabel = DateFormatter.formatRelativeDay(dealDate, today),
            priceLabel = priceLabel(),
            priceSubLabel = priceSubLabel(),
            changeLabel = changePercent?.let(MoneyFormatter::formatChangeRate),
            changeDirection = directionOf(changePercent),
            buildYearLabel = buildYear?.let { "${it}년 준공" },
            canceled = canceled,
            dealDateEpochDay = dealDate.toEpochDay(),
            sortAmountManwon = comparableAmount(),
        )
    }

    /**
     * 정렬·비교에 쓰는 대표 금액.
     * 매매는 거래금액, 전세는 보증금, 월세는 보증금(월세액은 따로 표기)을 쓴다.
     */
    private fun AptDeal.comparableAmount(): Long = when (this) {
        is AptTrade -> dealAmountManwon
        is AptRent -> depositManwon
    }

    private fun AptDeal.priceLabel(): String = when (this) {
        is AptTrade -> MoneyFormatter.formatManwon(dealAmountManwon)
        is AptRent -> if (isJeonse) {
            MoneyFormatter.formatManwon(depositManwon)
        } else {
            MoneyFormatter.formatMonthlyRent(depositManwon, monthlyRentManwon)
        }
    }

    /** 월세는 보증금/월세를 한 번 더 풀어 준다. 그 외에는 없음. */
    private fun AptDeal.priceSubLabel(): String? = when {
        this is AptRent && !isJeonse ->
            "보증금 ${MoneyFormatter.formatManwon(depositManwon)} · " +
                "월세 ${MoneyFormatter.formatManwon(monthlyRentManwon)}"
        else -> null
    }

    /**
     * 목록 키. 국토부 자료에는 행 ID 가 없어 값들로 만든다.
     * 같은 날 같은 단지·평형·층·금액이면 같은 거래로 본다.
     */
    private fun AptDeal.identity(): String = buildString {
        append(complexAreaKey)
        append('|').append(dealDate)
        append('|').append(floor ?: "-")
        append('|').append(comparableAmount())
        if (this@identity is AptRent) append('|').append(monthlyRentManwon)
        if (canceled) append("|X")
    }

    private fun directionOf(percent: Double?): ChangeDirection = when {
        percent == null -> ChangeDirection.NONE
        percent > 0.0 -> ChangeDirection.UP
        percent < 0.0 -> ChangeDirection.DOWN
        else -> ChangeDirection.FLAT
    }

    private fun FeedSort.comparator(): Comparator<FeedItemUi> = when (this) {
        FeedSort.LATEST -> compareByDescending<FeedItemUi> { it.dealDateEpochDay }
            .thenByDescending { it.sortAmountManwon }
            .thenBy { it.aptName }

        FeedSort.PRICE_DESC -> compareByDescending<FeedItemUi> { it.sortAmountManwon }
            .thenByDescending { it.dealDateEpochDay }
            .thenBy { it.aptName }

        FeedSort.PRICE_ASC -> compareBy<FeedItemUi> { it.sortAmountManwon }
            .thenByDescending { it.dealDateEpochDay }
            .thenBy { it.aptName }
    }
}
