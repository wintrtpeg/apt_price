package com.aptprice.tracker.presentation.feed

import com.aptprice.tracker.core.format.AreaBucket
import com.aptprice.tracker.core.time.TradePeriod
import com.aptprice.tracker.domain.model.DealTab
import com.aptprice.tracker.domain.region.RegionCatalog
import com.aptprice.tracker.domain.region.RegionGroup

/** 목록 정렬 기준. */
enum class FeedSort(val label: String) {
    LATEST("최신 거래순"),
    PRICE_DESC("높은 금액순"),
    PRICE_ASC("낮은 금액순"),
}

/**
 * 지역 선택 상태.
 *
 * 선택한 시군구 코드를 그대로 들고 있는다. 이 값이 곧 조회 대상이 되므로
 * 지역을 좁히면 API 호출량도 함께 줄어든다.
 */
@JvmInline
value class RegionSelection(val lawdCodes: Set<String>) {

    val isAll: Boolean get() = lawdCodes.size == RegionCatalog.all.size

    val isEmpty: Boolean get() = lawdCodes.isEmpty()

    /** 조회에 쓸 코드 목록. 카탈로그 순서를 지켜 안정적인 결과를 만든다. */
    fun codes(): List<String> = RegionCatalog.lawdCodes.filter { it in lawdCodes }

    fun contains(lawdCd: String): Boolean = lawdCd in lawdCodes

    /** 그룹 전체가 선택되어 있는가. */
    fun isGroupFullySelected(group: RegionGroup): Boolean {
        val groupCodes = RegionCatalog.byGroup(group).map { it.lawdCd }
        return groupCodes.isNotEmpty() && lawdCodes.containsAll(groupCodes)
    }

    /** 그룹 중 하나라도 선택되어 있는가. */
    fun isGroupPartiallySelected(group: RegionGroup): Boolean =
        RegionCatalog.byGroup(group).any { it.lawdCd in lawdCodes }

    fun toggleRegion(lawdCd: String): RegionSelection =
        if (lawdCd in lawdCodes) {
            RegionSelection(lawdCodes - lawdCd)
        } else {
            RegionSelection(lawdCodes + lawdCd)
        }

    /** 그룹 전체를 켜거나 끈다. 이미 전부 선택되어 있으면 전부 끈다. */
    fun toggleGroup(group: RegionGroup): RegionSelection {
        val groupCodes = RegionCatalog.byGroup(group).map { it.lawdCd }.toSet()
        return if (isGroupFullySelected(group)) {
            RegionSelection(lawdCodes - groupCodes)
        } else {
            RegionSelection(lawdCodes + groupCodes)
        }
    }

    /** 화면 상단에 띄울 요약. 예) `전체` / `서울 전체` / `강남구 외 2곳` */
    fun summaryLabel(): String {
        if (isAll) return "전체"
        if (isEmpty) return "지역 없음"

        val selectedGroups = RegionGroup.entries.filter { isGroupFullySelected(it) }
        val groupCodes = selectedGroups.flatMap { RegionCatalog.byGroup(it).map { r -> r.lawdCd } }.toSet()
        val leftovers = codes().filterNot { it in groupCodes }

        val parts = selectedGroups.map { "${it.label} 전체" }
        return when {
            leftovers.isEmpty() -> parts.joinToString(" · ")
            parts.isEmpty() -> leftoverLabel(leftovers)
            else -> (parts + leftoverLabel(leftovers)).joinToString(" · ")
        }
    }

    private fun leftoverLabel(leftovers: List<String>): String {
        val first = RegionCatalog.byLawdCd(leftovers.first())?.displayName ?: leftovers.first()
        return if (leftovers.size == 1) first else "$first 외 ${leftovers.size - 1}곳"
    }

    companion object {
        /** 아무 지역도 고르지 않은 상태. 앱을 처음 켰을 때의 기본값이다. */
        fun none(): RegionSelection = RegionSelection(emptySet())

        /** 대상 지역 전체. */
        fun all(): RegionSelection = RegionSelection(RegionCatalog.lawdCodes.toSet())

        fun ofGroups(vararg groups: RegionGroup): RegionSelection =
            RegionSelection(groups.flatMap { RegionCatalog.byGroup(it).map { r -> r.lawdCd } }.toSet())
    }
}

/** 메인 피드의 조회·표시 조건. */
data class FeedFilter(
    val period: TradePeriod = TradePeriod.DEFAULT,
    val tab: DealTab = DealTab.SALE,
    /**
     * 조회할 지역. 기본은 **선택 안 함** 이다.
     *
     * 전체 36개 지역을 기본으로 두었더니 앱을 켜자마자 수십~수천 회를 조회해
     * 공공데이터포털이 429(Too Many Requests) 로 막았다. 볼 지역을 먼저 고르게 한다.
     */
    val regions: RegionSelection = RegionSelection.none(),
    /** 비어 있으면 평형대 제한 없음. */
    val areaBuckets: Set<AreaBucket> = emptySet(),
    val sort: FeedSort = FeedSort.LATEST,
    /** 계약 해제 건을 목록에 남길지. 기본은 남기되 취소선으로 표시한다. */
    val includeCanceled: Boolean = true,
) {
    fun toggleAreaBucket(bucket: AreaBucket): FeedFilter =
        copy(areaBuckets = if (bucket in areaBuckets) areaBuckets - bucket else areaBuckets + bucket)

    /** 평형대 필터가 이 면적을 통과시키는가. */
    fun acceptsBucket(bucket: AreaBucket): Boolean =
        areaBuckets.isEmpty() || bucket in areaBuckets
}
