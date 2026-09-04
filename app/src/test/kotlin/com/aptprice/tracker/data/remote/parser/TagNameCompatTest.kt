package com.aptprice.tracker.data.remote.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 같은 자료라도 서비스에 따라 응답 태그 이름이 영문이거나 한글이다.
 * 어느 쪽이 오든 읽어야 한다.
 */
class TagNameCompatTest {

    private fun trade(body: String) = """
        <response><header><resultCode>000</resultCode></header>
        <body><items><item>$body</item></items><totalCount>1</totalCount></body></response>
    """.trimIndent()

    @Test
    fun `매매 응답의 한글 태그를 읽는다`() {
        val page = MolitParser.parseTrades(
            trade(
                """
                <거래금액>87,500</거래금액>
                <건축년도>2005</건축년도>
                <년>2026</년><월>9</월><일>3</일>
                <아파트>한글태그아파트</아파트>
                <전용면적>84.97</전용면적>
                <지번>101</지번>
                <지역코드>11680</지역코드>
                <법정동>역삼동</법정동>
                <층>10</층>
                """.trimIndent(),
            ),
            "11680",
        )

        assertTrue("한글 태그를 읽지 못했다: ${page.failures.map { it.reason }}", page.failures.isEmpty())
        val item = page.items.single()
        assertEquals("한글태그아파트", item.aptName)
        assertEquals("역삼동", item.umdNm)
        assertEquals(87_500L, item.dealAmountManwon)
        assertEquals(84.97, item.exclusiveAreaM2, 1e-9)
        assertEquals(LocalDate.of(2026, 9, 3), item.dealDate)
        assertEquals(10, item.floor)
        assertEquals(2005, item.buildYear)
        assertEquals("101", item.jibun)
    }

    @Test
    fun `매매 응답의 한글 해제여부도 읽는다`() {
        val page = MolitParser.parseTrades(
            trade(
                """
                <거래금액>300,000</거래금액><년>2026</년><월>9</월><일>1</일>
                <아파트>해제테스트</아파트><전용면적>84.97</전용면적>
                <지역코드>11680</지역코드><법정동>역삼동</법정동>
                <해제여부>O</해제여부><해제사유발생일>26.09.20</해제사유발생일>
                """.trimIndent(),
            ),
            "11680",
        )
        val item = page.items.single()
        assertTrue(item.canceled)
        assertEquals(LocalDate.of(2026, 9, 20), item.canceledDate)
    }

    @Test
    fun `영문 태그도 그대로 읽는다`() {
        val page = MolitParser.parseTrades(
            trade(
                """
                <dealAmount>87,500</dealAmount>
                <dealYear>2026</dealYear><dealMonth>9</dealMonth><dealDay>3</dealDay>
                <aptNm>영문태그아파트</aptNm><excluUseAr>84.97</excluUseAr>
                <sggCd>11680</sggCd><umdNm>역삼동</umdNm><floor>10</floor>
                """.trimIndent(),
            ),
            "11680",
        )
        assertTrue(page.failures.isEmpty())
        assertEquals("영문태그아파트", page.items.single().aptName)
    }

    @Test
    fun `전월세 응답의 한글 태그를 읽는다`() {
        val page = MolitParser.parseRents(
            trade(
                """
                <보증금액>50,000</보증금액><월세금액>0</월세금액>
                <년>2026</년><월>9</월><일>3</일>
                <아파트>한글전세</아파트><전용면적>84.97</전용면적>
                <지역코드>41135</지역코드><법정동>정자동</법정동><층>7</층>
                <계약구분>신규</계약구분><계약기간>25.09~27.09</계약기간>
                """.trimIndent(),
            ),
            "41135",
        )

        assertTrue("한글 태그를 읽지 못했다: ${page.failures.map { it.reason }}", page.failures.isEmpty())
        val item = page.items.single()
        assertEquals("한글전세", item.aptName)
        assertEquals(50_000L, item.depositManwon)
        assertEquals(0L, item.monthlyRentManwon)
        assertTrue(item.isJeonse)
        assertEquals("신규", item.contractType)
    }

    @Test
    fun `한글 월세 태그도 읽는다`() {
        val page = MolitParser.parseRents(
            trade(
                """
                <보증금액>10,000</보증금액><월세금액>120</월세금액>
                <년>2026</년><월>9</월><일>4</일>
                <아파트>한글월세</아파트><전용면적>59.99</전용면적>
                <지역코드>41135</지역코드><법정동>정자동</법정동>
                <종전계약보증금>8,000</종전계약보증금><종전계약월세>100</종전계약월세>
                """.trimIndent(),
            ),
            "41135",
        )
        val item = page.items.single()
        assertEquals(120L, item.monthlyRentManwon)
        assertEquals(8_000L, item.previousDepositManwon)
        assertEquals(100L, item.previousMonthlyRentManwon)
    }

    @Test
    fun `한글 태그여도 필수 값이 없으면 값을 만들지 않는다`() {
        val page = MolitParser.parseTrades(
            trade("<아파트>금액없음</아파트><전용면적>84.97</전용면적><년>2026</년><월>9</월><일>1</일><법정동>역삼동</법정동>"),
            "11680",
        )
        assertEquals(0, page.items.size)
        assertEquals(1, page.failures.size)
        assertTrue(page.failures.single().reason.contains("거래금액"))
    }
}
