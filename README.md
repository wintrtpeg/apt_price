# 실거래트래커 (apt_price)

서울 · 성남 · 용인 · 수원 · 동탄의 **아파트 실거래가**를 국토교통부 공공데이터로 조회하고,
단지별 · 평형별 시세 추이를 시계열 차트로 보는 안드로이드 앱.

전체 요구사항은 [`claude_code_instruction_real_estate_app.md`](claude_code_instruction_real_estate_app.md) 참고.

---

## 데이터 원칙

이 앱의 모든 숫자는 **국토교통부 실거래가 공개시스템**(공공데이터포털 Open API)에서 온 값이다.

- 임의의 Mock/더미 거래 데이터를 만들지 않는다. 값이 없으면 `거래 데이터 없음`,
  `국토교통부 미신고 건` 처럼 없다고 표시한다.
- 모든 목록 카드 · 상세 화면에 출처와 기준일시를 표기한다.
- 이 원칙은 문서가 아니라 코드로 강제한다:
  - 파서(`MoneyFormatter.parseManwon`, `AreaFormatter.parseAreaM2`,
    `TradeDateWindow.parseDealDate`)는 실패 시 `null` 을 반환하고 기본값을 채우지 않는다.
  - `TradeValue<T>` 는 `Reported` / `Missing` 두 갈래뿐이라, 값이 없는 경우를
    호출부가 반드시 다루게 만든다. "기본값으로 채우기" 오버로드는 의도적으로 없다.

---

## 기술 스택

| 항목 | 선택 |
| :--- | :--- |
| 언어 / UI | Kotlin 2.0.21 + Jetpack Compose (Material 3) |
| 최소 / 타깃 SDK | minSdk 26 (Android 8.0) / targetSdk 35 |
| DI | Hilt |
| 네트워크 | Retrofit + OkHttp + TikXml (국토부 API 는 XML 응답) |
| 로컬 캐시 | Room |
| 폰트 | Pretendard (APK 에 번들링) |

작업지시서의 `Flutter 또는 Kotlin Compose` 중 **Kotlin Compose** 를 택했다.

---

## 시작하기

### 1. 공공데이터포털 인증키 발급

1. [공공데이터포털](https://www.data.go.kr) 로그인
2. 아래 두 API 활용신청
   - 국토교통부_아파트 매매 실거래가 자료 (`getRTMSDataSvcAptTradeDev`)
   - 국토교통부_아파트 전월세 자료 (`getRTMSDataSvcAptRent`)
3. 마이페이지 → 개발계정 상세보기 → **일반 인증키(Decoding)** 복사

### 2. 키 설정

```bash
cp local.properties.example local.properties
# local.properties 를 열어 MOLIT_SERVICE_KEY 와 sdk.dir 를 채운다
```

`local.properties` 는 `.gitignore` 대상이다. **인증키를 커밋하지 말 것.**
키는 `BuildConfig.MOLIT_SERVICE_KEY` 로 주입되며, 키가 없으면 앱은 조회를 시도하지 않고
"인증키 미설정" 상태를 그대로 보여준다 (더미 데이터로 대체하지 않는다).

### 3. 빌드

```bash
./gradlew :app:assembleDebug     # APK 빌드
./gradlew :app:testDebugUnitTest # 단위 테스트
```

---

## 프로젝트 구조

```
app/src/main/kotlin/com/aptprice/tracker/
├─ core/
│  ├─ attribution/   출처 표기 문구 + TradeValue (값 없음을 타입으로 강제)
│  ├─ format/        금액(억/만원) · 면적(㎡/평) · 날짜 포맷
│  └─ time/          최근 2주 계약일 구간 ↔ DEAL_YMD 변환
├─ domain/region/    법정동 코드 카탈로그 + 동탄 법정동 필터
└─ ui/theme/         Pretendard 타이포그래피 · 컬러 · Material3 테마
```

## 조회 대상 지역 (36개 LAWD_CD)

| 그룹 | 개수 | LAWD_CD |
| :--- | ---: | :--- |
| 서울 25개 자치구 | 25 | 11110 ~ 11740 |
| 성남 (수정 · 중원 · 분당) | 3 | 41131 / 41133 / 41135 |
| 용인 (처인 · 기흥 · 수지) | 3 | 41461 / 41463 / 41465 |
| 수원 (장안 · 권선 · 팔달 · 영통) | 4 | 41111 / 41113 / 41115 / 41117 |
| 화성 — 동탄 | 1 | 41590 (법정동명으로 동탄만 필터링) |

화성시는 실거래가 API 가 시 전체를 코드 하나(41590)로 내려주므로, 응답의 법정동명(`umdNm`)이
동탄 관할인 경우만 남긴다. 대상 법정동은 `DongtanUmd.DEFAULT` (반송 · 석우 · 능 · 청계 · 영천 ·
신 · 목 · 산척 · 송 · 장지 · 오산동) 이며, 동탄2신도시 확장 구역(방교 · 중 · 금곡동)은
`DongtanUmd.EXTENDED` 로 분리해 두었다.

법정동 코드 출처: 행정안전부 「법정동코드 전체자료」(<https://www.code.go.kr>)

---

## 폰트

Pretendard 를 `app/src/main/res/font` 에 직접 번들링한다 (Regular / Medium / SemiBold / Bold).
기기 기본 한글 폰트에 의존하면 제조사 · OS 버전에 따라 렌더링이 달라지고 일부 기기에서 글리프가
빠져 네모(□)로 보이기 때문이다.

- 폰트: [Pretendard](https://github.com/orioncactus/pretendard) v1.3.9,
  (c) 2021 Kil Hyung-jin — SIL Open Font License 1.1
- 라이선스 원문: `app/src/main/assets/licenses/pretendard_ofl.txt` (APK 에 동봉)
- 재다운로드: `./scripts/fetch_pretendard.sh 1.3.9`

---

## 진행 상황

- [x] **Step 1** — 프로젝트 초기화, Pretendard 번들링, 금액/면적/날짜 유틸리티, 법정동 코드 테이블
- [ ] **Step 2** — 공공데이터포털 API 클라이언트, 최근 2주 필터, Room 캐시
- [ ] **Step 3** — 메인 화면 (최근 2주 피드, 매매/전세/월세 탭, 지역 필터)
- [ ] **Step 4** — 상세 화면 (평형 선택 칩, 시계열 라인 차트)
- [ ] **Step 5** — 무결성 검증 (Mock 데이터 배제 확인, 출처 라벨 표기 확인)
