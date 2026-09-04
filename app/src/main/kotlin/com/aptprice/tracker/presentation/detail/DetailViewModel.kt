package com.aptprice.tracker.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aptprice.tracker.core.time.TradePeriod
import com.aptprice.tracker.core.time.TradeQueryPlan
import com.aptprice.tracker.domain.model.AptRent
import com.aptprice.tracker.domain.model.AptTrade
import com.aptprice.tracker.domain.model.ComplexAreaKey
import com.aptprice.tracker.domain.repository.TradeRepository
import com.aptprice.tracker.presentation.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * 단지 상세 화면.
 *
 * 지역 하나만 보므로 5년 구간이어도 조회량이 61회로 작다.
 * (전체 지역 5년은 2,196회 — 메인 피드에서 확인을 받는 이유다)
 */
@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: TradeRepository,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val initialKey: ComplexAreaKey? =
        savedStateHandle.get<String>(Routes.ARG_COMPLEX_AREA_KEY)?.let(ComplexAreaKey::decode)

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    /** 선택된 (평형 키, 기간). 둘 중 하나가 바뀌면 다시 그린다. */
    private val selection = MutableStateFlow(
        initialKey to TradePeriod.CHART_DEFAULT,
    )

    private var syncJob: Job? = null

    init {
        if (initialKey == null) {
            _uiState.update {
                it.copy(isLoading = false, emptyMessage = "단지 정보를 읽지 못했습니다")
            }
        } else {
            observe()
            requestSync()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observe() {
        selection
            .flatMapLatest { (key, period) ->
                if (key == null) return@flatMapLatest flowOf(null)
                combine(
                    repository.observeTradeSeries(key.raw, period),
                    repository.observeJeonseSeries(key.raw, period),
                    repository.observeAreasOfComplex(key.complexKey),
                ) { trades, jeonse, areas ->
                    Triple(trades, jeonse, areas) to (key to period)
                }
            }
            .onEach { result ->
                if (result == null) return@onEach
                val (data, context) = result
                val (trades, jeonse, areas) = data
                val (key, period) = context
                render(key, period, trades, jeonse, areas)
            }
            .launchIn(viewModelScope)
    }

    private fun render(
        key: ComplexAreaKey,
        period: TradePeriod,
        trades: List<AptTrade>,
        jeonse: List<AptRent>,
        areas: List<Double>,
    ) {
        val chart = ChartBuilder.build(trades, jeonse, period, today())
        _uiState.update { state ->
            state.copy(
                key = key,
                aptName = key.aptName,
                regionLabel = DetailUiState.regionLabelOf(key),
                period = period,
                areaChips = DetailUiState.areaChipsOf(areas, key),
                chart = chart,
                history = DetailUiState.historyOf(trades, jeonse, chart.peak?.amountManwon),
                isLoading = false,
                // 값이 없으면 없다고 말한다. 빈 차트를 지어낸 선으로 채우지 않는다.
                emptyMessage = if (trades.isEmpty() && jeonse.isEmpty()) {
                    DetailUiState.emptyMessageFor(period)
                } else {
                    null
                },
            )
        }
    }

    fun selectPeriod(period: TradePeriod) {
        val (key, current) = selection.value
        if (current == period) return
        selection.value = key to period
        requestSync()
    }

    fun selectArea(chip: AreaChip) {
        val (current, period) = selection.value
        if (current?.raw == chip.key.raw) return
        // 같은 단지 안에서 평형만 바꾸는 것이라 이미 받아 둔 데이터로 충분하다.
        selection.value = chip.key to period
    }

    fun refresh() = requestSync()

    private fun requestSync() {
        val (key, period) = selection.value
        if (key == null) return
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            val plan = TradeQueryPlan.forSingleRegion(period, today(), key.lawdCd)
            try {
                repository.sync(plan)
                _uiState.update { it.copy(lastFetchedAt = repository.lastFetchedAt()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 상세 조회가 실패해도 앱이 죽지 않게 한다. 캐시에 있는 것만 보여준다.
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        emptyMessage = it.emptyMessage
                            ?: "실거래가를 불러오지 못했습니다: " +
                            (e.message ?: e::class.simpleName.orEmpty()),
                    )
                }
            }
        }
    }

    private fun today(): LocalDate = LocalDate.now(clock)
}
