package com.aptprice.tracker.presentation.settings

import com.aptprice.tracker.data.remote.api.KeyFormatHint
import com.aptprice.tracker.data.remote.api.ServiceKeyProvider
import com.aptprice.tracker.data.repository.FakeServiceKeyStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceKeyTest {

    private fun provider(stored: String = "", buildConfig: String = "") =
        ServiceKeyProvider(FakeServiceKeyStore(stored), buildConfig)

    @Test
    fun `키가 없으면 조회를 시도하지 않도록 null 을 준다`() = runTest {
        assertNull(provider().encodedKey())
        assertFalse(provider().isConfigured.first())
    }

    @Test
    fun `앱에서 입력한 키를 쓴다`() = runTest {
        val p = provider(stored = "STORED_KEY_1234567890")
        assertEquals("STORED_KEY_1234567890", p.rawKey())
        assertTrue(p.isConfigured.first())
    }

    @Test
    fun `앱에 키가 없으면 빌드 시 주입된 키로 넘어간다`() = runTest {
        val p = provider(stored = "", buildConfig = "BUILD_KEY_1234567890")
        assertEquals("BUILD_KEY_1234567890", p.rawKey())
        assertTrue(p.isConfigured.first())
    }

    @Test
    fun `앱에서 입력한 키가 빌드 시 키보다 우선한다`() = runTest {
        val p = provider(stored = "STORED_KEY_1234567890", buildConfig = "BUILD_KEY_1234567890")
        assertEquals("STORED_KEY_1234567890", p.rawKey())
    }

    @Test
    fun `저장한 키를 바로 읽을 수 있다`() = runTest {
        val store = FakeServiceKeyStore()
        val p = ServiceKeyProvider(store, buildConfigKey = "")
        assertFalse(p.isConfigured.first())

        p.save("  NEW_KEY_12345678901234  ")
        assertEquals("앞뒤 공백은 정리한다", "NEW_KEY_12345678901234", p.rawKey())
        assertTrue(p.isConfigured.first())

        p.clear()
        assertFalse(p.isConfigured.first())
        assertNull(p.encodedKey())
    }

    @Test
    fun `Decoding 키의 특수문자를 퍼센트 인코딩한다`() = runTest {
        // '+' 가 그대로 나가면 서버가 공백으로 해석해 인증이 깨진다.
        val p = provider(stored = "abc+def/ghi==")
        assertEquals("abc%2Bdef%2Fghi%3D%3D", p.encodedKey())
    }

    @Test
    fun `한 번 인코딩된 키는 그대로 통과시킨다`() {
        assertEquals("abc%2Bdef", ServiceKeyProvider.encode("abc+def"))
    }

    // ---------- 입력 안내 ----------

    @Test
    fun `빈 입력만 저장을 막는다`() {
        assertEquals(KeyFormatHint.EMPTY, ServiceKeyProvider.looksLikeKey(""))
        assertEquals(KeyFormatHint.EMPTY, ServiceKeyProvider.looksLikeKey("   "))
        assertTrue(KeyFormatHint.EMPTY.isBlocking)
    }

    @Test
    fun `Encoding 키를 붙여넣으면 알려 준다`() {
        // 포털의 Encoding 키에는 %2B 같은 문자가 들어 있다.
        val hint = ServiceKeyProvider.looksLikeKey("abc%2Bdef%2Fghi%3D%3Dxxxxxxxxxx")
        assertEquals(KeyFormatHint.LOOKS_ENCODED, hint)
        assertTrue(hint.message!!.contains("Decoding"))
        assertFalse("안내만 하고 저장은 막지 않는다", hint.isBlocking)
    }

    @Test
    fun `너무 짧으면 알려 준다`() {
        assertEquals(KeyFormatHint.TOO_SHORT, ServiceKeyProvider.looksLikeKey("abc123"))
    }

    @Test
    fun `중간에 공백이 있으면 알려 준다`() {
        assertEquals(
            KeyFormatHint.HAS_WHITESPACE,
            ServiceKeyProvider.looksLikeKey("abcdefghij klmnopqrstuvwxyz"),
        )
    }

    @Test
    fun `정상적인 Decoding 키는 안내가 없다`() {
        val hint = ServiceKeyProvider.looksLikeKey("aBc123+/dEf456ghi789JKL012mno345==")
        assertEquals(KeyFormatHint.OK, hint)
        assertNull(hint.message)
    }
}
