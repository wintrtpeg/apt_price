package com.aptprice.tracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.aptprice.tracker.data.local.entity.ComplexSearchRow
import com.aptprice.tracker.data.local.entity.TradeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TradeDao {

    /**
     * 한 달치를 통째로 갈아끼운다.
     *
     * 국토교통부 자료는 지연 신고·계약 해제로 나중에 바뀔 수 있으므로,
     * 행 단위로 합치지 않고 (지역 × 계약월) 단위로 지우고 다시 넣는다.
     */
    @Transaction
    suspend fun replaceMonth(lawdCd: String, dealYmd: String, rows: List<TradeEntity>) {
        deleteMonth(lawdCd, dealYmd)
        if (rows.isNotEmpty()) insertAll(rows)
    }

    @Query("DELETE FROM apt_trade WHERE lawdCd = :lawdCd AND dealYmd = :dealYmd")
    suspend fun deleteMonth(lawdCd: String, dealYmd: String)

    @Insert
    suspend fun insertAll(rows: List<TradeEntity>)

    /** 계약일 구간 + 지역으로 조회. 최신 계약일 순. */
    @Query(
        """
        SELECT * FROM apt_trade
        WHERE lawdCd IN (:lawdCodes)
          AND dealDateEpochDay BETWEEN :fromEpochDay AND :toEpochDay
        ORDER BY dealDateEpochDay DESC, dealAmountManwon DESC
        """,
    )
    fun observeInRange(
        lawdCodes: List<String>,
        fromEpochDay: Long,
        toEpochDay: Long,
    ): Flow<List<TradeEntity>>

    /** 단지 + 평형 단위 시계열. 상세 차트가 쓴다. 오래된 순. */
    @Query(
        """
        SELECT * FROM apt_trade
        WHERE complexAreaKey = :complexAreaKey
          AND dealDateEpochDay BETWEEN :fromEpochDay AND :toEpochDay
        ORDER BY dealDateEpochDay ASC
        """,
    )
    fun observeComplexArea(
        complexAreaKey: String,
        fromEpochDay: Long,
        toEpochDay: Long,
    ): Flow<List<TradeEntity>>

    /** 단지 안에 존재하는 평형 목록 (평형 선택 칩용). */
    @Query(
        """
        SELECT DISTINCT exclusiveAreaM2 FROM apt_trade
        WHERE complexKey = :complexKey
        ORDER BY exclusiveAreaM2 ASC
        """,
    )
    fun observeAreasOfComplex(complexKey: String): Flow<List<Double>>

    /**
     * 단지명으로 검색한다. 매매·전월세 양쪽에서 찾아 단지 단위로 합친다.
     *
     * 받아온 자료 안에서만 찾는다. API 가 단지명 조회를 제공하지 않기 때문이다.
     *
     * `latestAreaM2` 는 **가장 최근 거래의 전용면적**이다. SQLite 는 집계가 `MAX()`
     * 하나뿐일 때 함께 고른 일반 컬럼을 그 최댓값 행에서 가져온다(bare column 규칙).
     * `COUNT(*)` 는 min/max 가 아니므로 이 규칙을 깨지 않는다.
     * 상세 화면을 열 때 평형이 하나는 정해져 있어야 해서 함께 뽑는다.
     */
    @Query(
        """
        SELECT complexKey, aptName, lawdCd, umdNm,
               MAX(dealDateEpochDay) AS latestEpochDay,
               exclusiveAreaM2 AS latestAreaM2,
               COUNT(*) AS dealCount
        FROM (
            SELECT complexKey, aptName, lawdCd, umdNm, dealDateEpochDay, exclusiveAreaM2
            FROM apt_trade WHERE aptName LIKE :pattern
            UNION ALL
            SELECT complexKey, aptName, lawdCd, umdNm, dealDateEpochDay, exclusiveAreaM2
            FROM apt_rent WHERE aptName LIKE :pattern
        )
        GROUP BY complexKey
        ORDER BY latestEpochDay DESC
        LIMIT :limit
        """,
    )
    fun searchComplexes(pattern: String, limit: Int): Flow<List<ComplexSearchRow>>

    @Query("SELECT COUNT(*) FROM apt_trade")
    suspend fun count(): Int

    @Query("DELETE FROM apt_trade")
    suspend fun clear()
}
