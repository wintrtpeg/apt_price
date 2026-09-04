package com.aptprice.tracker.domain.model

import com.aptprice.tracker.core.format.AreaBucket
import java.time.LocalDate

/** 메인 피드의 3-Way 탭. */
enum class DealTab(val label: String) {
    SALE("매매"),
    JEONSE("전세"),
    MONTHLY("월세"),
}

/**
 * 국토교통부에 신고된 실거래 한 건.
 *
 * 모든 필드는 원자료에서 실제로 읽어낸 값이다. 원자료에 없는 값은 nullable 로 두고,
 * 임의의 기본값이나 추정치로 채우지 않는다.
 */
sealed interface AptDeal {
    /** 시군구 코드 (LAWD_CD 5자리) */
    val lawdCd: String

    /** 법정동명. 화성시 동탄 필터링의 기준이 된다. */
    val umdNm: String

    /** 단지명 */
    val aptName: String

    /** 지번. 같은 이름의 다른 단지를 구분하는 데 쓴다. */
    val jibun: String?

    /** 전용면적(㎡) */
    val exclusiveAreaM2: Double

    /** 층. 원자료에 없거나 읽을 수 없으면 null. */
    val floor: Int?

    /** 준공연도. 원자료에 없으면 null. */
    val buildYear: Int?

    /** 계약일 */
    val dealDate: LocalDate

    /** 계약 해제 여부. 해제 건은 목록에서 취소선 처리하거나 제외한다. */
    val canceled: Boolean

    /** 계약 해제일. 해제 건이 아니면 null. */
    val canceledDate: LocalDate?

    /** 어느 탭에 속하는가. */
    val tab: DealTab

    /** 평형대 (소형/중형/대형) */
    val areaBucket: AreaBucket get() = AreaBucket.of(exclusiveAreaM2)

    /**
     * 같은 단지·같은 평형을 묶는 키. 상세 화면의 시계열 차트가 이 키로 거래를 모은다.
     * 전용면적은 소수점 둘째 자리까지만 써서 같은 타입이 갈라지지 않게 한다.
     */
    val complexAreaKey: String
        get() = ComplexAreaKey.of(lawdCd, umdNm, aptName, exclusiveAreaM2).raw

    /** 단지 단위 키 (평형 무관) */
    val complexKey: String
        get() = "$lawdCd${ComplexAreaKey.SEPARATOR}$umdNm${ComplexAreaKey.SEPARATOR}$aptName"
}

/** 아파트 매매 실거래 (getRTMSDataSvcAptTradeDev) */
data class AptTrade(
    override val lawdCd: String,
    override val umdNm: String,
    override val aptName: String,
    override val jibun: String?,
    override val exclusiveAreaM2: Double,
    override val floor: Int?,
    override val buildYear: Int?,
    override val dealDate: LocalDate,
    override val canceled: Boolean,
    override val canceledDate: LocalDate?,
    /** 거래금액 (만원 단위) */
    val dealAmountManwon: Long,
    /** 거래유형: 중개거래 / 직거래. 원자료에 없으면 null. */
    val dealingGbn: String?,
    /** 등기일자 원문. 원자료에 없으면 null. */
    val registerDate: String?,
) : AptDeal {
    override val tab: DealTab get() = DealTab.SALE
}

/** 아파트 전월세 실거래 (getRTMSDataSvcAptRent) */
data class AptRent(
    override val lawdCd: String,
    override val umdNm: String,
    override val aptName: String,
    override val jibun: String?,
    override val exclusiveAreaM2: Double,
    override val floor: Int?,
    override val buildYear: Int?,
    override val dealDate: LocalDate,
    override val canceled: Boolean,
    override val canceledDate: LocalDate?,
    /** 보증금 (만원 단위) */
    val depositManwon: Long,
    /** 월세 (만원 단위). 0 이면 전세. */
    val monthlyRentManwon: Long,
    /** 계약구분: 신규 / 갱신. 원자료에 없으면 null. */
    val contractType: String?,
    /** 계약기간 원문 (예: "25.09~27.09"). 원자료에 없으면 null. */
    val contractTerm: String?,
    /** 종전 보증금 (만원). 갱신 계약에만 있다. */
    val previousDepositManwon: Long?,
    /** 종전 월세 (만원). 갱신 계약에만 있다. */
    val previousMonthlyRentManwon: Long?,
) : AptDeal {

    /**
     * 월세액이 0 이면 전세, 아니면 월세.
     * 국토교통부 전월세 자료는 전세·월세를 한 엔드포인트로 내려주고 월세액으로만 구분된다.
     */
    val isJeonse: Boolean get() = monthlyRentManwon == 0L

    override val tab: DealTab
        get() = if (isJeonse) DealTab.JEONSE else DealTab.MONTHLY
}
