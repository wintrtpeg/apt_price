package com.aptprice.tracker.presentation.detail

import com.aptprice.tracker.core.attribution.DataSourceAttribution
import com.aptprice.tracker.core.format.AreaBucket
import com.aptprice.tracker.core.format.AreaFormatter
import com.aptprice.tracker.core.format.DateFormatter
import com.aptprice.tracker.core.format.MoneyFormatter
import com.aptprice.tracker.core.time.TradePeriod
import com.aptprice.tracker.domain.model.AptRent
import com.aptprice.tracker.domain.model.AptTrade
import com.aptprice.tracker.domain.model.ComplexAreaKey
import com.aptprice.tracker.domain.region.RegionCatalog
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.Instant

/** 평형 선택 칩 하나. */
data class AreaChip(
    val areaM2: Double,
    /** 예) `26평` */
    val label: String,
    /** 예) `전용 84.97㎡` */
    val detailLabel: String,
    val key: ComplexAreaKey,
    val selected: Boolean,
)

/** 거래 이력 표의 한 줄. */
data class HistoryRow(
    val id: String,
    /** 예) `2026-09-03` */
    val dateLabel: String,
    /** 예) `매매` / `전세` */
    val typeLabel: String,
    /** 매매인가 전세인가. 화면이 색으로 구분할 때 문자열을 비교하지 않도록 둔다. */
    val isSale: Boolean,
    /** 예) `8억 7,500만원` */
    val priceLabel: String,
    /** 예) `10층`. 원자료에 없으면 null. */
    val floorLabel: String?,
    /** 기간 내 최고가와 같은 건인가. */
    val isPeak: Boolean,
    val canceled: Boolean,
)

/** 단지 상세 화면 상태. */
data class DetailUiState(
    val key: ComplexAreaKey? = null,
    val aptName: String = "",
    /** 예) `강남구 역삼동` */
    val regionLabel: String = "",
    val period: TradePeriod = TradePeriod.CHART_DEFAULT,
    val areaChips: List<AreaChip> = emptyList(),
    val chart: PriceChartData? = null,
    val history: List<HistoryRow> = emptyList(),
    val isLoading: Boolean = true,
    val lastFetchedAt: Instant? = null,
    /** 조회 결과가 없을 때의 문구. 없으면 null. */
    val emptyMessage: String? = null,
) {
    /**
     * 선택된 평형의 표기. 예) `84.69㎡ (25.6평)`
     *
     * "30평대" 같은 평형대가 아니라 **그 타입의 실제 평수**를 괄호로 병기한다.
     * 같은 30평대 안에서도 84.69㎡ 와 75.93㎡ 는 다른 집이라, 평형대만으로는
     * 지금 보고 있는 것이 무엇인지 알 수 없다.
     */
    val areaLabel: String
        get() = key?.areaM2?.let(AreaFormatter::formatWithPyeong).orEmpty()

    fun attributionLabel(zone: ZoneId = ZoneId.systemDefault()): String =
        lastFetchedAt
            ?.let { DataSourceAttribution.label(LocalDateTime.ofInstant(it, zone)) }
            ?: DataSourceAttribution.LABEL_NOT_SYNCED

    companion object {

        fun emptyMessageFor(period: TradePeriod): String =
            "${period.label} 기준 ${DataSourceAttribution.EMPTY_RESULT}"

        /** 단지 안의 전용면적 목록을 선택 칩으로 만든다. */
        fun areaChipsOf(
            areas: List<Double>,
            selected: ComplexAreaKey,
        ): List<AreaChip> {
            val complexKey = selected.complexKey
            val selectedArea = ComplexAreaKey.formatArea(selected.areaM2 ?: return emptyList())
            return areas.sorted().map { area ->
                AreaChip(
                    areaM2 = area,
                    // 같은 단지 안에서 타입을 고르는 자리라 전용면적을 그대로 보여 준다.
                    // 평형대(30평대 등)로는 서로 다른 타입이 한 칩으로 뭉개진다.
                    // 칩에도 실제 평수를 괄호로 함께 적는다. ㎡ 만으로는 몇 평인지 감이 안 온다.
                    label = AreaFormatter.formatWithPyeong(area),
                    detailLabel = "전용 ${AreaFormatter.formatWithPyeong(area)} · ${AreaBucket.of(area).label}",
                    key = ComplexAreaKey.ofComplex(complexKey, area),
                    selected = ComplexAreaKey.formatArea(area) == selectedArea,
                )
            }
        }

        /**
         * 거래 이력 표. 최신순.
         * 해제 건도 남기되 표시로 구분한다 — 신고된 사실 자체는 원자료에 있기 때문이다.
         */
        fun historyOf(
            trades: List<AptTrade>,
            rents: List<AptRent>,
            peakAmountManwon: Long?,
        ): List<HistoryRow> {
            val tradeRows = trades.map { trade ->
                HistoryRow(
                    id = "S|${trade.dealDate}|${trade.floor}|${trade.dealAmountManwon}",
                    dateLabel = DateFormatter.formatIso(trade.dealDate),
                    typeLabel = "매매",
                    isSale = true,
                    priceLabel = MoneyFormatter.formatManwon(trade.dealAmountManwon),
                    floorLabel = trade.floor?.let { "${it}층" },
                    isPeak = !trade.canceled && trade.dealAmountManwon == peakAmountManwon,
                    canceled = trade.canceled,
                )
            }
            val rentRows = rents.map { rent ->
                HistoryRow(
                    id = "J|${rent.dealDate}|${rent.floor}|${rent.depositManwon}",
                    dateLabel = DateFormatter.formatIso(rent.dealDate),
                    typeLabel = "전세",
                    isSale = false,
                    priceLabel = MoneyFormatter.formatManwon(rent.depositManwon),
                    floorLabel = rent.floor?.let { "${it}층" },
                    isPeak = false,
                    canceled = rent.canceled,
                )
            }
            // 같은 값의 거래가 실제로 존재하므로(원자료에 동 정보가 없어 구분 불가),
            // 목록 키가 겹치지 않도록 번호를 붙인다. Compose 는 키가 겹치면 예외를 던진다.
            val counts = mutableMapOf<String, Int>()
            return (tradeRows + rentRows)
                .sortedByDescending { it.dateLabel }
                .map { row ->
                    val seen = counts.merge(row.id, 1, Int::plus) ?: 1
                    if (seen == 1) row else row.copy(id = "${row.id}#$seen")
                }
        }

        /** 키에서 화면 제목에 쓸 지역 문구를 만든다. */
        fun regionLabelOf(key: ComplexAreaKey): String =
            listOfNotNull(
                RegionCatalog.byLawdCd(key.lawdCd)?.displayName,
                key.umdNm.takeIf { it.isNotEmpty() },
            ).joinToString(" ")
    }
}
