package com.aptprice.tracker.data.mapper

import com.aptprice.tracker.data.local.entity.RentEntity
import com.aptprice.tracker.data.local.entity.TradeEntity
import com.aptprice.tracker.domain.model.AptRent
import com.aptprice.tracker.domain.model.AptTrade
import java.time.LocalDate

/**
 * 도메인 ↔ Room 엔티티 변환.
 *
 * 날짜는 구간 조회를 위해 epochDay(Long) 로 저장한다.
 * 값을 만들어 내거나 잃어버리는 변환은 없다.
 */

fun AptTrade.toEntity(dealYmd: String): TradeEntity = TradeEntity(
    lawdCd = lawdCd,
    dealYmd = dealYmd,
    umdNm = umdNm,
    aptName = aptName,
    jibun = jibun,
    exclusiveAreaM2 = exclusiveAreaM2,
    floor = floor,
    buildYear = buildYear,
    dealDateEpochDay = dealDate.toEpochDay(),
    canceled = canceled,
    canceledDateEpochDay = canceledDate?.toEpochDay(),
    dealAmountManwon = dealAmountManwon,
    dealingGbn = dealingGbn,
    registerDate = registerDate,
    complexAreaKey = complexAreaKey,
    complexKey = complexKey,
)

fun TradeEntity.toDomain(): AptTrade = AptTrade(
    lawdCd = lawdCd,
    umdNm = umdNm,
    aptName = aptName,
    jibun = jibun,
    exclusiveAreaM2 = exclusiveAreaM2,
    floor = floor,
    buildYear = buildYear,
    dealDate = LocalDate.ofEpochDay(dealDateEpochDay),
    canceled = canceled,
    canceledDate = canceledDateEpochDay?.let(LocalDate::ofEpochDay),
    dealAmountManwon = dealAmountManwon,
    dealingGbn = dealingGbn,
    registerDate = registerDate,
)

fun AptRent.toEntity(dealYmd: String): RentEntity = RentEntity(
    lawdCd = lawdCd,
    dealYmd = dealYmd,
    umdNm = umdNm,
    aptName = aptName,
    jibun = jibun,
    exclusiveAreaM2 = exclusiveAreaM2,
    floor = floor,
    buildYear = buildYear,
    dealDateEpochDay = dealDate.toEpochDay(),
    depositManwon = depositManwon,
    monthlyRentManwon = monthlyRentManwon,
    contractType = contractType,
    contractTerm = contractTerm,
    previousDepositManwon = previousDepositManwon,
    previousMonthlyRentManwon = previousMonthlyRentManwon,
    complexAreaKey = complexAreaKey,
    complexKey = complexKey,
)

fun RentEntity.toDomain(): AptRent = AptRent(
    lawdCd = lawdCd,
    umdNm = umdNm,
    aptName = aptName,
    jibun = jibun,
    exclusiveAreaM2 = exclusiveAreaM2,
    floor = floor,
    buildYear = buildYear,
    dealDate = LocalDate.ofEpochDay(dealDateEpochDay),
    // 전월세 자료에는 해제 필드가 없다.
    canceled = false,
    canceledDate = null,
    depositManwon = depositManwon,
    monthlyRentManwon = monthlyRentManwon,
    contractType = contractType,
    contractTerm = contractTerm,
    previousDepositManwon = previousDepositManwon,
    previousMonthlyRentManwon = previousMonthlyRentManwon,
)
