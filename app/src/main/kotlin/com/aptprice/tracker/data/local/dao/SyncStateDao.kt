package com.aptprice.tracker.data.local.dao

import androidx.room.Dao
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.aptprice.tracker.data.local.entity.SyncStateEntity

@Dao
interface SyncStateDao {

    @Upsert
    suspend fun upsert(state: SyncStateEntity)

    @Query(
        """
        SELECT * FROM sync_state
        WHERE endpoint = :endpoint AND lawdCd IN (:lawdCodes) AND dealYmd IN (:dealYmds)
        """,
    )
    suspend fun findAll(
        endpoint: String,
        lawdCodes: List<String>,
        dealYmds: List<String>,
    ): List<SyncStateEntity>

    /** 가장 최근에 무언가를 받아온 시각. 화면의 "기준일시" 표기에 쓴다. */
    @Query("SELECT MAX(fetchedAtEpochMillis) FROM sync_state")
    suspend fun latestFetchedAt(): Long?

    /** 읽지 못한 행이 있는 구간. 0 이 아니면 파서 점검이 필요하다. */
    @Query("SELECT * FROM sync_state WHERE failureCount > 0 ORDER BY fetchedAtEpochMillis DESC")
    suspend fun withParseFailures(): List<SyncStateEntity>

    @Query("DELETE FROM sync_state")
    suspend fun clear()

    @Suppress("unused")
    companion object {
        const val CONFLICT_REPLACE = OnConflictStrategy.REPLACE
    }
}
