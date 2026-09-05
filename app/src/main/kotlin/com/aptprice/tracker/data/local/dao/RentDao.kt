package com.aptprice.tracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.aptprice.tracker.data.local.entity.RentEntity
import com.aptprice.tracker.data.local.entity.SparkRow
import kotlinx.coroutines.flow.Flow

@Dao
interface RentDao {

    @Transaction
    suspend fun replaceMonth(lawdCd: String, dealYmd: String, rows: List<RentEntity>) {
        deleteMonth(lawdCd, dealYmd)
        if (rows.isNotEmpty()) insertAll(rows)
    }

    @Query("DELETE FROM apt_rent WHERE lawdCd = :lawdCd AND dealYmd = :dealYmd")
    suspend fun deleteMonth(lawdCd: String, dealYmd: String)

    @Insert
    suspend fun insertAll(rows: List<RentEntity>)

    /**
     * 계약일 구간 + 지역으로 조회.
     * [jeonseOnly] 가 true 면 전세(월세 0)만, false 면 월세만 남긴다.
     */
    @Query(
        """
        SELECT * FROM apt_rent
        WHERE lawdCd IN (:lawdCodes)
          AND dealDateEpochDay BETWEEN :fromEpochDay AND :toEpochDay
          AND ((:jeonseOnly = 1 AND monthlyRentManwon = 0)
            OR (:jeonseOnly = 0 AND monthlyRentManwon > 0))
        ORDER BY dealDateEpochDay DESC, depositManwon DESC
        """,
    )
    fun observeInRange(
        lawdCodes: List<String>,
        fromEpochDay: Long,
        toEpochDay: Long,
        jeonseOnly: Boolean,
    ): Flow<List<RentEntity>>

    /** 단지 + 평형 단위 시계열 (전세만). 매매와 겹쳐 그리기 위한 것. */
    @Query(
        """
        SELECT * FROM apt_rent
        WHERE complexAreaKey = :complexAreaKey
          AND dealDateEpochDay BETWEEN :fromEpochDay AND :toEpochDay
          AND monthlyRentManwon = 0
        ORDER BY dealDateEpochDay ASC
        """,
    )
    fun observeJeonseOfComplexArea(
        complexAreaKey: String,
        fromEpochDay: Long,
        toEpochDay: Long,
    ): Flow<List<RentEntity>>

    /** 단지 안에 존재하는 평형 목록 (전세만 있는 평형도 칩에 나오도록). */
    @Query(
        """
        SELECT DISTINCT exclusiveAreaM2 FROM apt_rent
        WHERE complexKey = :complexKey
        ORDER BY exclusiveAreaM2 ASC
        """,
    )
    fun observeAreasOfComplex(complexKey: String): Flow<List<Double>>

    /**
     * 카드 미니 그래프용. 목록과 같은 기준(전세/월세)으로 보증금 흐름을 가져온다.
     * 카드마다 조회하지 않도록 키를 한 번에 넘긴다.
     *
     * 매매와 달리 해제 건을 거르지 않는다 — 전월세 자료에는 해제 여부 자체가 없다.
     * (`apt_rent` 에 그 컬럼이 없다. 없는 것을 거르는 척하지 않는다)
     */
    @Query(
        """
        SELECT complexAreaKey, dealDateEpochDay, depositManwon AS amountManwon
        FROM apt_rent
        WHERE complexAreaKey IN (:complexAreaKeys)
          AND ((:jeonseOnly = 1 AND monthlyRentManwon = 0)
            OR (:jeonseOnly = 0 AND monthlyRentManwon > 0))
        ORDER BY complexAreaKey ASC, dealDateEpochDay ASC
        """,
    )
    fun observeSparkPoints(
        complexAreaKeys: List<String>,
        jeonseOnly: Boolean,
    ): Flow<List<SparkRow>>

    @Query("SELECT COUNT(*) FROM apt_rent")
    suspend fun count(): Int

    @Query("DELETE FROM apt_rent")
    suspend fun clear()
}
