package com.aptprice.tracker.data.remote.parser

import com.aptprice.tracker.domain.model.DealTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 국토교통부 응답 파서 테스트.
 *
 * 픽스처는 응답 **구조** 재현용이며 실제 실거래 데이터가 아니다.
 * (app/src/test/resources/molit/README.md 참고)
 */
class MolitParserTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/molit/$name")) { "픽스처 없음: $name" }
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

    // ---------- 매매 ----------

    @Test
    fun `매매 응답을 읽어 도메인 모델로 만든다`() {
        val page = MolitParser.parseTrades(fixture("apt_trade_ok.xml"), "11680")

        assertEquals(2, page.items.size)
        assertTrue(page.failures.isEmpty())
        assertEquals(2, page.totalCount)

        val first = page.items[0]
        assertEquals("11680", first.lawdCd)
        assertEquals("역삼동", first.umdNm)
        assertEquals("파싱테스트아파트", first.aptName)
        assertEquals("101", first.jibun)
        assertEquals(84.97, first.exclusiveAreaM2, 1e-9)
        assertEquals(10, first.floor)
        assertEquals(2005, first.buildYear)
        assertEquals(LocalDate.of(2026, 9, 3), first.dealDate)
        // 앞뒤 공백과 콤마가 섞인 "    87,500" 을 만원 단위로 읽는다.
        assertEquals(87_500L, first.dealAmountManwon)
        assertEquals("중개거래", first.dealingGbn)
        assertEquals("26.09.15", first.registerDate)
        assertEquals(DealTab.SALE, first.tab)
        assertFalse(first.canceled)
        assertNull(first.canceledDate)
    }

    @Test
    fun `계약 해제 건은 해제 표시와 해제일을 갖는다`() {
        val page = MolitParser.parseTrades(fixture("apt_trade_ok.xml"), "11680")
        val canceled = page.items[1]

        assertTrue(canceled.canceled)
        assertEquals(LocalDate.of(2026, 9, 20), canceled.canceledDate)
        assertEquals(120_000L, canceled.dealAmountManwon)
    }

    @Test
    fun `응답에 없는 값은 null 로 두고 만들어 내지 않는다`() {
        val page = MolitParser.parseTrades(fixture("apt_trade_ok.xml"), "11680")
        val second = page.items[1]

        // 두 번째 행에는 dealingGbn / rgstDate 가 없다.
        assertNull(second.dealingGbn)
        assertNull(second.registerDate)
    }

    @Test
    fun `읽을 수 없는 행은 버리되 사유를 남긴다`() {
        val page = MolitParser.parseTrades(fixture("apt_trade_broken_rows.xml"), "11680")

        // 정상 1건만 살아남는다.
        assertEquals(1, page.items.size)
        assertEquals("정상행아파트", page.items[0].aptName)

        // 나머지 3건은 사유와 함께 실패로 집계된다. 0 원이나 0㎡ 로 채워지지 않는다.
        assertEquals(3, page.failures.size)
        val reasons = page.failures.map { it.reason }
        assertTrue(reasons.any { it.contains("거래금액") })
        assertTrue(reasons.any { it.contains("전용면적") })
        assertTrue(reasons.any { it.contains("계약일") })
        // 원문이 남아 있어야 어느 행이 문제인지 추적할 수 있다.
        assertTrue(page.failures.all { it.rawRow.isNotBlank() })
    }

    // ---------- 전월세 ----------

    @Test
    fun `월세액이 0이면 전세로 분류한다`() {
        val page = MolitParser.parseRents(fixture("apt_rent_ok.xml"), "41135")
        val jeonse = page.items[0]

        assertEquals("전세테스트아파트", jeonse.aptName)
        assertEquals(50_000L, jeonse.depositManwon)
        assertEquals(0L, jeonse.monthlyRentManwon)
        assertTrue(jeonse.isJeonse)
        assertEquals(DealTab.JEONSE, jeonse.tab)
        assertEquals("신규", jeonse.contractType)
        assertEquals("25.09~27.09", jeonse.contractTerm)
    }

    @Test
    fun `월세액이 있으면 월세로 분류하고 종전 조건도 읽는다`() {
        val page = MolitParser.parseRents(fixture("apt_rent_ok.xml"), "41135")
        val monthly = page.items[1]

        assertEquals(10_000L, monthly.depositManwon)
        assertEquals(120L, monthly.monthlyRentManwon)
        assertFalse(monthly.isJeonse)
        assertEquals(DealTab.MONTHLY, monthly.tab)
        assertEquals("갱신", monthly.contractType)
        assertEquals(8_000L, monthly.previousDepositManwon)
        assertEquals(100L, monthly.previousMonthlyRentManwon)
    }

    @Test
    fun `월세 태그가 없으면 전세로 본다`() {
        val page = MolitParser.parseRents(fixture("apt_rent_ok.xml"), "41135")
        val noTag = page.items[2]

        assertEquals(0L, noTag.monthlyRentManwon)
        assertTrue(noTag.isJeonse)
        assertEquals(45_000L, noTag.depositManwon)
        // 갱신 계약이 아니면 종전 조건은 없다.
        assertNull(noTag.previousDepositManwon)
    }

    @Test
    fun `전월세 자료에는 해제 필드가 없으므로 해제로 표시하지 않는다`() {
        val page = MolitParser.parseRents(fixture("apt_rent_ok.xml"), "41135")
        assertTrue(page.items.none { it.canceled })
        assertTrue(page.items.all { it.canceledDate == null })
    }

    // ---------- 오류 응답 ----------

    @Test
    fun `거래가 없는 달은 오류가 아니라 빈 결과다`() {
        val page = MolitParser.parseTrades(fixture("no_data.xml"), "11680")

        assertTrue(page.items.isEmpty())
        assertTrue(page.failures.isEmpty())
        assertEquals(0, page.totalCount)
        assertFalse(page.hasMorePages)
    }

    @Test
    fun `인증키 오류는 예외로 올리고 재시도하지 않는다`() {
        val error = runCatching { MolitParser.parseTrades(fixture("gateway_invalid_key.xml"), "11680") }
            .exceptionOrNull() as? MolitApiException

        assertNotNull(error)
        assertEquals(MolitApiError.Kind.INVALID_SERVICE_KEY, error!!.error.kind)
        assertEquals("30", error.error.code)
        assertFalse("인증키 오류는 재시도해도 소용없다", error.error.isRetriable)
        assertTrue(error.error.userMessage().contains("인증키"))
    }

    @Test
    fun `트래픽 한도 초과도 재시도하지 않는다`() {
        val error = runCatching {
            MolitParser.parseTrades(fixture("gateway_quota_exceeded.xml"), "11680")
        }.exceptionOrNull() as? MolitApiException

        assertNotNull(error)
        assertEquals(MolitApiError.Kind.QUOTA_EXCEEDED, error!!.error.kind)
        assertFalse(error.error.isRetriable)
        assertTrue(error.error.userMessage().contains("한도"))
    }

    @Test
    fun `해석할 수 없는 응답은 조용히 넘어가지 않는다`() {
        val error = runCatching { MolitParser.parseTrades("이것은 XML 이 아니다", "11680") }
            .exceptionOrNull() as? MolitApiException

        assertNotNull(error)
        assertEquals(MolitApiError.Kind.SERVICE_ERROR, error!!.error.kind)
        assertTrue("일시 오류로 보고 재시도 대상", error.error.isRetriable)
    }

    // ---------- 페이징 ----------

    @Test
    fun `전체 건수가 페이지 크기보다 크면 다음 페이지가 있다고 본다`() {
        val page = MolitParser.parseTrades(fixture("apt_trade_paged.xml"), "11680")

        assertEquals(1, page.pageNo)
        assertEquals(1, page.numOfRows)
        assertEquals(3, page.totalCount)
        assertTrue(page.hasMorePages)
    }

    @Test
    fun `마지막 페이지에서는 더 받을 것이 없다`() {
        val page = MolitParser.parseTrades(fixture("apt_trade_ok.xml"), "11680")
        // numOfRows=1000, pageNo=1, totalCount=2
        assertFalse(page.hasMorePages)
    }

    @Test
    fun `응답에 시군구 코드가 없으면 요청에 쓴 코드를 쓴다`() {
        val xml = """
            <response><header><resultCode>000</resultCode></header><body><items>
              <item>
                <aptNm>코드없는아파트</aptNm><dealAmount>10,000</dealAmount>
                <dealYear>2026</dealYear><dealMonth>9</dealMonth><dealDay>1</dealDay>
                <excluUseAr>84.97</excluUseAr><umdNm>정자동</umdNm>
              </item>
            </items><totalCount>1</totalCount></body></response>
        """.trimIndent()

        val page = MolitParser.parseTrades(xml, "41135")
        assertEquals(1, page.items.size)
        assertEquals("41135", page.items[0].lawdCd)
    }
}
