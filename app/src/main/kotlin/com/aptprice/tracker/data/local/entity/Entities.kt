package com.aptprice.tracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 캐시된 매매 실거래 한 건.
 *
 * 국토교통부 자료에는 행을 고유하게 가리키는 ID 가 없다. 대신 캐시 단위인
 * (lawdCd, dealYmd) 를 통째로 지우고 다시 넣는 방식으로 갱신하므로 중복이 생기지 않는다.
 * 이 방식은 뒤늦게 반영된 계약 해제나 지연 신고까지 정확히 따라간다.
 */
@Entity(
    tableName = "apt_trade",
    indices = [
        Index("lawdCd", "dealYmd"),
        Index("dealDateEpochDay"),
        Index("complexAreaKey"),
        Index("complexKey"),
    ],
)
data class TradeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 캐시 단위: 시군구 코드 */
    val lawdCd: String,
    /** 캐시 단위: 계약월 (YYYYMM) */
    val dealYmd: String,
    val umdNm: String,
    val aptName: String,
    val jibun: String?,
    val exclusiveAreaM2: Double,
    val floor: Int?,
    val buildYear: Int?,
    /** 계약일. 구간 조회를 위해 epochDay 로 저장한다. */
    val dealDateEpochDay: Long,
    val canceled: Boolean,
    val canceledDateEpochDay: Long?,
    val dealAmountManwon: Long,
    val dealingGbn: String?,
    val registerDate: String?,
    /** 단지 + 평형 묶음 키 (차트 그룹핑용) */
    val complexAreaKey: String,
    /** 단지 묶음 키 */
    val complexKey: String,
)

/** 캐시된 전월세 실거래 한 건. 월세액이 0 이면 전세다. */
@Entity(
    tableName = "apt_rent",
    indices = [
        Index("lawdCd", "dealYmd"),
        Index("dealDateEpochDay"),
        Index("complexAreaKey"),
        Index("complexKey"),
        Index("monthlyRentManwon"),
    ],
)
data class RentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lawdCd: String,
    val dealYmd: String,
    val umdNm: String,
    val aptName: String,
    val jibun: String?,
    val exclusiveAreaM2: Double,
    val floor: Int?,
    val buildYear: Int?,
    val dealDateEpochDay: Long,
    val depositManwon: Long,
    /** 0 이면 전세 */
    val monthlyRentManwon: Long,
    val contractType: String?,
    val contractTerm: String?,
    val previousDepositManwon: Long?,
    val previousMonthlyRentManwon: Long?,
    val complexAreaKey: String,
    val complexKey: String,
)

/**
 * (지역 × 계약월 × 엔드포인트) 단위 동기화 기록.
 *
 * 이 표가 있어야 "이미 받아온 구간" 을 건너뛸 수 있다.
 * 5년 조회에서 재조회를 막아 주는 핵심이다.
 */
@Entity(tableName = "sync_state", primaryKeys = ["lawdCd", "dealYmd", "endpoint"])
data class SyncStateEntity(
    val lawdCd: String,
    val dealYmd: String,
    /** [SyncEndpoint.name] */
    val endpoint: String,
    val fetchedAtEpochMillis: Long,
    /** 저장에 성공한 행 수 */
    val rowCount: Int,
    /** 서버가 알려준 전체 건수 */
    val totalCount: Int,
    /** 읽지 못해 제외한 행 수. 0 이 아니면 파서 점검이 필요하다. */
    val failureCount: Int,
)

/**
 * 단지 검색 결과 한 줄 (Room 프로젝션).
 * 매매·전월세 양쪽에서 단지명을 찾아 합친 것이다.
 */
data class ComplexSearchRow(
    val complexKey: String,
    val aptName: String,
    val lawdCd: String,
    val umdNm: String,
    val latestEpochDay: Long,
    /** 가장 최근 거래의 전용면적. 상세 화면을 어느 평형으로 열지 정하는 데 쓴다. */
    val latestAreaM2: Double,
    /** 받아온 자료 안에서 이 단지의 거래 건수 (매매 + 전월세) */
    val dealCount: Int,
)

/**
 * 카드 미니 그래프용 한 점 (Room 프로젝션).
 *
 * 카드마다 따로 조회하면 목록이 100장일 때 쿼리도 100번이다.
 * 화면에 있는 키를 한 번에 넘겨 한 방에 가져온다.
 */
data class SparkRow(
    val complexAreaKey: String,
    val dealDateEpochDay: Long,
    val amountManwon: Long,
)

/** 캐시 단위를 나누는 엔드포인트 구분. */
enum class SyncEndpoint {
    /** 아파트 매매 실거래자료 */
    TRADE,

    /** 아파트 전월세 자료 (전세 + 월세) */
    RENT,
}
