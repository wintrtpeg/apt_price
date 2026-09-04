package com.aptprice.tracker.presentation.settings

import com.aptprice.tracker.data.remote.api.KeyFormatHint
import com.aptprice.tracker.data.remote.api.ServiceKeyProvider
import com.aptprice.tracker.data.repository.FakeServiceKeyStore
import com.aptprice.tracker.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val store = FakeServiceKeyStore()
    private val provider = ServiceKeyProvider(store, buildConfigKey = "")
    private fun viewModel() = SettingsViewModel(provider)

    @Test
    fun `처음에는 키가 없다고 표시한다`() = runTest {
        assertFalse(viewModel().uiState.value.isConfigured)
    }

    @Test
    fun `키를 저장하면 설정됨으로 바뀐다`() = runTest {
        val vm = viewModel()
        vm.onInputChange("VALID_DECODING_KEY_1234567890")
        vm.save()

        assertTrue(vm.uiState.value.isConfigured)
        assertEquals("VALID_DECODING_KEY_1234567890", provider.rawKey())
        assertNotNull(vm.uiState.value.savedMessage)
        assertEquals("저장 후 입력창은 비운다", "", vm.uiState.value.input)
    }

    @Test
    fun `빈 값은 저장하지 않는다`() = runTest {
        val vm = viewModel()
        vm.onInputChange("   ")
        vm.save()

        assertEquals(0, store.saveCount)
        assertEquals(KeyFormatHint.EMPTY, vm.uiState.value.hint)
        assertFalse(vm.uiState.value.isConfigured)
    }

    @Test
    fun `Encoding 키로 보이면 알려 주되 저장은 한다`() = runTest {
        val vm = viewModel()
        vm.onInputChange("abc%2Bdef%2Fghi%3D%3Dxxxxxxxxxx")

        assertEquals(KeyFormatHint.LOOKS_ENCODED, vm.uiState.value.hint)

        vm.save()
        // 형식 판단은 추정일 뿐이라 사용자의 입력을 막지 않는다.
        assertEquals(1, store.saveCount)
        assertTrue(vm.uiState.value.isConfigured)
    }

    @Test
    fun `저장된 키를 지울 수 있다`() = runTest {
        val vm = viewModel()
        vm.onInputChange("VALID_DECODING_KEY_1234567890")
        vm.save()
        assertTrue(vm.uiState.value.isConfigured)

        vm.clear()
        assertFalse(vm.uiState.value.isConfigured)
        assertEquals("", provider.rawKey())
        assertNull(provider.encodedKey())
    }

    @Test
    fun `안내 메시지를 닫을 수 있다`() = runTest {
        val vm = viewModel()
        vm.onInputChange("VALID_DECODING_KEY_1234567890")
        vm.save()
        assertNotNull(vm.uiState.value.savedMessage)

        vm.dismissMessage()
        assertNull(vm.uiState.value.savedMessage)
    }

    @Test
    fun `입력을 고치면 이전 저장 메시지는 사라진다`() = runTest {
        val vm = viewModel()
        vm.onInputChange("VALID_DECODING_KEY_1234567890")
        vm.save()

        vm.onInputChange("A")
        assertNull(vm.uiState.value.savedMessage)
    }
}
