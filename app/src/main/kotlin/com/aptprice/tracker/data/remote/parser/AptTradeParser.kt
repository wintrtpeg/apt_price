package com.aptprice.tracker.data.remote.parser

import com.aptprice.tracker.core.format.AreaFormatter
import com.aptprice.tracker.core.format.MoneyFormatter
import com.aptprice.tracker.core.time.TradeDateWindow
import com.aptprice.tracker.data.remote.parser.MolitXml.childInt
import com.aptprice.tracker.data.remote.parser.MolitXml.childText
import com.aptprice.tracker.domain.model.AptTrade
import org.w3c.dom.Element
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 국토교통부_아파트 매매 실거래자료(`getRTMSDataSvcAptTradeDev`) 응답의 `<item>` 을 읽는다.
 *
 * 필드명은 공공데이터포털에 공개된 응답 명세를 따랐다.
 * **주의**: 이 프로젝트가 개발된 환경에서는 data.go.kr 에 접속할 수 없어 실제 응답으로
 * 검증하지 못했다. 최초 연동 시 실응답 한 건을 받아 필드명이 맞는지 확인할 것.
 * 필드명이 어긋나면 값이 조용히 비는 게 아니라 [Result.failure] 로 떨어져
 * "읽지 못한 행" 으로 집계되므로, 어긋난 사실 자체는 드러난다.
 */
internal object AptTradeParser {

    private val REGISTER_DATE_FORMATS = listOf(
        DateTimeFormatter.ofPattern("yy.MM.dd"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
    )

    fun parseRow(row: Element, fallbackLawdCd: String): Result<AptTrade> {
        // 시군구 코드: 응답에 있으면 그 값을, 없으면 요청에 쓴 코드를 쓴다.
        val lawdCd = row.childText("sggCd", "지역코드")?.take(5) ?: fallbackLawdCd

        val umdNm = row.childText("umdNm", "법정동")
            ?: return fail("법정동명(umdNm/법정동) 없음")

        val aptName = row.childText("aptNm", "아파트")
            ?: return fail("단지명(aptNm/아파트) 없음")

        val areaRaw = row.childText("excluUseAr", "전용면적")
        val area = AreaFormatter.parseAreaM2(areaRaw)
            ?: return fail("전용면적(excluUseAr/전용면적) 해석 불가: ${areaRaw ?: "없음"}")

        val dealDate = TradeDateWindow.parseDealDate(
            year = row.childText("dealYear", "년"),
            month = row.childText("dealMonth", "월"),
            day = row.childText("dealDay", "일"),
        ) ?: return fail("계약일(dealYear/dealMonth/dealDay) 해석 불가")

        val amountRaw = row.childText("dealAmount", "거래금액")
        val amount = MoneyFormatter.parseManwon(amountRaw)
            ?: return fail("거래금액(dealAmount/거래금액) 해석 불가: ${amountRaw ?: "없음"}")

        // 해제여부(cdealType) 가 "O" 이면 계약 해제 건이다.
        val cancelType = row.childText("cdealType", "해제여부")
        val canceled = cancelType?.equals("O", ignoreCase = true) == true
        val canceledDate = row.childText("cdealDay", "해제사유발생일")?.let(::parseFlexibleDate)

        return Result.success(
            AptTrade(
                lawdCd = lawdCd,
                umdNm = umdNm,
                aptName = aptName,
                jibun = row.childText("jibun", "지번"),
                exclusiveAreaM2 = area,
                floor = row.childInt("floor", "층"),
                buildYear = row.childInt("buildYear", "건축년도"),
                dealDate = dealDate,
                canceled = canceled,
                canceledDate = canceledDate,
                dealAmountManwon = amount,
                dealingGbn = row.childText("dealingGbn", "거래유형"),
                registerDate = row.childText("rgstDate", "등기일자"),
            ),
        )
    }

    /** `yy.MM.dd` 또는 `yyyy-MM-dd` 로 오는 날짜. 해석 못하면 null (보정하지 않는다). */
    private fun parseFlexibleDate(raw: String): LocalDate? {
        val text = raw.trim()
        if (text.isEmpty() || text == "-") return null
        REGISTER_DATE_FORMATS.forEach { format ->
            runCatching { return LocalDate.parse(text, format) }
        }
        return null
    }

    private fun fail(reason: String): Result<AptTrade> = Result.failure(IllegalArgumentException(reason))
}
