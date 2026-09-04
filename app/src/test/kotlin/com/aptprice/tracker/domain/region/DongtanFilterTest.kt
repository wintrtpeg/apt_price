package com.aptprice.tracker.domain.region

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 화성시(41590)는 시 전체가 한 코드로 묶여 있어 동탄 외 지역이 함께 내려온다.
 * 법정동명 필터가 실제로 동탄만 남기는지 확인한다.
 */
class DongtanFilterTest {

    private val dongtan = RegionCatalog.dongtan

    @Test
    fun `화성시 코드는 41590 이고 동탄 화이트리스트를 갖는다`() {
        assertEquals("41590", dongtan.lawdCd)
        assertEquals("화성시", dongtan.sigungu)
        assertEquals("동탄", dongtan.displayName)
        assertEquals(DongtanUmd.DEFAULT, dongtan.umdWhitelist)
    }

    @Test
    fun `작업지시서에 명시된 11개 동을 모두 통과시킨다`() {
        val required = listOf(
            "청계동", "목동", "영천동", "산척동", "송동",
            "장지동", "오산동", "신동", "반송동", "석우동", "능동",
        )
        required.forEach { umd ->
            assertTrue("$umd 은 동탄 관할이어야 함", dongtan.includesUmd(umd))
        }
        assertEquals(required.size, DongtanUmd.DEFAULT.size)
    }

    @Test
    fun `동탄이 아닌 화성시 법정동은 걸러낸다`() {
        // 병점·봉담·향남·남양 등은 화성시이지만 동탄 관할이 아니다.
        listOf("진안동", "봉담읍", "향남읍", "남양읍", "우정읍", "매송면").forEach { umd ->
            assertFalse("$umd 은 동탄이 아님", dongtan.includesUmd(umd))
        }
    }

    @Test
    fun `법정동명 앞뒤 공백은 무시한다`() {
        assertTrue(dongtan.includesUmd(" 청계동 "))
        assertTrue(dongtan.includesUmd("반송동 "))
    }

    @Test
    fun `법정동명이 비어 있으면 통과시키지 않는다`() {
        assertFalse(dongtan.includesUmd(null))
        assertFalse(dongtan.includesUmd(""))
        assertFalse(dongtan.includesUmd("   "))
    }

    @Test
    fun `화이트리스트가 없는 지역은 모든 법정동을 통과시킨다`() {
        val gangnam = RegionCatalog.byLawdCd("11680")!!
        assertTrue(gangnam.includesUmd("역삼동"))
        assertTrue(gangnam.includesUmd("대치동"))
        // 화이트리스트가 없으면 umdNm 이 없어도 지역 자체로는 대상이다.
        assertTrue(gangnam.includesUmd(null))
    }

    @Test
    fun `카탈로그 단위 필터도 동탄만 남긴다`() {
        assertTrue(RegionCatalog.accepts("41590", "반송동"))
        assertFalse(RegionCatalog.accepts("41590", "봉담읍"))
        assertTrue(RegionCatalog.accepts("11680", "역삼동"))
    }

    @Test
    fun `확장 목록은 기본 목록을 모두 포함하고 동탄2신도시 법정동을 더한다`() {
        assertTrue(DongtanUmd.EXTENDED.containsAll(DongtanUmd.DEFAULT))
        assertEquals(DongtanUmd.DEFAULT.size + 3, DongtanUmd.EXTENDED.size)
        listOf("방교동", "중동", "금곡동").forEach {
            assertTrue("$it 은 확장 목록에 있어야 함", it in DongtanUmd.EXTENDED)
            assertFalse("$it 은 기본 목록에는 없어야 함", it in DongtanUmd.DEFAULT)
        }
    }
}
