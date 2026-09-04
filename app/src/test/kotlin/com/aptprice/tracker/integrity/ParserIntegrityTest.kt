package com.aptprice.tracker.integrity

import com.aptprice.tracker.data.remote.parser.MolitParser
import com.aptprice.tracker.domain.model.AptRent
import com.aptprice.tracker.domain.model.AptTrade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 파서 무결성 계약.
 *
 * 필수 값이 비어 있을 때 **어떤 경우에도 값이 만들어지지 않는지** 를 필드별로 확인한다.
 * 0원·0㎡ 같은 기본값이 하나라도 새어 나오면 화면에 가짜 실거래가가 뜨게 된다.
 */
class ParserIntegrityTest {

    /** 필드 하나를 비우거나 망가뜨린 응답을 만든다. */
    private fun tradeXml(
        aptNm: String = "<aptNm>무결성테스트</aptNm>",
        dealAmount: String = "<dealAmount>87,500</dealAmount>",
        excluUseAr: String = "<excluUseAr>84.97</excluUseAr>",
        dealYear: String = "<dealYear>2026</dealYear>",
        dealMonth: String = "<dealMonth>9</dealMonth>",
        dealDay: String = "<dealDay>3</dealDay>",
        umdNm: String = "<umdNm>역삼동</umdNm>",
    ) = """
        <response><header><resultCode>000</resultCode></header><body><items><item>
        $aptNm$dealAmount$excluUseAr$dealYear$dealMonth$dealDay$umdNm
        <sggCd>11680</sggCd>
        </item></items><totalCount>1</totalCount></body></response>
    """.trimIndent()

    private fun rentXml(
        aptNm: String = "<aptNm>무결성테스트</aptNm>",
        deposit: String = "<deposit>50,000</deposit>",
        monthlyRent: String = "<monthlyRent>0</monthlyRent>",
        excluUseAr: String = "<excluUseAr>84.97</excluUseAr>",
        dealYear: String = "<dealYear>2026</dealYear>",
        dealMonth: String = "<dealMonth>9</dealMonth>",
        dealDay: String = "<dealDay>3</dealDay>",
        umdNm: String = "<umdNm>역삼동</umdNm>",
    ) = """
        <response><header><resultCode>000</resultCode></header><body><items><item>
        $aptNm$deposit$monthlyRent$excluUseAr$dealYear$dealMonth$dealDay$umdNm
        <sggCd>11680</sggCd>
        </item></items><totalCount>1</totalCount></body></response>
    """.trimIndent()

    /** 값이 사라지는 여러 형태: 태그 없음 / 빈 태그 / 공백 / 숫자가 아닌 값 */
    private val vanishings = listOf(
        "태그 없음" to { _: String -> "" },
        "빈 태그" to { tag: String -> "<$tag></$tag>" },
        "공백만" to { tag: String -> "<$tag>   </$tag>" },
        "숫자 아님" to { tag: String -> "<$tag>미상</$tag>" },
    )

    @Test
    fun `거래금액이 없으면 어떤 형태로도 값을 만들지 않는다`() {
        vanishings.forEach { (name, make) ->
            val page = MolitParser.parseTrades(tradeXml(dealAmount = make("dealAmount")), "11680")
            assertEquals("$name: 금액 없는 행이 통과했다", 0, page.items.size)
            assertEquals("$name: 실패로 집계되어야 한다", 1, page.failures.size)
            assertTrue(page.failures.single().reason.contains("거래금액"))
        }
    }

    @Test
    fun `전용면적이 없으면 값을 만들지 않는다`() {
        vanishings.forEach { (name, make) ->
            val page = MolitParser.parseTrades(tradeXml(excluUseAr = make("excluUseAr")), "11680")
            assertEquals("$name: 면적 없는 행이 통과했다", 0, page.items.size)
            assertEquals(1, page.failures.size)
        }
    }

    @Test
    fun `전용면적이 0이면 거래로 보지 않는다`() {
        val page = MolitParser.parseTrades(tradeXml(excluUseAr = "<excluUseAr>0</excluUseAr>"), "11680")
        assertEquals(0, page.items.size)
        assertEquals(1, page.failures.size)
    }

    @Test
    fun `계약일 세 필드 중 하나만 빠져도 값을 만들지 않는다`() {
        listOf("dealYear", "dealMonth", "dealDay").forEach { field ->
            val xml = when (field) {
                "dealYear" -> tradeXml(dealYear = "")
                "dealMonth" -> tradeXml(dealMonth = "")
                else -> tradeXml(dealDay = "")
            }
            val page = MolitParser.parseTrades(xml, "11680")
            assertEquals("$field 없는 행이 통과했다", 0, page.items.size)
            assertTrue(page.failures.single().reason.contains("계약일"))
        }
    }

    @Test
    fun `존재하지 않는 날짜는 가까운 날로 보정하지 않는다`() {
        val page = MolitParser.parseTrades(
            tradeXml(dealMonth = "<dealMonth>2</dealMonth>", dealDay = "<dealDay>30</dealDay>"),
            "11680",
        )
        assertEquals("2월 30일을 2월 28일로 당기지 않는다", 0, page.items.size)
        assertEquals(1, page.failures.size)
    }

    @Test
    fun `단지명과 법정동이 없으면 값을 만들지 않는다`() {
        assertEquals(0, MolitParser.parseTrades(tradeXml(aptNm = ""), "11680").items.size)
        assertEquals(0, MolitParser.parseTrades(tradeXml(umdNm = ""), "11680").items.size)
    }

    @Test
    fun `보증금이 없으면 전월세 행도 통과하지 않는다`() {
        vanishings.forEach { (name, make) ->
            val page = MolitParser.parseRents(rentXml(deposit = make("deposit")), "11680")
            assertEquals("$name: 보증금 없는 행이 통과했다", 0, page.items.size)
            assertEquals(1, page.failures.size)
        }
    }

    @Test
    fun `월세액이 숫자가 아니면 0으로 때우지 않는다`() {
        val page = MolitParser.parseRents(
            rentXml(monthlyRent = "<monthlyRent>미상</monthlyRent>"),
            "11680",
        )
        assertEquals("숫자가 아닌 월세를 0(전세)으로 만들지 않는다", 0, page.items.size)
        assertTrue(page.failures.single().reason.contains("월세"))
    }

    @Test
    fun `실패한 행은 사유와 원문을 남긴다`() {
        val page = MolitParser.parseTrades(tradeXml(dealAmount = ""), "11680")
        val failure = page.failures.single()
        assertTrue("사유가 비어 있으면 원인을 추적할 수 없다", failure.reason.isNotBlank())
        assertTrue("원문이 없으면 어느 행인지 알 수 없다", failure.rawRow.isNotBlank())
        assertTrue(failure.rawRow.contains("무결성테스트"))
    }

    @Test
    fun `필수 값은 타입 자체가 값 없음을 허용하지 않는다`() {
        // Kotlin 의 non-null Long 은 JVM 에서 primitive long 이다.
        // 반대로 선택 값은 박싱된 타입이라, 구조만 봐도 필수/선택이 구분된다.
        assertEquals(
            "거래금액은 값이 없을 수 없어야 한다",
            java.lang.Long.TYPE,
            AptTrade::class.java.getDeclaredField("dealAmountManwon").type,
        )
        assertEquals(
            "전용면적은 값이 없을 수 없어야 한다",
            java.lang.Double.TYPE,
            AptTrade::class.java.getDeclaredField("exclusiveAreaM2").type,
        )
        assertEquals(
            "보증금은 값이 없을 수 없어야 한다",
            java.lang.Long.TYPE,
            AptRent::class.java.getDeclaredField("depositManwon").type,
        )
        // 층은 원자료에 없을 수 있으므로 박싱 타입이어야 한다.
        assertEquals(
            "층은 없을 수 있어야 한다",
            java.lang.Integer::class.java,
            AptTrade::class.java.getDeclaredField("floor").type,
        )
        assertEquals(
            java.lang.Integer::class.java,
            AptTrade::class.java.getDeclaredField("buildYear").type,
        )
    }
}
