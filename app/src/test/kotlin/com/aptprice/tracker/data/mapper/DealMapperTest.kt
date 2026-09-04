package com.aptprice.tracker.data.mapper

import com.aptprice.tracker.domain.model.AptRent
import com.aptprice.tracker.domain.model.AptTrade
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DealMapperTest {

    private val trade = AptTrade(
        lawdCd = "11680",
        umdNm = "역삼동",
        aptName = "매핑테스트아파트",
        jibun = "101",
        exclusiveAreaM2 = 84.97,
        floor = 10,
        buildYear = 2005,
        dealDate = LocalDate.of(2026, 9, 3),
        canceled = true,
        canceledDate = LocalDate.of(2026, 9, 20),
        dealAmountManwon = 87_500,
        dealingGbn = "중개거래",
        registerDate = "26.09.15",
    )

    private val rent = AptRent(
        lawdCd = "41135",
        umdNm = "정자동",
        aptName = "매핑테스트아파트",
        jibun = "200",
        exclusiveAreaM2 = 59.99,
        floor = 5,
        buildYear = 2018,
        dealDate = LocalDate.of(2026, 9, 4),
        canceled = false,
        canceledDate = null,
        depositManwon = 10_000,
        monthlyRentManwon = 120,
        contractType = "갱신",
        contractTerm = "25.09~27.09",
        previousDepositManwon = 8_000,
        previousMonthlyRentManwon = 100,
    )

    @Test
    fun `매매는 엔티티로 갔다 와도 값이 그대로다`() {
        assertEquals(trade, trade.toEntity("202609").toDomain())
    }

    @Test
    fun `전월세는 엔티티로 갔다 와도 값이 그대로다`() {
        assertEquals(rent, rent.toEntity("202609").toDomain())
    }

    @Test
    fun `캐시 단위인 계약월이 엔티티에 남는다`() {
        assertEquals("202609", trade.toEntity("202609").dealYmd)
        assertEquals("202609", rent.toEntity("202609").dealYmd)
    }

    @Test
    fun `계약일은 epochDay 로 저장된다`() {
        val entity = trade.toEntity("202609")
        assertEquals(LocalDate.of(2026, 9, 3).toEpochDay(), entity.dealDateEpochDay)
        assertEquals(LocalDate.of(2026, 9, 20).toEpochDay(), entity.canceledDateEpochDay)
    }

    @Test
    fun `단지 평형 키로 같은 타입끼리 묶인다`() {
        val sameArea = trade.copy(floor = 20, dealAmountManwon = 90_000)
        val otherArea = trade.copy(exclusiveAreaM2 = 59.99)

        assertEquals(trade.complexAreaKey, sameArea.complexAreaKey)
        assertEquals(trade.complexKey, otherArea.complexKey)
        assert(trade.complexAreaKey != otherArea.complexAreaKey)
    }

    @Test
    fun `전용면적이 소수점 셋째 자리에서만 달라도 같은 평형으로 묶인다`() {
        val a = trade.copy(exclusiveAreaM2 = 84.970)
        val b = trade.copy(exclusiveAreaM2 = 84.9701)
        assertEquals(a.complexAreaKey, b.complexAreaKey)
    }
}
