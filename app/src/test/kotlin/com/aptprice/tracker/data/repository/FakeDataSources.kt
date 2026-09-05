package com.aptprice.tracker.data.repository

import com.aptprice.tracker.data.local.dao.RentDao
import com.aptprice.tracker.data.local.dao.SyncStateDao
import com.aptprice.tracker.data.local.dao.TradeDao
import com.aptprice.tracker.data.local.entity.ComplexSearchRow
import com.aptprice.tracker.data.local.entity.RentEntity
import com.aptprice.tracker.data.local.entity.SparkRow
import com.aptprice.tracker.data.local.entity.SyncStateEntity
import com.aptprice.tracker.data.local.entity.TradeEntity
import com.aptprice.tracker.data.remote.api.MolitApiService
import com.aptprice.tracker.data.remote.api.ServiceKeyStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** 어떤 (지역, 계약월, 엔드포인트) 를 몇 번 불렀는지 기록하는 가짜 API. */
class FakeMolitApiService(
    private val tradeResponder: (lawdCd: String, dealYmd: String, pageNo: Int) -> String,
    private val rentResponder: (lawdCd: String, dealYmd: String, pageNo: Int) -> String,
) : MolitApiService {

    val tradeCalls = mutableListOf<Triple<String, String, Int>>()
    val rentCalls = mutableListOf<Triple<String, String, Int>>()

    private val lock = Any()

    /** 상세 매매를 부른 횟수 (엔드포인트 폴백 검증용) */
    val detailCalls = mutableListOf<Triple<String, String, Int>>()

    /** 기본 매매를 부른 횟수 */
    val basicCalls = mutableListOf<Triple<String, String, Int>>()

    /** 상세 매매가 실패하도록 만들 때 쓴다. null 이면 tradeResponder 를 그대로 쓴다. */
    var detailFailure: (() -> Nothing)? = null

    override suspend fun getAptTradesDetail(
        serviceKey: String,
        lawdCd: String,
        dealYmd: String,
        pageNo: Int,
        numOfRows: Int,
    ): String {
        synchronized(lock) {
            tradeCalls += Triple(lawdCd, dealYmd, pageNo)
            detailCalls += Triple(lawdCd, dealYmd, pageNo)
        }
        detailFailure?.invoke()
        return tradeResponder(lawdCd, dealYmd, pageNo)
    }

    override suspend fun getAptTradesBasic(
        serviceKey: String,
        lawdCd: String,
        dealYmd: String,
        pageNo: Int,
        numOfRows: Int,
    ): String {
        synchronized(lock) {
            tradeCalls += Triple(lawdCd, dealYmd, pageNo)
            basicCalls += Triple(lawdCd, dealYmd, pageNo)
        }
        return tradeResponder(lawdCd, dealYmd, pageNo)
    }

    override suspend fun getAptRents(
        serviceKey: String,
        lawdCd: String,
        dealYmd: String,
        pageNo: Int,
        numOfRows: Int,
    ): String {
        synchronized(lock) { rentCalls += Triple(lawdCd, dealYmd, pageNo) }
        return rentResponder(lawdCd, dealYmd, pageNo)
    }

    val totalCalls: Int get() = tradeCalls.size + rentCalls.size
}

/**
 * @param rentRows 전월세 표. 단지 검색이 매매·전월세를 합쳐 찾기 때문에 함께 본다.
 *                 (실제 DAO 는 두 테이블을 UNION 한다)
 */
class FakeTradeDao(
    private val rentRows: () -> List<RentEntity> = { emptyList() },
) : TradeDao {
    val rows = mutableListOf<TradeEntity>()
    private val version = MutableStateFlow(0)
    private val ids = AtomicInteger(0)

    override suspend fun deleteMonth(lawdCd: String, dealYmd: String) {
        synchronized(rows) { rows.removeAll { it.lawdCd == lawdCd && it.dealYmd == dealYmd } }
        version.value++
    }

    override suspend fun insertAll(rows: List<TradeEntity>) {
        synchronized(this.rows) {
            this.rows += rows.map { it.copy(id = ids.incrementAndGet().toLong()) }
        }
        version.value++
    }

    override fun observeInRange(
        lawdCodes: List<String>,
        fromEpochDay: Long,
        toEpochDay: Long,
    ): Flow<List<TradeEntity>> = version.map {
        rows.filter {
            it.lawdCd in lawdCodes && it.dealDateEpochDay in fromEpochDay..toEpochDay
        }.sortedWith(compareByDescending<TradeEntity> { it.dealDateEpochDay }.thenByDescending { it.dealAmountManwon })
    }

    override fun observeComplexArea(
        complexAreaKey: String,
        fromEpochDay: Long,
        toEpochDay: Long,
    ): Flow<List<TradeEntity>> = version.map {
        rows.filter {
            it.complexAreaKey == complexAreaKey && it.dealDateEpochDay in fromEpochDay..toEpochDay
        }.sortedBy { it.dealDateEpochDay }
    }

    override fun observeAreasOfComplex(complexKey: String): Flow<List<Double>> = version.map {
        rows.filter { it.complexKey == complexKey }.map { it.exclusiveAreaM2 }.distinct().sorted()
    }

    override fun searchComplexes(pattern: String, limit: Int): Flow<List<ComplexSearchRow>> = version.map {
        val needle = pattern.trim('%')
        val union = rows.map {
            SearchInput(it.complexKey, it.aptName, it.lawdCd, it.umdNm, it.dealDateEpochDay, it.exclusiveAreaM2)
        } + rentRows().map {
            SearchInput(it.complexKey, it.aptName, it.lawdCd, it.umdNm, it.dealDateEpochDay, it.exclusiveAreaM2)
        }
        union.filter { it.aptName.contains(needle) }
            .groupBy { it.complexKey }
            .map { (complexKey, group) ->
                // SQLite 의 bare column 규칙과 같이, 가장 최근 거래 행에서 값을 가져온다.
                val latest = group.maxByOrNull { it.epochDay }!!
                ComplexSearchRow(
                    complexKey = complexKey,
                    aptName = latest.aptName,
                    lawdCd = latest.lawdCd,
                    umdNm = latest.umdNm,
                    latestEpochDay = latest.epochDay,
                    latestAreaM2 = latest.areaM2,
                    dealCount = group.size,
                )
            }
            .sortedByDescending { it.latestEpochDay }
            .take(limit)
    }

    /** 그래프 조회가 몇 번 일어났는지 (카드마다 부르지 않는지 확인용) */
    val sparkQueries = mutableListOf<List<String>>()

    override fun observeSparkPoints(complexAreaKeys: List<String>): Flow<List<SparkRow>> =
        version.map {
            sparkQueries += complexAreaKeys
            rows.filter { it.complexAreaKey in complexAreaKeys && !it.canceled }
                .sortedWith(compareBy({ it.complexAreaKey }, { it.dealDateEpochDay }))
                .map { SparkRow(it.complexAreaKey, it.dealDateEpochDay, it.dealAmountManwon) }
        }

    override suspend fun count(): Int = rows.size

    override suspend fun clear() {
        rows.clear()
        version.value++
    }

    private data class SearchInput(
        val complexKey: String,
        val aptName: String,
        val lawdCd: String,
        val umdNm: String,
        val epochDay: Long,
        val areaM2: Double,
    )
}

class FakeRentDao : RentDao {
    val rows = mutableListOf<RentEntity>()
    private val version = MutableStateFlow(0)
    private val ids = AtomicInteger(0)

    override suspend fun deleteMonth(lawdCd: String, dealYmd: String) {
        synchronized(rows) { rows.removeAll { it.lawdCd == lawdCd && it.dealYmd == dealYmd } }
        version.value++
    }

    override suspend fun insertAll(rows: List<RentEntity>) {
        synchronized(this.rows) {
            this.rows += rows.map { it.copy(id = ids.incrementAndGet().toLong()) }
        }
        version.value++
    }

    override fun observeInRange(
        lawdCodes: List<String>,
        fromEpochDay: Long,
        toEpochDay: Long,
        jeonseOnly: Boolean,
    ): Flow<List<RentEntity>> = version.map {
        rows.filter {
            it.lawdCd in lawdCodes &&
                it.dealDateEpochDay in fromEpochDay..toEpochDay &&
                (if (jeonseOnly) it.monthlyRentManwon == 0L else it.monthlyRentManwon > 0L)
        }.sortedWith(compareByDescending<RentEntity> { it.dealDateEpochDay }.thenByDescending { it.depositManwon })
    }

    override fun observeJeonseOfComplexArea(
        complexAreaKey: String,
        fromEpochDay: Long,
        toEpochDay: Long,
    ): Flow<List<RentEntity>> = version.map {
        rows.filter {
            it.complexAreaKey == complexAreaKey &&
                it.dealDateEpochDay in fromEpochDay..toEpochDay &&
                it.monthlyRentManwon == 0L
        }.sortedBy { it.dealDateEpochDay }
    }

    override fun observeAreasOfComplex(complexKey: String): Flow<List<Double>> = version.map {
        rows.filter { it.complexKey == complexKey }.map { it.exclusiveAreaM2 }.distinct().sorted()
    }

    /** 그래프 조회가 몇 번 일어났는지 (카드마다 부르지 않는지 확인용) */
    val sparkQueries = mutableListOf<Pair<List<String>, Boolean>>()

    override fun observeSparkPoints(
        complexAreaKeys: List<String>,
        jeonseOnly: Boolean,
    ): Flow<List<SparkRow>> = version.map {
        sparkQueries += complexAreaKeys to jeonseOnly
        rows.filter {
            it.complexAreaKey in complexAreaKeys &&
                (if (jeonseOnly) it.monthlyRentManwon == 0L else it.monthlyRentManwon > 0L)
        }
            .sortedWith(compareBy({ it.complexAreaKey }, { it.dealDateEpochDay }))
            .map { SparkRow(it.complexAreaKey, it.dealDateEpochDay, it.depositManwon) }
    }

    override suspend fun count(): Int = rows.size

    override suspend fun clear() {
        rows.clear()
        version.value++
    }
}

class FakeSyncStateDao : SyncStateDao {
    val states = ConcurrentHashMap<Triple<String, String, String>, SyncStateEntity>()

    override suspend fun upsert(state: SyncStateEntity) {
        states[Triple(state.lawdCd, state.dealYmd, state.endpoint)] = state
    }

    override suspend fun findAll(
        endpoint: String,
        lawdCodes: List<String>,
        dealYmds: List<String>,
    ): List<SyncStateEntity> = states.values.filter {
        it.endpoint == endpoint && it.lawdCd in lawdCodes && it.dealYmd in dealYmds
    }

    override suspend fun latestFetchedAt(): Long? =
        states.values.maxOfOrNull { it.fetchedAtEpochMillis }

    override suspend fun withParseFailures(): List<SyncStateEntity> =
        states.values.filter { it.failureCount > 0 }

    override suspend fun clear() = states.clear()
}

/** 메모리에만 두는 인증키 저장소. */
class FakeServiceKeyStore(initial: String = "") : ServiceKeyStore {
    private val state = MutableStateFlow(initial)
    override val keyFlow: Flow<String> = state

    var saveCount: Int = 0
        private set

    override suspend fun save(key: String) {
        state.value = key
        saveCount++
    }

    override suspend fun clear() {
        state.value = ""
    }
}
