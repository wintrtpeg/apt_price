package com.aptprice.tracker.domain.region

/**
 * 앱이 조회하는 전체 대상 지역 카탈로그.
 *
 * 모든 LAWD_CD 는 행정안전부 「법정동코드 전체자료」의 시군구 코드(10자리 중 앞 5자리)이며,
 * 국토교통부 실거래가 Open API 의 `LAWD_CD` 파라미터로 그대로 사용한다.
 * 여기 있는 값은 실제 고시 코드이며, 임의로 생성한 값이 없다.
 */
object RegionCatalog {

    /** 서울특별시 25개 자치구 전역. */
    val seoul: List<Region> = listOf(
        seoul("11110", "종로구"),
        seoul("11140", "중구"),
        seoul("11170", "용산구"),
        seoul("11200", "성동구"),
        seoul("11215", "광진구"),
        seoul("11230", "동대문구"),
        seoul("11260", "중랑구"),
        seoul("11290", "성북구"),
        seoul("11305", "강북구"),
        seoul("11320", "도봉구"),
        seoul("11350", "노원구"),
        seoul("11380", "은평구"),
        seoul("11410", "서대문구"),
        seoul("11440", "마포구"),
        seoul("11470", "양천구"),
        seoul("11500", "강서구"),
        seoul("11530", "구로구"),
        seoul("11545", "금천구"),
        seoul("11560", "영등포구"),
        seoul("11590", "동작구"),
        seoul("11620", "관악구"),
        seoul("11650", "서초구"),
        seoul("11680", "강남구"),
        seoul("11710", "송파구"),
        seoul("11740", "강동구"),
    )

    /** 성남시 3개 일반구 (판교·서현·야탑·위례 등 포함). */
    val seongnam: List<Region> = listOf(
        gyeonggi("41131", "성남시 수정구", "수정구", RegionGroup.SEONGNAM),
        gyeonggi("41133", "성남시 중원구", "중원구", RegionGroup.SEONGNAM),
        gyeonggi("41135", "성남시 분당구", "분당구", RegionGroup.SEONGNAM),
    )

    /** 용인시 3개 일반구 (풍덕천·상현·보정·신갈 등 포함). */
    val yongin: List<Region> = listOf(
        gyeonggi("41461", "용인시 처인구", "처인구", RegionGroup.YONGIN),
        gyeonggi("41463", "용인시 기흥구", "기흥구", RegionGroup.YONGIN),
        gyeonggi("41465", "용인시 수지구", "수지구", RegionGroup.YONGIN),
    )

    /** 수원시 4개 일반구 (광교·매탄·망포·인계 등 포함). */
    val suwon: List<Region> = listOf(
        gyeonggi("41111", "수원시 장안구", "장안구", RegionGroup.SUWON),
        gyeonggi("41113", "수원시 권선구", "권선구", RegionGroup.SUWON),
        gyeonggi("41115", "수원시 팔달구", "팔달구", RegionGroup.SUWON),
        gyeonggi("41117", "수원시 영통구", "영통구", RegionGroup.SUWON),
    )

    /**
     * 화성시(41590) — 동탄 관할 법정동만 필터링해서 사용한다.
     * API 는 화성시 전체를 한 번에 내려주므로 [Region.umdWhitelist] 로 동탄만 남긴다.
     */
    val dongtan: Region = Region(
        lawdCd = "41590",
        sido = "경기도",
        sigungu = "화성시",
        displayName = "동탄",
        group = RegionGroup.DONGTAN,
        umdWhitelist = DongtanUmd.DEFAULT,
        // 확장 목록까지 저장해 두면 대상 동을 넓히는 설정이 생겨도 재조회가 필요 없다.
        storageUmdWhitelist = DongtanUmd.EXTENDED,
    )

    /** 전체 조회 대상 지역 (서울 25 + 성남 3 + 용인 3 + 수원 4 + 화성 1 = 36). */
    val all: List<Region> = seoul + seongnam + yongin + suwon + listOf(dongtan)

    private val byCode: Map<String, Region> = all.associateBy { it.lawdCd }

    /** 실제 API 호출이 필요한 고유 LAWD_CD 목록. */
    val lawdCodes: List<String> = all.map { it.lawdCd }

    fun byLawdCd(lawdCd: String): Region? = byCode[lawdCd]

    fun byGroup(group: RegionGroup): List<Region> = all.filter { it.group == group }

    /**
     * 특정 지역의 응답 행이 화면에 노출되어도 되는지 판정한다.
     * 알 수 없는 LAWD_CD 는 대상 지역이 아니므로 false 를 반환한다.
     */
    fun accepts(lawdCd: String, umdNm: String?): Boolean =
        byCode[lawdCd]?.includesUmd(umdNm) ?: false

    /**
     * 이 행을 캐시에 저장해도 되는지 판정한다.
     * 알 수 없는 LAWD_CD 는 대상 지역이 아니므로 저장하지 않는다.
     */
    fun storable(lawdCd: String, umdNm: String?): Boolean =
        byCode[lawdCd]?.storesUmd(umdNm) ?: false

    private fun seoul(lawdCd: String, gu: String) = Region(
        lawdCd = lawdCd,
        sido = "서울특별시",
        sigungu = gu,
        displayName = gu,
        group = RegionGroup.SEOUL,
    )

    private fun gyeonggi(
        lawdCd: String,
        sigungu: String,
        displayName: String,
        group: RegionGroup,
    ) = Region(
        lawdCd = lawdCd,
        sido = "경기도",
        sigungu = sigungu,
        displayName = displayName,
        group = group,
    )
}
