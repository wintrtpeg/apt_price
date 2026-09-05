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
     * 카드 미니 그래프용 금액 흐름. 키마다 **오래된 순**으로 준다.
     *
     * 카드 한 장씩 조회하면 목록이 100장일 때 쿼리도 100번이므로 키를 한 번에 받는다.
     * 목록과 달리 기간으로 자르지 않는다 — 기본 2주로 자르면 대부분 한 건만 남아
     * 그릴 것이 없다. 받아 둔 자료 안에서의 흐름을 그대로 보여 준다.
     *
     * 월세 탭은 보증금 흐름이다 (목록·요약이 쓰는 비교값과 같다).
     *
     * 키가 아주 많으면 **앞에서부터 일부만** 본다. 5년치 서울 전역이면 카드가 수만 장이라
     * 전부를 한 쿼리에 넣을 수 없다. 뒤쪽 카드는 그래프 없이 나온다 —
     * 지어낸 그래프를 붙이는 것보다 낫다. 그러니 화면에 보이는 순서대로 넘길 것.
     */
    fun observeAmountSeries(
        complexAreaKeys: List<String>,
        tab: DealTab,
    ): Flow<Map<String, List<Long>>>

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
