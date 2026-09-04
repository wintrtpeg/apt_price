package com.aptprice.tracker.data.repository

import com.aptprice.tracker.data.local.dao.RentDao
import com.aptprice.tracker.data.local.dao.SyncStateDao
import com.aptprice.tracker.data.local.dao.TradeDao
import com.aptprice.tracker.data.local.entity.RentEntity
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

    override suspend fun getAptTrades(
        serviceKey: String,
        lawdCd: String,
        dealYmd: String,
        pageNo: Int,
        numOfRows: Int,
    ): String {
        synchronized(lock) { tradeCalls += Triple(lawdCd, dealYmd, pageNo) }
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

class FakeTradeDao : TradeDao {
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

    override suspend fun count(): Int = rows.size

    override suspend fun clear() {
        rows.clear()
        version.value++
    }
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
