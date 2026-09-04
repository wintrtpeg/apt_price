package com.aptprice.tracker.data.remote.api

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 국토교통부 실거래가 Open API.
 *
 * 응답은 XML 이며, 본문을 문자열로 받아 [com.aptprice.tracker.data.remote.parser.MolitParser]
 * 가 해석한다. (오류 응답이 서비스 스키마가 아니라 포털 게이트웨이 스키마로 오는 경우가 있어,
 * 컨버터에 맡기지 않고 직접 판별한다)
 *
 * Base URL: `https://apis.data.go.kr/1613000/`
 */
interface MolitApiService {

    /** 국토교통부_아파트 매매 실거래자료 */
    @GET("RTMSDataSvcAptTradeDev/getRTMSDataSvcAptTradeDev")
    suspend fun getAptTrades(
        // 인증키는 이미 퍼센트 인코딩된 상태로 넘긴다. (ServiceKeyProvider 주석 참고)
        @Query(value = "serviceKey", encoded = true) serviceKey: String,
        @Query("LAWD_CD") lawdCd: String,
        @Query("DEAL_YMD") dealYmd: String,
        @Query("pageNo") pageNo: Int,
        @Query("numOfRows") numOfRows: Int,
    ): String

    /** 국토교통부_아파트 전월세 자료 */
    @GET("RTMSDataSvcAptRent/getRTMSDataSvcAptRent")
    suspend fun getAptRents(
        @Query(value = "serviceKey", encoded = true) serviceKey: String,
        @Query("LAWD_CD") lawdCd: String,
        @Query("DEAL_YMD") dealYmd: String,
        @Query("pageNo") pageNo: Int,
        @Query("numOfRows") numOfRows: Int,
    ): String

    companion object {
        const val BASE_URL = "https://apis.data.go.kr/1613000/"

        /**
         * 한 번에 받아올 행 수.
         * 크게 잡을수록 (지역 × 계약월) 당 호출 횟수가 줄어든다.
         * 강남구처럼 거래가 많은 달도 대개 한 번에 들어온다.
         */
        const val PAGE_SIZE = 1000

        /** 페이징 폭주 방지 상한. */
        const val MAX_PAGES = 20
    }
}
