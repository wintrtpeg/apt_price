package com.aptprice.tracker.presentation.feed

/** 직전 거래 대비 방향. 색으로 구분한다. */
enum class ChangeDirection {
    UP,
    DOWN,
    FLAT,

    /** 비교할 직전 거래가 없음. 등락률을 표시하지 않는다. */
    NONE,
}

/**
 * 피드 카드 한 장에 그릴 값.
 *
 * 모든 문자열은 국토교통부 원자료에서 나온 값을 포맷한 결과다.
 * 원자료에 없는 값은 `null` 로 두고 화면에서 자리를 비운다. 채워 넣지 않는다.
 */
data class FeedItemUi(
    /** 목록 키. 같은 단지·평형·계약일·금액이면 같은 값. */
    val id: String,
    /** 상세 화면으로 넘길 단지+평형 키 */
    val complexAreaKey: String,
    val aptName: String,
    /** 예) `강남구 역삼동` */
    val regionLabel: String,
    /** 예) `84.97㎡ (25.7평)` */
    val areaLabel: String,
    /** 예) `중형` */
    val areaBucketLabel: String,
    /** 예) `10층`. 원자료에 층이 없으면 null. */
    val floorLabel: String?,
    /** 예) `09.03 (목)` */
    val dateLabel: String,
    /** 예) `어제` */
    val relativeDateLabel: String,
    /** 예) `8억 7,500만원` / 월세는 `1억 / 120만원` */
    val priceLabel: String,
    /** 월세 탭에서 보증금·월세를 나눠 보여줄 때 쓰는 보조 라벨. */
    val priceSubLabel: String?,
    /** 예) `+3.4%`. 비교 대상이 없으면 null. */
    val changeLabel: String?,
    val changeDirection: ChangeDirection,
    /** 예) `2005년 준공`. 원자료에 없으면 null. */
    val buildYearLabel: String?,
    /** 계약 해제 건이면 true — 취소선 + 배지 */
    val canceled: Boolean,
    /** 정렬용 원본 값 */
    val dealDateEpochDay: Long,
    val sortAmountManwon: Long,
)
