package com.aptprice.tracker.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun `제곱미터와 평을 함께 표기하되 전용 기준임을 밝힌다`() {
        assertEquals("84.97㎡ (전용 25.7평)", AreaFormatter.formatWithPyeong(84.97))
        assertEquals("59.99㎡ (전용 18.1평)", AreaFormatter.formatWithPyeong(59.99))
    }

    @Test
    fun `평 표기에는 무엇을 기준으로 한 평인지가 반드시 붙는다`() {
        // 시장에서 84㎡ 를 "34평(국민평형)" 이라고 부르는 것은 공급면적 기준이다.
        // 전용면적을 그대로 환산한 25.7평만 적어 두면 사람들이 아는 평형과 어긋나
        // 잘못 읽는다. 기준을 밝히지 않은 평 표기가 다시 생기지 않게 고정한다.
        listOf(39.72, 59.99, 84.97, 114.20, 134.50).forEach { area ->
            val text = AreaFormatter.formatWithPyeong(area)
            assertTrue("기준이 빠졌다: $text", text.contains("전용"))
            assertTrue("평 표기가 없다: $text", text.contains("평"))
        }
    }

    @Test
    fun `공급면적을 추정해 만들어 내지 않는다`() {
        // 실거래가 API 는 전용면적만 준다. 전용률을 가정해 공급면적을 역산하면
        // 그건 지어낸 값이다 (작업지시서 2.2).
        val text = AreaFormatter.formatWithPyeong(84.97)
        // 전용 25.7평이 시장 통칭 34평으로 둔갑해서는 안 된다.
        assertFalse("공급면적 기준 평수를 지어냈다: $text", text.contains("34평"))
        assertTrue(text.contains("25.7평"))
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
    fun `시장에서 부르는 평형대로 나뉜다`() {
        // 사람들이 말하는 30평대는 공급면적 기준 호칭이다. 국토부 자료에는 전용면적만
        // 있으므로, 각 호칭에 해당하는 전용면적 구간을 잡았다.
        assertEquals(AreaBucket.UNDER_20, AreaFormatter.bucketOf(39.72))
        assertEquals(AreaBucket.UNDER_20, AreaFormatter.bucketOf(49.99))
        assertEquals("전용 59㎡ 는 20평대", AreaBucket.PYEONG_20, AreaFormatter.bucketOf(59.99))
        assertEquals("전용 84㎡ 는 30평대(국민평형)", AreaBucket.PYEONG_30, AreaFormatter.bucketOf(84.97))
        assertEquals("전용 114㎡ 는 40평대", AreaBucket.PYEONG_40, AreaFormatter.bucketOf(114.20))
        assertEquals(AreaBucket.OVER_50, AreaFormatter.bucketOf(134.88))
        assertEquals(AreaBucket.OVER_50, AreaFormatter.bucketOf(200.0))
    }

    @Test
    fun `구간 경계가 겹치거나 비지 않는다`() {
        assertEquals(AreaBucket.UNDER_20, AreaFormatter.bucketOf(49.99))
        assertEquals(AreaBucket.PYEONG_20, AreaFormatter.bucketOf(50.0))
        assertEquals(AreaBucket.PYEONG_20, AreaFormatter.bucketOf(65.99))
        assertEquals(AreaBucket.PYEONG_30, AreaFormatter.bucketOf(66.0))
        assertEquals(AreaBucket.PYEONG_30, AreaFormatter.bucketOf(98.99))
        assertEquals(AreaBucket.PYEONG_40, AreaFormatter.bucketOf(99.0))
        assertEquals(AreaBucket.PYEONG_40, AreaFormatter.bucketOf(131.99))
        assertEquals(AreaBucket.OVER_50, AreaFormatter.bucketOf(132.0))
    }

    @Test
    fun `평형대 라벨은 실제 평수 호칭이다`() {
        assertEquals("10평대 이하", AreaBucket.UNDER_20.label)
        assertEquals("20평대", AreaBucket.PYEONG_20.label)
        assertEquals("30평대", AreaBucket.PYEONG_30.label)
        assertEquals("40평대", AreaBucket.PYEONG_40.label)
        assertEquals("50평대 이상", AreaBucket.OVER_50.label)
    }

    @Test
    fun `화면 표기는 전용면적과 평형대를 함께 보여 준다`() {
        assertEquals("84.97㎡ · 30평대", AreaFormatter.formatWithBucket(84.97))
        assertEquals("59.99㎡ · 20평대", AreaFormatter.formatWithBucket(59.99))
        assertEquals("114.2㎡ · 40평대", AreaFormatter.formatWithBucket(114.20))
    }
}
