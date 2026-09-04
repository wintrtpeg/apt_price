package com.aptprice.tracker.core.attribution

/**
 * "값이 없으면 없다고 말한다" 는 원칙을 타입으로 강제하기 위한 래퍼.
 *
 * 실거래 값을 다루는 모든 지점에서 [Missing] 을 명시적으로 처리하게 만들어,
 * 파싱 실패나 미신고 구간을 0 이나 임의의 추정치로 대체하는 실수를 막는다.
 */
sealed interface TradeValue<out T> {

    /** 국토교통부 원자료에서 실제로 확인된 값. */
    data class Reported<out T>(val value: T) : TradeValue<T>

    /** 원자료에 값이 없거나 조회할 수 없는 상태. [reason] 은 화면에 그대로 노출한다. */
    data class Missing(val reason: String) : TradeValue<Nothing>

    companion object {
        /** 파싱 결과가 null 이면 [Missing] 으로 승격한다. */
        fun <T> ofNullable(
            value: T?,
            reason: String = DataSourceAttribution.NOT_REPORTED,
        ): TradeValue<T> = if (value != null) Reported(value) else Missing(reason)
    }
}

/** 표시용 문자열로 변환. [Missing] 이면 사유 문구를 그대로 보여준다. */
inline fun <T> TradeValue<T>.display(format: (T) -> String): String = when (this) {
    is TradeValue.Reported -> format(value)
    is TradeValue.Missing -> reason
}

/** 값이 있을 때만 꺼낸다. 기본값으로 채우는 오버로드는 의도적으로 제공하지 않는다. */
fun <T> TradeValue<T>.valueOrNull(): T? = when (this) {
    is TradeValue.Reported -> value
    is TradeValue.Missing -> null
}
