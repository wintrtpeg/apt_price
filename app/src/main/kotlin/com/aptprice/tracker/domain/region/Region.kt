package com.aptprice.tracker.domain.region

/**
 * 국토교통부 실거래가 Open API 의 조회 단위인 시군구(법정동 코드 5자리) 를 표현한다.
 *
 * `LAWD_CD` 는 행정안전부가 고시하는 10자리 법정동코드의 앞 5자리(시도 2 + 시군구 3)이며,
 * 국토교통부 실거래가 API 는 이 5자리 값을 지역 파라미터로 받는다.
 *
 * 출처: 행정안전부 「법정동코드 전체자료」(https://www.code.go.kr)
 */
data class Region(
    /** 법정동 코드 5자리. 예) 11680 = 서울특별시 강남구 */
    val lawdCd: String,
    /** 시/도 명칭. 예) 서울특별시, 경기도 */
    val sido: String,
    /** 시군구 전체 명칭. 예) 강남구, 성남시 분당구, 화성시 */
    val sigungu: String,
    /** 목록/칩에 노출할 짧은 이름. 예) 강남구, 분당구, 동탄 */
    val displayName: String,
    /** 지역 묶음(필터 그룹) */
    val group: RegionGroup,
    /**
     * 시군구 전체가 아니라 일부 법정동만 대상으로 삼을 때 사용하는 화이트리스트.
     *
     * `null` 이면 해당 시군구의 모든 법정동을 대상으로 한다.
     * 화성시(41590) 처럼 시 단위 코드 하나에 동탄 외 지역이 함께 묶여 있는 경우에만 값을 갖는다.
     */
    val umdWhitelist: Set<String>? = null,
    /**
     * 캐시에 **저장할 때** 쓰는 화이트리스트. 기본값은 [umdWhitelist] 와 같다.
     *
     * 동탄처럼 대상 법정동 목록을 설정에서 바꿀 수 있는 지역은, 고를 수 있는 모든 법정동을
     * 미리 저장해 두면 설정을 바꿔도 다시 받아올 필요가 없다.
     * (읽을 때는 [umdWhitelist] 로 다시 거르므로 화면에 보이는 범위는 달라지지 않는다)
     */
    val storageUmdWhitelist: Set<String>? = umdWhitelist,
) {
    init {
        require(lawdCd.length == 5 && lawdCd.all { it.isDigit() }) {
            "LAWD_CD 는 숫자 5자리여야 합니다: $lawdCd"
        }
    }

    /** API 응답의 법정동명(umdNm)이 이 지역의 조회 대상인지 판정한다. */
    fun includesUmd(umdNm: String?): Boolean = matches(umdWhitelist, umdNm)

    /** 캐시에 저장할 대상인지 판정한다. [includesUmd] 보다 넓거나 같다. */
    fun storesUmd(umdNm: String?): Boolean = matches(storageUmdWhitelist, umdNm)

    private fun matches(whitelist: Set<String>?, umdNm: String?): Boolean {
        if (whitelist == null) return true
        val normalized = umdNm?.trim().orEmpty()
        if (normalized.isEmpty()) return false
        return normalized in whitelist
    }
}

/** 화면 상단 지역 필터의 묶음 단위. */
enum class RegionGroup(val label: String) {
    SEOUL("서울"),
    SEONGNAM("성남"),
    YONGIN("용인"),
    SUWON("수원"),
    DONGTAN("동탄"),
}
