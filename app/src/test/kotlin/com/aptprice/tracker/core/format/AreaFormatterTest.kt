package com.aptprice.tracker.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AreaFormatterTest {

    @Test
    fun `전용면적 문자열을 제곱미터 실수로 파싱한다`() {
        assertEquals(84.97, AreaFormatter.parseAreaM2("84.97")!!, 1e-9)
        assertEquals(59.99, AreaFormatter.parseAreaM2(" 59.99 ")!!, 1e-9)
        assertEquals(1_084.97, AreaFormatter.parseAreaM2("1,084.97")!!, 1e-9)
    }

    @Test
    fun `면적이 없거나 0 이하이면 null 이다`() {
        assertNull(AreaFormatter.parseAreaM2(null))
        assertNull(AreaFormatter.parseAreaM2(""))
        assertNull(AreaFormatter.parseAreaM2("미상"))
        assertNull(AreaFormatter.parseAreaM2("0"))
        assertNull(AreaFormatter.parseAreaM2("-10"))
    }

    @Test
    fun `평 환산은 제곱미터에 0_3025 를 곱한다`() {
        assertEquals(25.703425, AreaFormatter.toPyeong(84.97), 1e-9)
        assertEquals(0.3025, AreaFormatter.toPyeong(1.0), 1e-9)
    }

    @Test
    fun `목록 카드에는 제곱미터와 평을 함께 표기한다`() {
        assertEquals("84.97㎡ (25.7평)", AreaFormatter.formatWithPyeong(84.97))
        assertEquals("59.99㎡ (18.1평)", AreaFormatter.formatWithPyeong(59.99))
    }

    @Test
    fun `불필요한 소수점 0 은 지운다`() {
        assertEquals("84㎡", AreaFormatter.formatM2(84.0))
        assertEquals("84.5㎡", AreaFormatter.formatM2(84.50))
    }

    @Test
    fun `평형 선택 칩은 정수 평으로 반올림한다`() {
        assertEquals("26평", AreaFormatter.formatPyeongChip(84.97))
        assertEquals("18평", AreaFormatter.formatPyeongChip(59.99))
        assertEquals("35평", AreaFormatter.formatPyeongChip(114.20))
    }

    @Test
    fun `평형대는 60제곱미터와 국민주택규모 85제곱미터를 경계로 나뉜다`() {
        assertEquals(AreaBucket.SMALL, AreaFormatter.bucketOf(39.99))
        assertEquals(AreaBucket.SMALL, AreaFormatter.bucketOf(59.99))
        assertEquals(AreaBucket.MEDIUM, AreaFormatter.bucketOf(60.0))
        assertEquals(AreaBucket.MEDIUM, AreaFormatter.bucketOf(84.97))
        assertEquals(AreaBucket.MEDIUM, AreaFormatter.bucketOf(85.0))
        assertEquals(AreaBucket.LARGE, AreaFormatter.bucketOf(85.01))
        assertEquals(AreaBucket.LARGE, AreaFormatter.bucketOf(134.98))
    }

    @Test
    fun `평형대 라벨은 소형 중형 대형이다`() {
        assertEquals("소형", AreaBucket.SMALL.label)
        assertEquals("중형", AreaBucket.MEDIUM.label)
        assertEquals("대형", AreaBucket.LARGE.label)
    }
}
