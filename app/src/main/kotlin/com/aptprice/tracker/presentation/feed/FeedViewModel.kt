package com.aptprice.tracker.presentation.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aptprice.tracker.core.format.AreaBucket
import com.aptprice.tracker.core.time.TradePeriod
import com.aptprice.tracker.core.time.TradeQueryPlan
import com.aptprice.tracker.data.remote.api.ServiceKeyProvider
import com.aptprice.tracker.data.remote.parser.MolitApiError
import com.aptprice.tracker.domain.model.DealTab
import com.aptprice.tracker.domain.repository.TradeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
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
    private val serviceKey: ServiceKeyProvider,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    private val filterFlow = MutableStateFlow(FeedFilter())
    private var syncJob: Job? = null

    init {
        observeDeals()
        observeServiceKey()
        requestSync(force = false)
    }

    /**
     * 설정 화면에서 인증키를 넣고 돌아오면 곧바로 조회를 시작한다.
     * 키가 없던 상태에서 생긴 경우에만 반응한다.
     */
    private fun observeServiceKey() {
        var wasConfigured: Boolean? = null
        serviceKey.isConfigured
            .onEach { configured ->
                val previous = wasConfigured
                wasConfigured = configured
                if (previous == false && configured) requestSync(force = false)
            }
            .catch { error -> showFlowError("인증키 상태를 읽지 못했습니다", error) }
            .launchIn(viewModelScope)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeDeals() {
        // filterFlow 는 StateFlow 라 같은 필터를 두 번 흘리지 않는다.
        filterFlow
            .flatMapLatest { filter ->
                repository
                    .observeDeals(filter.period, filter.regions.codes(), filter.tab)
                    .map { deals -> filter to FeedBuilder.build(deals, filter, today()) }
            }
            // 목록이 그대로면 그래프도 다시 조회하지 않는다.
            .distinctUntilChanged()
            .flatMapLatest { (filter, items) ->
                // 그래프는 목록에 담을 수 없다. 카드가 보여 주는 한 건이 아니라
                // 그 단지·평형의 지난 거래들이라, 목록과는 다른 조회가 필요하다.
                repository
                    .observeAmountSeries(items.map { it.complexAreaKey }, filter.tab)
                    .map { series -> Triple(filter, items, SparklineBuilder.build(series, filter.tab)) }
            }
            .onEach { (filter, items, sparklines) ->
                // 요약은 목록을 그대로 접은 것이라 다시 조회하지 않는다.
                val summary = FeedSummaryBuilder.build(items, filter.period, filter.tab, today())
                _uiState.update { state ->
                    state.copy(
                        filter = filter,
                        summary = summary,
                        sparklines = sparklines,
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
            // Flow 안에서 던져진 예외는 그대로 앱을 종료시킨다. 화면에 알리고 멈춘다.
            .catch { error -> showFlowError("목록을 표시하지 못했습니다", error) }
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

    /**
     * 지역 선택을 적용한다.
     *
     * 시트에서 칩을 누를 때마다가 아니라 **확인을 눌렀을 때 한 번만** 불린다.
     * 한 곳씩 켤 때마다 조회하면 열 곳을 고르는 동안 아홉 번을 헛부르게 되고,
     * 그것이 공공데이터포털의 429(Too Many Requests) 로 이어졌다.
     */
    fun applyRegions(selection: RegionSelection) =
        updateFilter { it.copy(regions = selection) }

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
        // 지역을 고르기 전에는 아무것도 조회하지 않는다.
        if (filterFlow.value.regions.isEmpty) {
            _uiState.update {
                it.copy(
                    sync = SyncStatus(inProgress = false),
                    isRefreshing = false,
                    heavyQueryPrompt = null,
                    content = FeedContent.Empty(FeedUiState.NO_REGION_SELECTED),
                )
            }
            return
        }
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

            val report = try {
                repository.sync(plan) { progress ->
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // 여기까지 올라온 것은 앱을 종료시킨다. Error 까지 포함해 막는다.
                _uiState.update { state ->
                    state.copy(
                        sync = SyncStatus(inProgress = false),
                        isRefreshing = false,
                        content = if (state.content is FeedContent.Items) {
                            state.content
                        } else {
                            FeedContent.Error(
                                message = "실거래가를 불러오지 못했습니다: " +
                                    (e.message ?: e::class.simpleName.orEmpty()),
                                retryable = true,
                            )
                        },
                    )
                }
                return@launch
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
                            needsServiceKey =
                                report.abortedBy.kind == MolitApiError.Kind.INVALID_SERVICE_KEY,
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

    /** Flow 가 실패했을 때 앱을 죽이는 대신 화면에 남긴다. */
    private fun showFlowError(prefix: String, error: Throwable) {
        _uiState.update { state ->
            state.copy(
                sync = SyncStatus(inProgress = false),
                isRefreshing = false,
                content = FeedContent.Error(
                    message = "$prefix: ${error.message ?: error::class.simpleName.orEmpty()}",
                    retryable = true,
                ),
            )
        }
    }

    /** 지금 조건으로 조회하면 몇 번을 부르게 되는지. 화면에 미리 보여 준다. */
    fun currentRequestCount(): Int = currentPlan().requestCount

    /**
     * 아직 적용하지 않은 선택으로 조회하면 몇 번을 부르게 되는지.
     * 지역 시트가 확인을 누르기 전에 규모를 보여주는 데 쓴다.
     */
    fun requestCountFor(selection: RegionSelection): Int =
        TradeQueryPlan.of(filterFlow.value.period, today(), selection.codes()).requestCount

    private fun today(): LocalDate = LocalDate.now(clock)

}
