package com.aptprice.tracker.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aptprice.tracker.core.attribution.DataSourceAttribution
import com.aptprice.tracker.domain.model.ComplexSummary
import com.aptprice.tracker.domain.repository.TradeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * 아파트 검색 화면 상태.
 *
 * 고르는 것과 여는 것을 나눈다. 목록에서 단지를 고르면 [selected] 만 바뀌고,
 * **조회** 를 눌러야 상세로 넘어간다.
 */
data class SearchUiState(
    val query: String = "",
    val results: List<ComplexSummary> = emptyList(),
    /** 검색어가 너무 짧아 아직 찾지 않는 상태 */
    val tooShort: Boolean = false,
    val selected: ComplexSummary? = null,
    val message: String? = null,
) {
    /** 조회 버튼을 누를 수 있는가. */
    val canOpen: Boolean get() = selected != null

    /**
     * 화면 하단에 항상 노출하는 출처.
     *
     * 작업지시서 2.2 는 모든 화면에 출처를 명시하도록 한다. 검색 화면은 조회를 하지 않고
     * 이미 받아온 자료만 훑으므로 기준일시가 없다. 대신 **검색 범위**를 함께 밝힌다.
     */
    fun attributionLabel(): String =
        "데이터 출처: ${DataSourceAttribution.PROVIDER} · 받아온 자료 안에서 검색"
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: TradeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val queryFlow = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private val searching = queryFlow
        // 타이핑 중에 매 글자마다 조회하지 않는다.
        .debounce(DEBOUNCE_MILLIS)
        .flatMapLatest { repository.searchComplexes(it) }
        .onEach { results ->
            _uiState.update { state ->
                state.copy(
                    results = results,
                    // 목록이 바뀌면 사라진 단지를 고른 채로 두지 않는다.
                    selected = state.selected?.takeIf { picked ->
                        results.any { it.complexKey == picked.complexKey }
                    },
                    message = messageFor(state.query, results),
                )
            }
        }
        // Flow 안에서 던져진 예외는 그대로 앱을 종료시킨다. 화면에 알리고 멈춘다.
        .catch { error ->
            _uiState.update {
                it.copy(
                    results = emptyList(),
                    selected = null,
                    message = "검색에 실패했습니다: ${error.message ?: error::class.simpleName}",
                )
            }
        }
        .launchIn(viewModelScope)

    fun onQueryChange(value: String) {
        queryFlow.value = value
        _uiState.update {
            it.copy(
                query = value,
                tooShort = value.trim().isNotEmpty() && value.trim().length < MIN_LENGTH,
                results = if (value.isBlank()) emptyList() else it.results,
                selected = if (value.isBlank()) null else it.selected,
                message = if (value.isBlank()) null else it.message,
            )
        }
    }

    /** 목록에서 단지를 고른다. 다시 누르면 선택이 풀린다. */
    fun select(summary: ComplexSummary) {
        _uiState.update {
            it.copy(selected = if (it.selected?.complexKey == summary.complexKey) null else summary)
        }
    }

    fun clear() {
        queryFlow.value = ""
        _uiState.value = SearchUiState()
    }

    private fun messageFor(query: String, results: List<ComplexSummary>): String? = when {
        query.trim().length < MIN_LENGTH -> null
        results.isEmpty() -> NOT_FOUND
        else -> null
    }

    companion object {
        /** 한 글자로는 결과가 너무 많아 쓸모가 없다. */
        const val MIN_LENGTH = 2

        /**
         * 검색은 이미 받아온 자료만 훑는다. 국토교통부 API 가 단지명 조회를 제공하지 않아
         * 전국을 미리 받아 둘 수 없기 때문이다. 그 사실을 감추지 않고 그대로 알린다.
         */
        const val NOT_FOUND =
            "받아온 자료에서 찾지 못했습니다.\n" +
                "메인 화면에서 지역과 기간을 골라 조회한 뒤 다시 검색해 주세요."

        private const val DEBOUNCE_MILLIS = 250L
    }
}
