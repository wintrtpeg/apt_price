package com.aptprice.tracker.presentation.detail

import com.aptprice.tracker.core.time.TradePeriod
import com.aptprice.tracker.domain.model.AptRent
import com.aptprice.tracker.domain.model.AptTrade
import com.aptprice.tracker.domain.model.ComplexAreaKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class DetailUiStateTest {

    private val key = ComplexAreaKey.of("11680", "역삼동", "상세테스트", 84.97)

    private fun trade(day: Int, amount: Long, canceled: Boolean = false, floor: Int? = 10) =
        AptTrade(
            lawdCd = "11680", umdNm = "역삼동", aptName = "상세테스트", jibun = null,
            exclusiveAreaM2 = 84.97, floor = floor, buildYear = 2005,
            dealDate = LocalDate.of(2026, 9, day), canceled = canceled, canceledDate = null,
            dealAmountManwon = amount, dealingGbn = null, registerDate = null,
        )

    private fun jeonse(day: Int, deposit: Long) = AptRent(
        lawdCd = "11680", umdNm = "역삼동", aptName = "상세테스트", jibun = null,
        exclusiveAreaM2 = 84.97, floor = 3, buildYear = 2005,
        dealDate = LocalDate.of(2026, 9, day), canceled = false, canceledDate = null,
        depositManwon = deposit, monthlyRentManwon = 0,
        contractType = null, contractTerm = null,
        previousDepositManwon = null, previousMonthlyRentManwon = null,
    )

    // ---------- 평형 칩 ----------

    @Test
    fun `단지의 평형이 칩으로 나오고 선택된 것이 표시된다`() {
        val chips = DetailUiState.areaChipsOf(listOf(59.99, 84.97, 114.20), key)

        assertEquals(3, chips.size)
        // 칩에도 실제 평수를 괄호로 병기한다. ㎡ 만으로는 몇 평인지 감이 오지 않는다.
        assertEquals(
            listOf("59.99㎡ (18.1평)", "84.97㎡ (25.7평)", "114.2㎡ (34.5평)"),
            chips.map { it.label },
        )
        assertEquals(listOf(false, true, false), chips.map { it.selected })
        assertEquals("전용 84.97㎡ (25.7평) · 30평대", chips[1].detailLabel)
    }

    @Test
    fun `칩은 같은 단지의 다른 평형 키를 갖는다`() {
        val chips = DetailUiState.areaChipsOf(listOf(59.99, 84.97), key)

        assertTrue(chips.all { it.key.complexKey == key.complexKey })
        assertEquals("11680|역삼동|상세테스트|59.99", chips[0].key.raw)
    }

    @Test
    fun `평형은 작은 순으로 정렬된다`() {
        val chips = DetailUiState.areaChipsOf(listOf(114.20, 59.99, 84.97), key)
        assertEquals(listOf(59.99, 84.97, 114.20), chips.map { it.areaM2 })
    }

    // ---------- 거래 이력 ----------

    @Test
    fun `이력은 매매와 전세를 합쳐 최신순으로 보여준다`() {
        val rows = DetailUiState.historyOf(
            trades = listOf(trade(1, 80_000), trade(3, 90_000)),
            rents = listOf(jeonse(2, 50_000)),
            peakAmountManwon = 90_000,
        )

        assertEquals(3, rows.size)
        assertEquals(listOf("2026-09-03", "2026-09-02", "2026-09-01"), rows.map { it.dateLabel })
        assertEquals(listOf("매매", "전세", "매매"), rows.map { it.typeLabel })
    }

    @Test
    fun `최고가 건이 표시된다`() {
        val rows = DetailUiState.historyOf(
            trades = listOf(trade(1, 80_000), trade(3, 90_000)),
            rents = emptyList(),
            peakAmountManwon = 90_000,
        )
        assertEquals(1, rows.count { it.isPeak })
        assertEquals("9억", rows.first { it.isPeak }.priceLabel)
    }

    @Test
    fun `해제 건은 이력에 남되 최고가로 보지 않는다`() {
        val rows = DetailUiState.historyOf(
            trades = listOf(trade(1, 80_000), trade(3, 300_000, canceled = true)),
            rents = emptyList(),
            peakAmountManwon = 80_000,
        )

        assertEquals("해제 건도 신고된 사실이므로 이력에는 남는다", 2, rows.size)
        val canceledRow = rows.first { it.canceled }
        assertEquals("30억", canceledRow.priceLabel)
        assertFalse("해제 건은 최고가가 아니다", canceledRow.isPeak)
    }

    @Test
    fun `층이 없는 거래는 층 표기를 비운다`() {
        val rows = DetailUiState.historyOf(
            trades = listOf(trade(1, 80_000, floor = null)),
            rents = emptyList(),
            peakAmountManwon = null,
        )
        assertNull(rows.single().floorLabel)
    }

    @Test
    fun `매매가 없으면 최고가 표시도 없다`() {
        val rows = DetailUiState.historyOf(
            trades = emptyList(),
            rents = listOf(jeonse(2, 50_000)),
            peakAmountManwon = null,
        )
        assertEquals(1, rows.size)
        assertTrue(rows.none { it.isPeak })
    }

    // ---------- 화면 상태 ----------

    @Test
    fun `차트 기본 기간은 1년이고 5년까지 고를 수 있다`() {
        assertEquals(TradePeriod.ONE_YEAR, DetailUiState().period)
        assertTrue(TradePeriod.FIVE_YEARS in TradePeriod.chartOptions)
        assertFalse("2주는 차트에 점이 너무 적다", TradePeriod.TWO_WEEKS in TradePeriod.chartOptions)
    }

    @Test
    fun `선택된 평형이 제목에 표기된다`() {
        val state = DetailUiState(key = key)
        // 평형대("30평대")가 아니라 그 타입의 실제 평수를 보여 준다.
        assertEquals("84.97㎡ (25.7평)", state.areaLabel)
    }

    @Test
    fun `지역 문구가 키에서 만들어진다`() {
        assertEquals("강남구 역삼동", DetailUiState.regionLabelOf(key))
    }

    @Test
    fun `출처는 상세 화면에도 표기된다`() {
        val notSynced = DetailUiState()
        assertTrue(notSynced.attributionLabel().contains("국토교통부 실거래가 공개시스템"))

        val synced = DetailUiState(lastFetchedAt = Instant.parse("2026-09-04T02:20:00Z"))
        assertEquals(
            "데이터 출처: 국토교통부 실거래가 공개시스템 (기준일시: 2026-09-04 02:20)",
            synced.attributionLabel(ZoneOffset.UTC),
        )
    }

    @Test
    fun `거래가 없으면 없다고 말한다`() {
        val message = DetailUiState.emptyMessageFor(TradePeriod.FIVE_YEARS)
        assertTrue(message.contains("최근 5년"))
        assertTrue(message.contains("거래 데이터 없음"))
    }
}
