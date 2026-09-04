package com.aptprice.tracker.data.cache

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * (지역 × 계약월) 캐시를 언제 다시 받아올지 정한다.
 *
 * 계약월마다 성격이 다르다.
 * - **최근 달**: 부동산 거래신고는 계약일로부터 30일 이내이므로, 이번 달과 직전 두 달치는
 *   새 신고가 계속 들어온다. 자주 다시 받아야 한다.
 * - **지난 달**: 신고가 대부분 끝났다. 다만 계약 해제가 뒤늦게 반영될 수 있어
 *   영원히 고정은 아니다. 길게 잡되 만료는 시킨다.
 *
 * 5년치를 받을 때 61개월 중 대부분은 "지난 달" 이라, 한 번 받아두면 재조회가 거의 없다.
 */
object CachePolicy {

    /** 신고가 계속 들어오는 최근 구간의 유효기간. */
    val RECENT_MONTH_TTL: Duration = Duration.ofHours(6)

    /** 신고가 마무리된 구간의 유효기간. */
    val SETTLED_MONTH_TTL: Duration = Duration.ofDays(30)

    /**
     * "최근 달" 로 보는 범위 (개월).
     * 신고 기한 30일 + 처리 지연을 감안해 이번 달 포함 직전 2개월까지로 잡는다.
     */
    const val RECENT_MONTH_SPAN = 2

    /** 해당 계약월의 유효기간. */
    fun ttlFor(dealYmd: String, today: LocalDate): Duration {
        val monthsAgo = monthsAgo(dealYmd, today) ?: return RECENT_MONTH_TTL
        return if (monthsAgo <= RECENT_MONTH_SPAN) RECENT_MONTH_TTL else SETTLED_MONTH_TTL
    }

    /**
     * 캐시가 만료됐는가.
     *
     * @param fetchedAt 마지막으로 받아온 시각. `null` 이면 받은 적이 없으므로 항상 만료.
     */
    fun isStale(
        dealYmd: String,
        fetchedAt: Instant?,
        now: Instant,
        today: LocalDate,
    ): Boolean {
        if (fetchedAt == null) return true
        // 시계가 뒤로 간 경우(기기 시간 변경 등)에도 다시 받는 쪽이 안전하다.
        if (fetchedAt.isAfter(now)) return true
        return Duration.between(fetchedAt, now) >= ttlFor(dealYmd, today)
    }

    /** 아직 오지 않은 미래의 계약월인가. 조회할 필요가 없다. */
    fun isFutureMonth(dealYmd: String, today: LocalDate): Boolean {
        val month = parseYearMonth(dealYmd) ?: return false
        return month.isAfter(YearMonth.from(today))
    }

    /** 오늘 기준 몇 개월 전인가. 형식이 어긋나면 null. */
    private fun monthsAgo(dealYmd: String, today: LocalDate): Long? {
        val month = parseYearMonth(dealYmd) ?: return null
        return ChronoUnit.MONTHS.between(month, YearMonth.from(today))
    }

    private fun parseYearMonth(dealYmd: String): YearMonth? {
        if (dealYmd.length != 6 || dealYmd.any { !it.isDigit() }) return null
        val year = dealYmd.substring(0, 4).toIntOrNull() ?: return null
        val month = dealYmd.substring(4, 6).toIntOrNull() ?: return null
        if (month !in 1..12) return null
        return YearMonth.of(year, month)
    }
}
