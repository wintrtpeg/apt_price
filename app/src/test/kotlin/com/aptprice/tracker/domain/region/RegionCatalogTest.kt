package com.aptprice.tracker.domain.region

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionCatalogTest {

    @Test
    fun `서울은 25개 자치구를 모두 포함한다`() {
        assertEquals(25, RegionCatalog.seoul.size)
        assertEquals(25, RegionCatalog.seoul.map { it.lawdCd }.toSet().size)
        assertTrue(RegionCatalog.seoul.all { it.sido == "서울특별시" })
        assertTrue(RegionCatalog.seoul.all { it.lawdCd.startsWith("11") })
    }

    @Test
    fun `서울 주요 자치구의 법정동 코드가 맞다`() {
        assertEquals("강남구", RegionCatalog.byLawdCd("11680")?.displayName)
        assertEquals("서초구", RegionCatalog.byLawdCd("11650")?.displayName)
        assertEquals("송파구", RegionCatalog.byLawdCd("11710")?.displayName)
        assertEquals("마포구", RegionCatalog.byLawdCd("11440")?.displayName)
        assertEquals("종로구", RegionCatalog.byLawdCd("11110")?.displayName)
        assertEquals("강동구", RegionCatalog.byLawdCd("11740")?.displayName)
    }

    @Test
    fun `성남 용인 수원의 구별 코드가 맞다`() {
        assertEquals("수정구", RegionCatalog.byLawdCd("41131")?.displayName)
        assertEquals("중원구", RegionCatalog.byLawdCd("41133")?.displayName)
        assertEquals("분당구", RegionCatalog.byLawdCd("41135")?.displayName)

        assertEquals("처인구", RegionCatalog.byLawdCd("41461")?.displayName)
        assertEquals("기흥구", RegionCatalog.byLawdCd("41463")?.displayName)
        assertEquals("수지구", RegionCatalog.byLawdCd("41465")?.displayName)

        assertEquals("장안구", RegionCatalog.byLawdCd("41111")?.displayName)
        assertEquals("권선구", RegionCatalog.byLawdCd("41113")?.displayName)
        assertEquals("팔달구", RegionCatalog.byLawdCd("41115")?.displayName)
        assertEquals("영통구", RegionCatalog.byLawdCd("41117")?.displayName)
    }

    @Test
    fun `전체 대상 지역은 36곳이고 코드가 중복되지 않는다`() {
        assertEquals(36, RegionCatalog.all.size)
        assertEquals(36, RegionCatalog.lawdCodes.toSet().size)
    }

    @Test
    fun `모든 법정동 코드는 숫자 5자리다`() {
        RegionCatalog.all.forEach { region ->
            assertEquals("${region.displayName} 코드 길이", 5, region.lawdCd.length)
            assertTrue("${region.displayName} 코드는 숫자여야 함", region.lawdCd.all { it.isDigit() })
        }
    }

    @Test
    fun `그룹별로 지역을 뽑을 수 있다`() {
        assertEquals(25, RegionCatalog.byGroup(RegionGroup.SEOUL).size)
        assertEquals(3, RegionCatalog.byGroup(RegionGroup.SEONGNAM).size)
        assertEquals(3, RegionCatalog.byGroup(RegionGroup.YONGIN).size)
        assertEquals(4, RegionCatalog.byGroup(RegionGroup.SUWON).size)
        assertEquals(1, RegionCatalog.byGroup(RegionGroup.DONGTAN).size)
    }

    @Test
    fun `알 수 없는 코드는 null 이다`() {
        assertNull(RegionCatalog.byLawdCd("99999"))
        assertNull(RegionCatalog.byLawdCd(""))
        assertNotNull(RegionCatalog.byLawdCd("11680"))
    }

    @Test
    fun `대상이 아닌 시군구 코드의 거래는 받지 않는다`() {
        // 부산 해운대구(26350) 는 조회 대상이 아니다.
        assertFalse(RegionCatalog.accepts("26350", "우동"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `법정동 코드 형식이 어긋나면 생성되지 않는다`() {
        Region(
            lawdCd = "1168",
            sido = "서울특별시",
            sigungu = "강남구",
            displayName = "강남구",
            group = RegionGroup.SEOUL,
        )
    }
}
