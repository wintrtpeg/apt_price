package com.aptprice.tracker.data.repository

import com.aptprice.tracker.core.time.TradePeriod
import com.aptprice.tracker.core.time.TradeQueryPlan
import com.aptprice.tracker.core.time.TradeRequestKey
import com.aptprice.tracker.data.cache.CachePolicy
import com.aptprice.tracker.data.local.dao.RentDao
import com.aptprice.tracker.data.local.dao.SyncStateDao
import com.aptprice.tracker.data.local.dao.TradeDao
import com.aptprice.tracker.data.local.entity.SyncEndpoint
import com.aptprice.tracker.data.local.entity.SyncStateEntity
import com.aptprice.tracker.data.mapper.toDomain
import com.aptprice.tracker.data.mapper.toEntity
import com.aptprice.tracker.data.remote.api.MolitApiService
import com.aptprice.tracker.data.remote.api.ServiceKeyProvider
import com.aptprice.tracker.data.remote.parser.MolitApiError
import com.aptprice.tracker.data.remote.parser.MolitApiException
import com.aptprice.tracker.data.remote.parser.MolitPage
import com.aptprice.tracker.data.remote.parser.MolitParser
import com.aptprice.tracker.data.remote.throttle.MolitHttpException
import com.aptprice.tracker.domain.model.AptDeal
import com.aptprice.tracker.domain.model.AptRent
import com.aptprice.tracker.domain.model.AptTrade
import com.aptprice.tracker.domain.model.ComplexSummary
import com.aptprice.tracker.domain.model.DealTab
import com.aptprice.tracker.domain.region.RegionCatalog
import com.aptprice.tracker.domain.repository.SyncFailure
import com.aptprice.tracker.domain.repository.SyncProgress
import com.aptprice.tracker.domain.repository.SyncReport
import com.aptprice.tracker.domain.repository.TradeRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

class TradeRepositoryImpl(
    private val api: MolitApiService,
    private val serviceKey: ServiceKeyProvider,
    private val tradeDao: TradeDao,
    private val rentDao: RentDao,
    private val syncStateDao: SyncStateDao,
    private val ioDispatcher: CoroutineDispatcher,
    private val clock: Clock,
) : TradeRepository {

    /** 접근 가능한 매매 서비스. 처음 성공한 쪽으로 고정된다. */
    @Volatile
    private var tradeApiVariant: TradeApiVariant? = null

    override fun observeDeals(
        period: TradePeriod,
        lawdCodes: List<String>,
        tab: DealTab,
    ): Flow<List<AptDeal>> {
        val range = period.range(today())
        val from = range.start.toEpochDay()
        val to = range.endInclusive.toEpochDay()
        val codes = lawdCodes.distinct()

        return when (tab) {
            DealTab.SALE -> tradeDao.observeInRange(codes, from, to)
                .map { rows -> rows.map { it.toDomain() }.filterVisible() }

            DealTab.JEONSE -> rentDao.observeInRange(codes, from, to, jeonseOnly = true)
                .map { rows -> rows.map { it.toDomain() }.filterVisible() }

            DealTab.MONTHLY -> rentDao.observeInRange(codes, from, to, jeonseOnly = false)
                .map { rows -> rows.map { it.toDomain() }.filterVisible() }
        }
    }

    override fun observeTradeSeries(
        complexAreaKey: String,
        period: TradePeriod,
    ): Flow<List<AptTrade>> {
        val range = period.range(today())
        return tradeDao
            .observeComplexArea(complexAreaKey, range.start.toEpochDay(), range.endInclusive.toEpochDay())
            .map { rows -> rows.map { it.toDomain() }.filterVisible() }
    }

    override fun observeJeonseSeries(
        complexAreaKey: String,
        period: TradePeriod,
    ): Flow<List<AptRent>> {
        val range = period.range(today())
        return rentDao
            .observeJeonseOfComplexArea(
                complexAreaKey,
                range.start.toEpochDay(),
                range.endInclusive.toEpochDay(),
            )
            .map { rows -> rows.map { it.toDomain() }.filterVisible() }
    }

    override fun searchComplexes(query: String): Flow<List<ComplexSummary>> {
        val trimmed = query.trim()
        // 한 글자로는 결과가 너무 많아 쓸모가 없다.
        if (trimmed.length < MIN_SEARCH_LENGTH) return flowOf(emptyList())

        return tradeDao.searchComplexes("%$trimmed%", SEARCH_LIMIT).map { rows ->
            rows.filter { RegionCatalog.accepts(it.lawdCd, it.umdNm) }
                .map { row ->
                    ComplexSummary(
                        complexKey = row.complexKey,
                        aptName = row.aptName,
                        lawdCd = row.lawdCd,
                        umdNm = row.umdNm,
                        regionLabel = listOfNotNull(
                            RegionCatalog.byLawdCd(row.lawdCd)?.displayName,
                            row.umdNm.takeIf { it.isNotEmpty() },
                        ).joinToString(" "),
                        latestDealDate = LocalDate.ofEpochDay(row.latestEpochDay),
                        latestAreaM2 = row.latestAreaM2,
                        dealCount = row.dealCount,
                    )
                }
        }
    }

    /** 매매·전월세 양쪽에 있는 평형을 합쳐서 돌려준다. 전세만 있는 평형도 칩에 나와야 한다. */
    override fun observeAreasOfComplex(complexKey: String): Flow<List<Double>> =
        combine(
            tradeDao.observeAreasOfComplex(complexKey),
            rentDao.observeAreasOfComplex(complexKey),
        ) { fromTrades, fromRents ->
            (fromTrades + fromRents).distinct().sorted()
        }

    override suspend fun lastFetchedAt(): Instant? = withContext(ioDispatcher) {
        syncStateDao.latestFetchedAt()?.let(Instant::ofEpochMilli)
    }

    override suspend fun sync(
        plan: TradeQueryPlan,
        onProgress: (SyncProgress) -> Unit,
    ): SyncReport = withContext(ioDispatcher) {
        val encodedKey = serviceKey.encodedKey()
        if (encodedKey == null) {
            return@withContext SyncReport.notConfigured(
                planned = plan.requestCount * SyncEndpoint.entries.size,
                error = MolitApiError(
                    code = "NO_KEY",
                    message = "공공데이터포털 인증키가 설정되지 않았습니다",
                    kind = MolitApiError.Kind.INVALID_SERVICE_KEY,
                ),
            )
        }

        val today = today()
        // 아직 오지 않은 달은 조회할 것이 없다.
        val keys = plan.requestKeys().filterNot { CachePolicy.isFutureMonth(it.dealYmd, today) }
        val jobs = SyncEndpoint.entries.flatMap { endpoint -> keys.map { endpoint to it } }

        // 동기화 기록은 엔드포인트당 한 번에 읽는다.
        // (구간마다 조회하면 5년 × 36지역 × 2엔드포인트 = 4천여 번의 DB 조회가 된다)
        val fetchedAtByJob = SyncEndpoint.entries.associateWith { endpoint ->
            syncStateDao
                .findAll(endpoint.name, plan.lawdCodes, plan.dealYmdCodes)
                .associate { (it.lawdCd to it.dealYmd) to Instant.ofEpochMilli(it.fetchedAtEpochMillis) }
        }

        val state = SyncState(planned = jobs.size)
        val now = clock.instant()
        // 이 filter 는 코루틴을 띄우기 전에 순차 실행되므로 카운터 경합이 없다.
        val staleJobs = jobs.filter { (endpoint, key) ->
            val fetchedAt = fetchedAtByJob[endpoint]?.get(key.lawdCd to key.dealYmd)
            val stale = CachePolicy.isStale(key.dealYmd, fetchedAt, now, today)
            if (!stale) state.recordSkipped()
            stale
        }

        val lastReported = AtomicInteger(-1)
        // 여러 코루틴이 동시에 콜백을 부르므로, 값이 뒤로 튀지 않도록 단조 증가만 내보낸다.
        fun report(key: TradeRequestKey?) {
            val done = state.completed
            while (true) {
                val previous = lastReported.get()
                if (done <= previous) return
                if (lastReported.compareAndSet(previous, done)) break
            }
            onProgress(SyncProgress(done, jobs.size, key))
        }

        report(null)

        val semaphore = Semaphore(MAX_CONCURRENT_REQUESTS)
        coroutineScope {
            staleJobs.map { (endpoint, key) ->
                async {
                    // 계속 시도해도 소용없는 오류를 만났으면 남은 구간은 건너뛴다.
                    if (state.abortError() != null) return@async
                    semaphore.withPermit {
                        if (state.abortError() != null) return@withPermit
                        runOne(endpoint, key, encodedKey, state)
                    }
                    report(key)
                }
            }.awaitAll()
        }

        state.toReport()
    }

    /** 구간 하나를 받아 저장한다. 실패는 [SyncState] 에 기록만 하고 던지지 않는다. */
    private suspend fun runOne(
        endpoint: SyncEndpoint,
        key: TradeRequestKey,
        encodedKey: String,
        state: SyncState,
    ) {
        // 어느 API 가 실패했는지 알 수 있게 사유에 함께 남긴다.
        val endpointLabel = if (endpoint == SyncEndpoint.TRADE) "매매" else "전월세"

        // 이 엔드포인트는 권한이 없다고 이미 확인됐다. 같은 실패를 수천 번 되풀이하지 않는다.
        state.deniedError(endpoint)?.let { denied ->
            state.recordFailure(SyncFailure(key, "[$endpointLabel] ${denied.userMessage()}"))
            return
        }

        try {
            when (endpoint) {
                SyncEndpoint.TRADE -> syncTradeMonth(key, encodedKey, state)
                SyncEndpoint.RENT -> syncRentMonth(key, encodedKey, state)
            }
        } catch (e: MolitApiException) {
            when {
                // 이 API 만 권한이 없는 것이면 전체를 멈추지 않는다.
                // 매매가 막혔어도 전월세는 볼 수 있어야 한다.
                e.error.kind == MolitApiError.Kind.ACCESS_DENIED ->
                    state.denyEndpoint(endpoint, e.error)

                // 인증키 오류·트래픽 초과 → 남은 수천 건을 헛되이 때리지 않고 즉시 중단한다.
                !e.error.isRetriable -> state.abort(e.error)
            }
            state.recordFailure(SyncFailure(key, "[$endpointLabel] ${e.error.userMessage()}"))
        } catch (e: MolitHttpException) {
            // 권한이 없는 것이면 이 엔드포인트는 여기서 접는다. 다시 시도해도 같은 결과다.
            if (e.isAccessDenied) state.denyEndpoint(endpoint, asApiError(e))
            state.recordFailure(SyncFailure(key, "[$endpointLabel] ${describe(e)}"))
        } catch (e: IOException) {
            state.recordFailure(
                SyncFailure(key, "[$endpointLabel] 네트워크 오류(${e::class.simpleName}): ${e.message}"),
            )
        } catch (e: CancellationException) {
            // 취소는 정상 흐름이므로 그대로 올려보낸다. 삼키면 코루틴 취소가 깨진다.
            throw e
        } catch (e: Throwable) {
            // 구간 하나가 실패했다고 앱이 죽어서는 안 된다.
            // Exception 이 아니라 Throwable 로 잡는다. OutOfMemoryError 같은 Error 는
            // Exception 이 아니어서, Exception 만 잡으면 그대로 앱을 종료시킨다.
            state.recordFailure(
                SyncFailure(key, "[$endpointLabel] 조회 실패(${e::class.simpleName}): ${e.message}"),
            )
        }
    }

    private suspend fun syncTradeMonth(
        key: TradeRequestKey,
        encodedKey: String,
        state: SyncState,
    ) {
        val pages = fetchAllPages(key) { pageNo ->
            loadTradePage(key, encodedKey, pageNo)
        }
        val rows = pages.flatMap { it.items }
            .filter { RegionCatalog.storable(it.lawdCd, it.umdNm) }
            .map { it.toEntity(key.dealYmd) }

        tradeDao.replaceMonth(key.lawdCd, key.dealYmd, rows)
        recordSuccess(SyncEndpoint.TRADE, key, pages, rows.size, state)
    }

    private suspend fun syncRentMonth(
        key: TradeRequestKey,
        encodedKey: String,
        state: SyncState,
    ) {
        val pages = fetchAllPages(key) { pageNo ->
            MolitParser.parseRents(
                api.getAptRents(
                    serviceKey = encodedKey,
                    lawdCd = key.lawdCd,
                    dealYmd = key.dealYmd,
                    pageNo = pageNo,
                    numOfRows = MolitApiService.PAGE_SIZE,
                ),
                key.lawdCd,
            )
        }
        val rows = pages.flatMap { it.items }
            .filter { RegionCatalog.storable(it.lawdCd, it.umdNm) }
            .map { it.toEntity(key.dealYmd) }

        rentDao.replaceMonth(key.lawdCd, key.dealYmd, rows)
        recordSuccess(SyncEndpoint.RENT, key, pages, rows.size, state)
    }

    /**
     * 매매 자료를 받아온다.
     *
     * 매매는 "상세 자료"와 "기본 자료" 두 서비스로 나뉘어 있고, 활용신청한 쪽에만
     * 접근할 수 있다. 어느 쪽이 열려 있는지 미리 알 수 없으므로 되는 쪽을 찾아 쓴다.
     * 한 번 성공한 쪽을 기억해 두어, 그다음부터는 곧바로 그쪽만 부른다.
     */
    private suspend fun loadTradePage(
        key: TradeRequestKey,
        encodedKey: String,
        pageNo: Int,
    ): MolitPage<AptTrade> {
        val order = tradeApiVariant?.let { listOf(it) } ?: TradeApiVariant.entries
        var lastError: Throwable? = null

        order.forEach { variant ->
            try {
                val xml = when (variant) {
                    TradeApiVariant.DETAIL -> api.getAptTradesDetail(
                        serviceKey = encodedKey,
                        lawdCd = key.lawdCd,
                        dealYmd = key.dealYmd,
                        pageNo = pageNo,
                        numOfRows = MolitApiService.PAGE_SIZE,
                    )
                    TradeApiVariant.BASIC -> api.getAptTradesBasic(
                        serviceKey = encodedKey,
                        lawdCd = key.lawdCd,
                        dealYmd = key.dealYmd,
                        pageNo = pageNo,
                        numOfRows = MolitApiService.PAGE_SIZE,
                    )
                }
                val page = MolitParser.parseTrades(xml, key.lawdCd)
                tradeApiVariant = variant
                return page
            } catch (e: CancellationException) {
                throw e
            } catch (e: MolitHttpException) {
                // 그 서비스에 닿을 수 없을 때만 다른 쪽을 시도한다.
                //   404 = 서비스가 없음
                //   403/401 = 인증키에 그 서비스 권한이 없음 (활용신청 안 됨)
                // 매매 자료는 상세·기본 두 서비스로 나뉘어 있어 한쪽만 열려 있을 수 있으므로,
                // 권한 거부야말로 갈아타 봐야 하는 경우다.
                // 반면 429 나 서버 오류에 다른 쪽까지 부르면 요청량이 두 배가 되어 더 막힌다.
                if (!e.isNotFound && !e.isAccessDenied) throw e
                lastError = e
            } catch (e: Throwable) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("매매 자료를 받아오지 못했습니다")
    }

    /** `totalCount` 를 보고 다음 페이지가 있으면 이어서 받는다. */
    private suspend fun <T> fetchAllPages(
        key: TradeRequestKey,
        load: suspend (pageNo: Int) -> MolitPage<T>,
    ): List<MolitPage<T>> {
        val pages = mutableListOf<MolitPage<T>>()
        var pageNo = 1
        while (pageNo <= MolitApiService.MAX_PAGES) {
            val page = withRetry { load(pageNo) }
            pages += page
            if (!page.hasMorePages) break
            pageNo++
            delay(POLITENESS_DELAY_MILLIS)
        }
        return pages
    }

    /** 일시적인 오류만 재시도한다. 인증·한도 오류는 그대로 던진다. */
    private suspend fun <T> withRetry(block: suspend () -> T): T {
        var lastError: Exception? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                return block()
            } catch (e: MolitApiException) {
                if (!e.error.isRetriable) throw e
                lastError = e
            } catch (e: MolitHttpException) {
                // MolitHttpException 은 IOException 이라 아래 분기에 걸려 재시도된다.
                // 권한 없음(403)·서비스 없음(404)은 몇 번을 물어도 같은 답이 온다.
                // 그대로 두면 한 구간에 3배를 부르게 되고, 그것이 429 로 가는 길이다.
                if (e.isAccessDenied || e.isNotFound) throw e
                lastError = e
            } catch (e: IOException) {
                lastError = e
            }
            delay(RETRY_BASE_DELAY_MILLIS * (1L shl attempt))
        }
        throw lastError ?: IllegalStateException("재시도 실패")
    }

    private suspend fun <T> recordSuccess(
        endpoint: SyncEndpoint,
        key: TradeRequestKey,
        pages: List<MolitPage<T>>,
        storedRows: Int,
        state: SyncState,
    ) {
        val failureCount = pages.sumOf { it.failures.size }
        syncStateDao.upsert(
            SyncStateEntity(
                lawdCd = key.lawdCd,
                dealYmd = key.dealYmd,
                endpoint = endpoint.name,
                fetchedAtEpochMillis = clock.millis(),
                rowCount = storedRows,
                totalCount = pages.firstOrNull()?.totalCount ?: 0,
                failureCount = failureCount,
            ),
        )
        state.recordFetched(storedRows, failureCount)
    }

    /**
     * 2xx 아닌 응답을 사유로 바꾼다.
     *
     * 본문에 담긴 사유를 우선한다. 상태 코드는 "무엇을 해야 하는지" 를 알려 주지 않지만
     * 본문에는 `SERVICE_ACCESS_DENIED_ERROR` 같은 실제 원인이 들어 있다.
     */
    private fun asApiError(e: MolitHttpException): MolitApiError =
        e.body?.let { runCatching { MolitParser.parseError(it) }.getOrNull() }
            ?: MolitApiError(
                code = e.code.toString(),
                message = e.message.orEmpty(),
                kind = when {
                    // 활용신청이 안 된 서비스. 다시 시도해도 소용없고, 사용자가 할 일이 있다.
                    e.isAccessDenied -> MolitApiError.Kind.ACCESS_DENIED
                    else -> MolitApiError.Kind.SERVICE_ERROR
                },
            )

    /** 화면에 그대로 띄울 사유. */
    private fun describe(e: MolitHttpException): String = when {
        e.isRateLimited ->
            "요청이 몰려 공공데이터포털이 일시적으로 막았습니다(429). " +
                "지역을 줄이거나 잠시 뒤 다시 시도해 주세요"
        e.isNotFound -> "해당 서비스를 찾을 수 없습니다(404)"
        e.isServerError -> "공공데이터포털 서버 오류(${e.code})"
        else -> asApiError(e).userMessage()
    }

    private fun today(): LocalDate = LocalDate.now(clock)

    /**
     * 화면에 보일 행만 남긴다.
     *
     * 캐시에는 동탄 확장 목록까지 저장돼 있으므로, 지역별 활성 화이트리스트로 한 번 더 거른다.
     */
    private fun <T : AptDeal> List<T>.filterVisible(): List<T> =
        filter { RegionCatalog.accepts(it.lawdCd, it.umdNm) }

    /** 동시 실행 중인 코루틴들이 함께 갱신하는 집계 상태. */
    private class SyncState(val planned: Int) {
        private val mutex = Mutex()
        private var skippedFresh = 0
        private var fetched = 0
        private var storedRows = 0
        private var parseFailures = 0
        private val failures = mutableListOf<SyncFailure>()

        @Volatile
        private var abortError: MolitApiError? = null

        /**
         * 권한이 없어 포기한 엔드포인트.
         *
         * 매매 활용신청이 안 되어 있으면 매매 구간은 하나도 빠짐없이 같은 이유로 실패한다.
         * 5년 × 36지역이면 수천 번을 헛되이 때리게 되므로, 한 번 확인되면 그 엔드포인트는
         * 더 부르지 않는다. **다른 엔드포인트는 계속 간다** — 매매가 막혔다고 전월세까지
         * 멈추면, 볼 수 있었던 자료마저 못 보게 된다.
         */
        private val deniedEndpoints = java.util.concurrent.ConcurrentHashMap<SyncEndpoint, MolitApiError>()

        @Volatile
        var completed: Int = 0
            private set

        fun abortError(): MolitApiError? = abortError

        fun abort(error: MolitApiError) {
            if (abortError == null) abortError = error
        }

        fun denyEndpoint(endpoint: SyncEndpoint, error: MolitApiError) {
            deniedEndpoints.putIfAbsent(endpoint, error)
        }

        fun deniedError(endpoint: SyncEndpoint): MolitApiError? = deniedEndpoints[endpoint]

        fun recordSkipped() {
            skippedFresh++
            completed++
        }

        suspend fun recordFetched(rows: Int, failureCount: Int) = mutex.withLock {
            fetched++
            storedRows += rows
            parseFailures += failureCount
            completed++
        }

        suspend fun recordFailure(failure: SyncFailure) = mutex.withLock {
            failures += failure
            completed++
        }

        fun toReport() = SyncReport(
            planned = planned,
            skippedFresh = skippedFresh,
            fetched = fetched,
            storedRows = storedRows,
            parseFailures = parseFailures,
            failures = failures.toList(),
            abortedBy = abortError,
        )
    }

    /** 어느 매매 서비스가 열려 있는지. 한 번 확인되면 그쪽만 쓴다. */
    private enum class TradeApiVariant { DETAIL, BASIC }

    private companion object {
        /**
         * 동시 요청 수.
         *
         * 4로 두었더니 실기기에서 HTTP 429(Too Many Requests) 가 쏟아졌다.
         * 간격 조절은 ThrottleInterceptor 가 맡고, 여기서는 동시에 열리는 연결 수를 줄인다.
         */
        const val MAX_CONCURRENT_REQUESTS = 2

        /** 검색어 최소 길이. 한 글자로는 결과가 너무 많다. */
        const val MIN_SEARCH_LENGTH = 2

        /** 검색 결과 상한. */
        const val SEARCH_LIMIT = 50

        const val MAX_ATTEMPTS = 3
        const val RETRY_BASE_DELAY_MILLIS = 500L

        /** 페이지를 이어 받을 때의 간격. */
        const val POLITENESS_DELAY_MILLIS = 100L
    }
}
