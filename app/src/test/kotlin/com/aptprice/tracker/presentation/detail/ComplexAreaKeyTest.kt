package com.aptprice.tracker.presentation.detail

import com.aptprice.tracker.domain.model.AptTrade
import com.aptprice.tracker.domain.model.ComplexAreaKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class ComplexAreaKeyTest {

    @Test
    fun `키를 만들고 다시 읽을 수 있다`() {
        val key = ComplexAreaKey.of("11680", "역삼동", "래미안", 84.97)

        assertEquals("11680|역삼동|래미안|84.97", key.raw)
        assertEquals("11680", key.lawdCd)
        assertEquals("역삼동", key.umdNm)
        assertEquals("래미안", key.aptName)
        assertEquals(84.97, key.areaM2!!, 1e-9)
        assertEquals("11680|역삼동|래미안", key.complexKey)
    }

    @Test
    fun `기기 로케일이 달라도 같은 키가 나온다`() {
        val original = Locale.getDefault()
        try {
            // 소수 구분자가 쉼표인 로케일
            Locale.setDefault(Locale.GERMANY)
            val german = ComplexAreaKey.of("11680", "역삼동", "래미안", 84.97)

            Locale.setDefault(Locale.KOREA)
            val korean = ComplexAreaKey.of("11680", "역삼동", "래미안", 84.97)

            assertEquals("로케일에 따라 키가 달라지면 캐시가 어긋난다", german.raw, korean.raw)
            assertTrue(german.raw.contains("84.97"))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `도메인 모델이 만드는 키도 로케일에 좌우되지 않는다`() {
        val original = Locale.getDefault()
        try {
            val trade = AptTrade(
                lawdCd = "11680", umdNm = "역삼동", aptName = "래미안", jibun = null,
                exclusiveAreaM2 = 84.97, floor = 1, buildYear = null,
                dealDate = LocalDate.of(2026, 9, 3), canceled = false, canceledDate = null,
                dealAmountManwon = 1, dealingGbn = null, registerDate = null,
            )
            Locale.setDefault(Locale.GERMANY)
            val german = trade.complexAreaKey
            Locale.setDefault(Locale.KOREA)
            assertEquals(german, trade.complexAreaKey)
            assertTrue(german.endsWith("84.97"))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `단지명에 구분자가 있어도 읽어낸다`() {
        val key = ComplexAreaKey.of("11680", "역삼동", "래미안|1차", 84.97)
        assertEquals("래미안|1차", key.aptName)
        assertEquals(84.97, key.areaM2!!, 1e-9)
        assertEquals("11680", key.lawdCd)
    }

    @Test
    fun `같은 단지에서 평형만 바꿀 수 있다`() {
        val key = ComplexAreaKey.of("11680", "역삼동", "래미안", 84.97)
        val other = ComplexAreaKey.ofComplex(key.complexKey, 59.99)

        assertEquals("11680|역삼동|래미안|59.99", other.raw)
        assertEquals(key.complexKey, other.complexKey)
    }

    @Test
    fun `화면 경로용 인코딩이 왕복한다`() {
        // 한글 · 공백 · 구분자가 모두 들어간 최악의 경우
        val key = ComplexAreaKey.of("41590", "반송동", "시범 다은마을 | 아파트", 84.97)
        val encoded = key.encode()

        assertTrue("경로에 쓸 수 없는 문자가 없어야 한다", encoded.all { it.isLetterOrDigit() || it in "-_" })
        assertEquals(key.raw, ComplexAreaKey.decode(encoded)?.raw)
    }

    @Test
    fun `잘못된 인코딩은 null 이다`() {
        assertNull(ComplexAreaKey.decode("!!! 이건 base64 가 아니다 !!!"))
        assertNotNull(ComplexAreaKey.decode(ComplexAreaKey("a|b|c|1.00").encode()))
    }

    @Test
    fun `면적이 숫자가 아니면 null 이다`() {
        assertNull(ComplexAreaKey("11680|역삼동|래미안|미상").areaM2)
    }

    @Test
    fun `면적은 항상 소수 두 자리로 맞춘다`() {
        assertEquals("84.00", ComplexAreaKey.formatArea(84.0))
        assertEquals("84.97", ComplexAreaKey.formatArea(84.9712))
        assertEquals("84.97", ComplexAreaKey.formatArea(84.9749))
    }
}
