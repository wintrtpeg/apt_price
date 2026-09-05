package com.aptprice.tracker.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class AreaFormatterTest {

    @Test
    fun `면적은 제곱미터로만 적는다`() {
        // 전용 84.97㎡ 를 25.7평이라고 병기하면, 시장에서 부르는 34평(국민평형)과
        // 어긋나 오히려 헷갈린다. 그렇다고 34평은 공급면적을 지어내야 나오는 값이다.
        // 그래서 원자료 그대로 ㎡ 로만 적고, 시장 호칭은 평형대가 맡는다.
        assertEquals("84.97㎡", AreaFormatter.formatM2(84.97))
        assertEquals("84.97㎡ · 30평대", AreaFormatter.formatWithBucket(84.97))
    }

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
    fun `불필요한 소수점 0 은 지운다`() {
        assertEquals("84㎡", AreaFormatter.formatM2(84.0))
        assertEquals("84.5㎡", AreaFormatter.formatM2(84.50))
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
