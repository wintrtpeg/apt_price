package com.aptprice.tracker.presentation.feed

import com.aptprice.tracker.core.time.TradePeriod
import com.aptprice.tracker.core.time.TradeQueryPlan
import com.aptprice.tracker.domain.model.AptDeal
import com.aptprice.tracker.domain.model.AptRent
import com.aptprice.tracker.domain.model.AptTrade
import com.aptprice.tracker.domain.model.ComplexSummary
import com.aptprice.tracker.domain.model.DealTab
import com.aptprice.tracker.domain.repository.SyncProgress
import com.aptprice.tracker.domain.repository.SyncReport
import com.aptprice.tracker.domain.repository.TradeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

/** ViewModel 상태 기계를 검증하기 위한 가짜 저장소. */
class FakeTradeRepository(
    private val deals: MutableStateFlow<List<AptDeal>> = MutableStateFlow(emptyList()),
) : TradeRepository {

    /** sync 가 어떤 계획으로 몇 번 불렸는지 */
    val syncedPlans = mutableListOf<TradeQueryPlan>()

    /** observeDeals 가 어떤 조건으로 불렸는지 */
    val observedQueries = mutableListOf<Triple<TradePeriod, List<String>, DealTab>>()

    var report: SyncReport = SyncReport(
        planned = 0,
        skippedFresh = 0,
        fetched = 0,
        storedRows = 0,
        parseFailures = 0,
        failures = emptyList(),
        abortedBy = null,
    )
    var fetchedAt: Instant? = null

    fun emit(items: List<AptDeal>) {
        deals.value = items
    }

    override fun observeDeals(
        period: TradePeriod,
        lawdCodes: List<String>,
        tab: DealTab,
    ): Flow<List<AptDeal>> {
        observedQueries += Triple(period, lawdCodes, tab)
        return deals.map { list ->
            list.filter {
                when (tab) {
                    DealTab.SALE -> it is AptTrade
                    DealTab.JEONSE -> it is AptRent && it.isJeonse
                    DealTab.MONTHLY -> it is AptRent && !it.isJeonse
                }
            }
        }
    }

    override fun observeTradeSeries(complexAreaKey: String, period: TradePeriod): Flow<List<AptTrade>> =
        deals.map { list -> list.filterIsInstance<AptTrade>().filter { it.complexAreaKey == complexAreaKey } }

    override fun observeJeonseSeries(complexAreaKey: String, period: TradePeriod): Flow<List<AptRent>> =
        deals.map { list ->
            list.filterIsInstance<AptRent>().filter { it.complexAreaKey == complexAreaKey && it.isJeonse }
        }

    /** observeAmountSeries 가 어떤 키·탭으로 불렸는지 (카드마다 조회하지 않는지 확인용) */
    val observedSeriesQueries = mutableListOf<Pair<List<String>, DealTab>>()

    override fun observeAmountSeries(
        complexAreaKeys: List<String>,
        tab: DealTab,
    ): Flow<Map<String, List<Long>>> {
        observedSeriesQueries += complexAreaKeys to tab
        val keys = complexAreaKeys.toSet()
        return deals.map { list ->
            list.asSequence()
                .filter { it.complexAreaKey in keys }
                .filter {
                    when (tab) {
                        DealTab.SALE -> it is AptTrade && !it.canceled
                        DealTab.JEONSE -> it is AptRent && it.isJeonse
                        DealTab.MONTHLY -> it is AptRent && !it.isJeonse
                    }
                }
                .sortedBy { it.dealDate }
                .groupBy { it.complexAreaKey }
                .mapValues { (_, group) ->
                    group.map { deal ->
                        when (deal) {
                            is AptTrade -> deal.dealAmountManwon
                            is AptRent -> deal.depositManwon
                        }
                    }
                }
        }
    }

    override fun observeAreasOfComplex(complexKey: String): Flow<List<Double>> =
        deals.map { list -> list.filter { it.complexKey == complexKey }.map { it.exclusiveAreaM2 }.distinct() }

    /** 검색어를 받은 그대로 기록해 두면 디바운스·최소 길이 동작을 확인할 수 있다. */
    val searchedQueries = mutableListOf<String>()

    override fun searchComplexes(query: String): Flow<List<ComplexSummary>> {
        searchedQueries += query
        val trimmed = query.trim()
        if (trimmed.length < 2) return deals.map { emptyList() }
        return deals.map { list ->
            list.filter { it.aptName.contains(trimmed) }
                .groupBy { it.complexKey }
                .map { (complexKey, group) ->
                    val latest = group.maxByOrNull { it.dealDate }!!
                    ComplexSummary(
                        complexKey = complexKey,
                        aptName = latest.aptName,
                        lawdCd = latest.lawdCd,
                        umdNm = latest.umdNm,
                        regionLabel = "${latest.lawdCd} ${latest.umdNm}",
                        latestDealDate = latest.dealDate,
                        latestAreaM2 = latest.exclusiveAreaM2,
                        dealCount = group.size,
                    )
                }
                .sortedByDescending { it.latestDealDate }
        }
    }

    override suspend fun sync(
        plan: TradeQueryPlan,
        onProgress: (SyncProgress) -> Unit,
    ): SyncReport {
        syncedPlans += plan
        onProgress(SyncProgress(plan.requestCount * 2, plan.requestCount * 2, null))
        return report
    }

    override suspend fun lastFetchedAt(): Instant? = fetchedAt
}
