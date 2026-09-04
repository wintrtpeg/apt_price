package com.aptprice.tracker.data.remote.parser

import com.aptprice.tracker.core.format.AreaFormatter
import com.aptprice.tracker.core.format.MoneyFormatter
import com.aptprice.tracker.core.time.TradeDateWindow
import com.aptprice.tracker.data.remote.parser.MolitXml.childInt
import com.aptprice.tracker.data.remote.parser.MolitXml.childText
import com.aptprice.tracker.domain.model.AptRent
import org.w3c.dom.Element

/**
 * 국토교통부_아파트 전월세 자료(`getRTMSDataSvcAptRent`) 응답의 `<item>` 을 읽는다.
 *
 * 전세와 월세는 한 엔드포인트로 함께 내려오고 월세액(`monthlyRent`) 이 0 인지로만 구분된다.
 *
 * **주의**: [AptTradeParser] 와 같은 이유로 필드명은 실응답으로 검증되지 않았다.
 */
internal object AptRentParser {

    fun parseRow(row: Element, fallbackLawdCd: String): Result<AptRent> {
        val lawdCd = row.childText("sggCd", "지역코드")?.take(5) ?: fallbackLawdCd

        val umdNm = row.childText("umdNm", "법정동")
            ?: return fail("법정동명(umdNm/법정동) 없음")

        val aptName = row.childText("aptNm", "아파트")
            ?: return fail("단지명(aptNm/아파트) 없음")

        val areaRaw = row.childText("excluUseAr", "전용면적")
        val area = AreaFormatter.parseAreaM2(areaRaw)
            ?: return fail("전용면적(excluUseAr) 해석 불가: ${areaRaw ?: "없음"}")

        val dealDate = TradeDateWindow.parseDealDate(
            year = row.childText("dealYear", "년"),
            month = row.childText("dealMonth", "월"),
            day = row.childText("dealDay", "일"),
        ) ?: return fail("계약일(dealYear/dealMonth/dealDay) 해석 불가")

        val depositRaw = row.childText("deposit", "보증금액")
        val deposit = MoneyFormatter.parseManwon(depositRaw)
            ?: return fail("보증금(deposit) 해석 불가: ${depositRaw ?: "없음"}")

        // 월세는 전세일 때 "0" 으로 오거나 태그가 비어 있다. 둘 다 0 으로 본다.
        // 다만 "값이 있는데 숫자가 아닌" 경우는 실패로 처리한다 (조용히 0 으로 만들지 않는다).
        val monthlyRaw = row.childText("monthlyRent", "월세금액")
        val monthly = when {
            monthlyRaw == null -> 0L
            else -> MoneyFormatter.parseManwon(monthlyRaw)
                ?: return fail("월세(monthlyRent) 해석 불가: $monthlyRaw")
        }

        return Result.success(
            AptRent(
                lawdCd = lawdCd,
                umdNm = umdNm,
                aptName = aptName,
                jibun = row.childText("jibun", "지번"),
                exclusiveAreaM2 = area,
                floor = row.childInt("floor", "층"),
                buildYear = row.childInt("buildYear", "건축년도"),
                dealDate = dealDate,
                // 전월세 자료에는 해제 관련 필드가 없다. 없는 값을 지어내지 않는다.
                canceled = false,
                canceledDate = null,
                depositManwon = deposit,
                monthlyRentManwon = monthly,
                contractType = row.childText("contractType", "계약구분"),
                contractTerm = row.childText("contractTerm", "계약기간"),
                previousDepositManwon = row.childText("preDeposit", "종전계약보증금")
                    ?.let(MoneyFormatter::parseManwon),
                previousMonthlyRentManwon = row.childText("preMonthlyRent", "종전계약월세")
                    ?.let(MoneyFormatter::parseManwon),
            ),
        )
    }

    private fun fail(reason: String): Result<AptRent> = Result.failure(IllegalArgumentException(reason))
}
