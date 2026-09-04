package com.aptprice.tracker.presentation.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aptprice.tracker.core.format.AreaBucket
import com.aptprice.tracker.core.time.TradePeriod
import com.aptprice.tracker.core.time.TradeQueryPlan
import com.aptprice.tracker.domain.model.DealTab
import com.aptprice.tracker.domain.region.RegionGroup
import com.aptprice.tracker.domain.repository.TradeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val repository: TradeRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    private val filterFlow = MutableStateFlow(FeedFilter())
    private var syncJob: Job? = null

    init {
        observeDeals()
        requestSync(force = false)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeDeals() {
        // StateFlow 는 같은 값을 이미 걸러 내므로 distinctUntilChanged 가 필요 없다.
        filterFlow
            .flatMapLatest { filter ->
                repository
                    .observeDeals(filter.period, filter.regions.codes(), filter.tab)
                    .map { deals -> filter to FeedBuilder.build(deals, filter, today()) }
            }
            .onEach { (filter, items) ->
                _uiState.update { state ->
                    state.copy(
                        filter = filter,
                        content = when {
                            items.isNotEmpty() -> FeedContent.Items(items)
                            // 아직 한 번도 받아오지 않았다면 "없음" 이 아니라 로딩이다.
                            state.sync.inProgress -> FeedContent.Loading
                            state.lastFetchedAt == null -> FeedContent.Loading
                            else -> FeedContent.Empty(FeedUiState.emptyMessage(filter))
                        },
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    // ---------- 필터 조작 ----------

    fun selectTab(tab: DealTab) = updateFilter { it.copy(tab = tab) }

    fun selectPeriod(period: TradePeriod) = updateFilter { it.copy(period = period) }

    fun selectSort(sort: FeedSort) {
        // 정렬은 이미 받아온 데이터만 다시 세우면 되므로 재조회하지 않는다.
        filterFlow.value = filterFlow.value.copy(sort = sort)
    }

    fun toggleAreaBucket(bucket: AreaBucket) {
        // 평형대 필터도 조회 범위를 바꾸지 않는다.
        filterFlow.value = filterFlow.value.toggleAreaBucket(bucket)
    }

    fun toggleIncludeCanceled() {
        filterFlow.value = filterFlow.value.let { it.copy(includeCanceled = !it.includeCanceled) }
    }

    fun toggleRegion(lawdCd: String) =
        updateFilter { it.copy(regions = it.regions.toggleRegion(lawdCd)) }

    fun toggleRegionGroup(group: RegionGroup) =
        updateFilter { it.copy(regions = it.regions.toggleGroup(group)) }

    fun selectAllRegions() = updateFilter { it.copy(regions = RegionSelection.all()) }

    /** 조회 범위가 바뀌는 변경. 필터를 갱신하고 필요한 구간을 받아온다. */
    private fun updateFilter(transform: (FeedFilter) -> FeedFilter) {
        val updated = transform(filterFlow.value)
        if (updated == filterFlow.value) return
        filterFlow.value = updated
        requestSync(force = false)
    }

    // ---------- 동기화 ----------

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        requestSync(force = true)
    }

    /** 무거운 조회 확인 대화상자에서 "계속" 을 누른 경우. */
    fun confirmHeavyQuery() {
        _uiState.update { it.copy(heavyQueryPrompt = null) }
        runSync()
    }

    fun dismissHeavyQuery() {
        _uiState.update { it.copy(heavyQueryPrompt = null, isRefreshing = false) }
    }

    fun dismissNotices() {
        _uiState.update { it.copy(parseFailureNotice = null, partialSyncNotice = null) }
    }

    private fun requestSync(force: Boolean) {
        val plan = currentPlan()
        // 수천 회짜리 조회는 바로 시작하지 않고 규모를 알린 뒤 확인을 받는다.
        if (plan.isHeavy && !force) {
            _uiState.update { it.copy(heavyQueryPrompt = HeavyQueryPrompt.of(plan)) }
            return
        }
        // 지역을 좁혀 조회가 가벼워졌다면, 떠 있던 확인 요청은 더 이상 유효하지 않다.
        _uiState.update { it.copy(heavyQueryPrompt = null) }
        runSync()
    }

    private fun runSync() {
        val plan = currentPlan()
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            _uiState.update {
                it.copy(sync = SyncStatus(inProgress = true, completed = 0, total = plan.requestCount * 2))
            }

            val report = repository.sync(plan) { progress ->
                _uiState.update {
                    it.copy(
                        sync = SyncStatus(
                            inProgress = true,
                            completed = progress.completed,
                            total = progress.total,
                        ),
                    )
                }
            }

            val (parseNotice, partialNotice) = FeedUiState.noticesFrom(report)
            val fetchedAt = repository.lastFetchedAt()

            _uiState.update { state ->
                state.copy(
                    sync = SyncStatus(inProgress = false),
                    isRefreshing = false,
                    lastFetchedAt = fetchedAt,
                    parseFailureNotice = parseNotice,
                    partialSyncNotice = partialNotice,
                    content = when {
                        state.content is FeedContent.Items -> state.content
                        report.abortedBy != null -> FeedContent.Error(
                            message = report.abortedBy.userMessage(),
                            retryable = report.abortedBy.isRetriable,
                        )
                        else -> FeedContent.Empty(FeedUiState.emptyMessage(state.filter))
                    },
                )
            }
        }
    }

    private fun currentPlan(): TradeQueryPlan {
        val filter = filterFlow.value
        return TradeQueryPlan.of(filter.period, today(), filter.regions.codes())
    }

    /** 지금 조건으로 조회하면 몇 번을 부르게 되는지. 화면에 미리 보여 준다. */
    fun currentRequestCount(): Int = currentPlan().requestCount

    private fun today(): LocalDate = LocalDate.now(clock)

}
