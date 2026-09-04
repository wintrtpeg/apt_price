package com.aptprice.tracker.core.format

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** 목록/차트에서 쓰는 날짜 표기. 로케일은 항상 한국어로 고정한다. */
object DateFormatter {

    private val KOREA = Locale.KOREA
    private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", KOREA)
    private val ISO_DATE_TIME: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", KOREA)

    /** `"2026-09-04"` */
    fun formatIso(date: LocalDate): String = date.format(ISO_DATE)

    /** `"2026-09-04 11:20"` — 데이터 기준일시 표기에 사용. */
    fun formatIsoDateTime(dateTime: LocalDateTime): String = dateTime.format(ISO_DATE_TIME)

    /** `"09.04 (금)"` — 피드 카드의 계약일 표기. */
    fun formatFeedDate(date: LocalDate): String {
        val dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.SHORT, KOREA)
        return "%02d.%02d (%s)".format(date.monthValue, date.dayOfMonth, dayOfWeek)
    }

    /** `"오늘"`, `"어제"`, `"3일 전"` — 최근 2주 피드의 상대 표기. */
    fun formatRelativeDay(date: LocalDate, today: LocalDate): String {
        val days = ChronoUnit.DAYS.between(date, today)
        return when {
            days == 0L -> "오늘"
            days == 1L -> "어제"
            days > 1L -> "${days}일 전"
            else -> formatFeedDate(date)
        }
    }
}
