package com.aptprice.tracker.domain.repository

import com.aptprice.tracker.core.time.TradePeriod
import com.aptprice.tracker.core.time.TradeQueryPlan
import com.aptprice.tracker.domain.model.AptDeal
import com.aptprice.tracker.domain.model.AptRent
import com.aptprice.tracker.domain.model.AptTrade
import com.aptprice.tracker.domain.model.ComplexSummary
import com.aptprice.tracker.domain.model.DealTab
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * 실거래 데이터 접근 창구.
 *
 * 읽기는 항상 로컬 캐시(Room)에서 나오고, 네트워크 조회는 [sync] 로만 일어난다.
 * 화면은 캐시를 구독하다가 동기화가 끝나면 자동으로 갱신된다.
 */
interface TradeRepository {

    /**
     * 기간 · 지역 · 탭에 해당하는 실거래 목록. 최신 계약일 순.
     * 캐시에 있는 것만 나오므로, 화면은 이 Flow 를 구독한 채로 [sync] 를 호출하면 된다.
     */
    fun observeDeals(
        period: TradePeriod,
        lawdCodes: List<String>,
        tab: DealTab,
    ): Flow<List<AptDeal>>

    /** 단지 + 평형의 매매 시계열 (차트용). 오래된 순. */
    fun observeTradeSeries(complexAreaKey: String, period: TradePeriod): Flow<List<AptTrade>>

    /** 단지 + 평형의 전세 시계열 (매매와 겹쳐 그리기 위한 것). 오래된 순. */
    fun observeJeonseSeries(complexAreaKey: String, period: TradePeriod): Flow<List<AptRent>>

    /**
     * 단지명으로 검색한다. **이미 받아온 자료 안에서만** 찾는다.
     * 두 글자 미만이면 빈 목록을 돌려준다.
     */
    fun searchComplexes(query: String): Flow<List<ComplexSummary>>

    /** 단지 안에 존재하는 전용면적 목록 (평형 선택 칩용). */
    fun observeAreasOfComplex(complexKey: String): Flow<List<Double>>

    /**
     * 계획된 구간을 받아온다. 캐시가 유효한 구간은 건너뛴다.
     *
     * 인증키 오류나 트래픽 초과처럼 계속 시도해도 소용없는 오류를 만나면
     * 남은 구간을 포기하고 즉시 돌아온다. (2천 건을 헛되이 때리지 않기 위한 것)
     *
     * [onProgress] 는 여러 스레드에서 불릴 수 있다. 값 자체는 단조 증가가 보장되지만,
     * 콜백 안에서 UI 를 직접 만지지 말고 상태 갱신만 할 것.
     */
    suspend fun sync(
        plan: TradeQueryPlan,
        onProgress: (SyncProgress) -> Unit = {},
    ): SyncReport

    /** 마지막으로 무언가를 받아온 시각. 화면의 "기준일시" 표기에 쓴다. */
    suspend fun lastFetchedAt(): Instant?
}
