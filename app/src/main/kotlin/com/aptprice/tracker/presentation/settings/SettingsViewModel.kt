package com.aptprice.tracker.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aptprice.tracker.data.remote.api.KeyFormatHint
import com.aptprice.tracker.data.remote.api.ServiceKeyProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 인증키 설정 화면 상태. */
data class SettingsUiState(
    /** 입력창에 들어 있는 값 */
    val input: String = "",
    /** 기기에 저장된 키가 있는가 */
    val isConfigured: Boolean = false,
    /** 입력값에 대한 안내. 저장을 막지는 않는다(EMPTY 제외). */
    val hint: KeyFormatHint = KeyFormatHint.OK,
    /** 저장 직후 한 번 보여주는 메시지 */
    val savedMessage: String? = null,
) {
    val canSave: Boolean get() = !KeyFormatHint.EMPTY.isBlocking || input.isNotBlank()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val serviceKey: ServiceKeyProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        serviceKey.isConfigured
            .onEach { configured -> _uiState.update { it.copy(isConfigured = configured) } }
            // 저장소를 읽지 못해도 앱이 죽지 않게 한다.
            .catch { _ -> _uiState.update { it.copy(isConfigured = false) } }
            .launchIn(viewModelScope)
    }

    fun onInputChange(value: String) {
        _uiState.update {
            it.copy(
                input = value,
                // 입력 중에는 안내만 갱신하고 저장을 막지 않는다.
                hint = if (value.isBlank()) KeyFormatHint.OK else ServiceKeyProvider.looksLikeKey(value),
                savedMessage = null,
            )
        }
    }

    fun save() {
        val value = _uiState.value.input
        val hint = ServiceKeyProvider.looksLikeKey(value)
        if (hint.isBlocking) {
            _uiState.update { it.copy(hint = hint) }
            return
        }
        viewModelScope.launch {
            try {
                serviceKey.save(value)
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(savedMessage = "저장하지 못했습니다: ${e.message ?: e::class.simpleName}")
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    input = "",
                    hint = hint,
                    savedMessage = "인증키를 저장했습니다. 목록으로 돌아가면 조회를 시작합니다.",
                )
            }
        }
    }

    fun clear() {
        viewModelScope.launch {
            serviceKey.clear()
            _uiState.update {
                it.copy(input = "", hint = KeyFormatHint.OK, savedMessage = "저장된 인증키를 지웠습니다.")
            }
        }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(savedMessage = null) }
    }
}
