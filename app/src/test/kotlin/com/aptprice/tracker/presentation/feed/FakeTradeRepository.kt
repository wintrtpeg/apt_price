package com.aptprice.tracker.presentation.feed

import com.aptprice.tracker.core.time.TradePeriod
import com.aptprice.tracker.core.time.TradeQueryPlan
import com.aptprice.tracker.domain.model.AptDeal
import com.aptprice.tracker.domain.model.AptRent
import com.aptprice.tracker.domain.model.AptTrade
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

    override fun observeAreasOfComplex(complexKey: String): Flow<List<Double>> =
        deals.map { list -> list.filter { it.complexKey == complexKey }.map { it.exclusiveAreaM2 }.distinct() }

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
