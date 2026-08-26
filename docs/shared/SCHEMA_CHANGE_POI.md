# 스키마 변경 제안 — POI 인프라 적재

> 대상: `db/schema.sql` (v1.1) | 변경 2건 | 작성 사유: 자전거 인프라 POI 적재 착수
>
> ⚠️ **경로 변경 (2026-08-12)**: 정본 DDL은 `db/schema.sql`에서 **`src/main/resources/db/migration/V1__initial_schema.sql`** 로 이동했다.
> Flyway가 앱 기동 시 자동 적용하므로 아래 "팀원 수동 실행" 절차는 더 이상 필요 없다. 본문의 `db/schema.sql` 언급은 작성 당시 기준이다.

---

## 1. 요약

| # | 테이블 | 변경 | 성격 |
|---|---|---|---|
| 1 | `route_facility` | `facility_type` 허용값에 `AIR_PUMP` 추가 | **누락 보완** — 지금 상태로는 적재가 실패한다 |
| 2 | `bike_parking` | `mgmt_no` 컬럼 추가 (UNIQUE) | 재적재 멱등성 확보 |

둘 다 **기존 데이터에 영향이 없다.** 두 테이블 모두 비어 있고, 기존 컬럼의 타입·제약을 바꾸지 않는다.

---

## 2. 검증 근거

제안 내용은 전부 실데이터로 확인했다. 추정치가 아니다.

| 데이터 | 검증 방법 | 결과 |
|---|---|---|
| 행안부 자전거길 DB | CSV 1,133행 직접 분석 | 구분 **4종**, 좌표 결측 0 |
| 서울시 자전거 편의시설 | API 전량 3,375건 수집·집계 | 좌표 결측 0, `USE_YN` 전 건 "사용" |
| 행안부 자전거보관소 | API 실호출 | 서울 2,698건, WGS84 좌표, `MNG_NO` 고유 |
| V-World WFS 자전거보관소 | WFS 실호출 (`lt_p_bycracks`) | GeoJSON·WGS84로 응답. **고유키 없음** |
| 이용허락 (전 데이터) | 데이터셋 메타·약관 확인 | `DATA_SOURCES.md` 5장에 정리 |
| 따릉이 대여소 | API 실호출 | 3,237건, **스키마 변경 불필요** |
| TAAS 사고다발지 | API 실호출 | 폴리곤을 GeoJSON으로 직접 제공, **스키마 변경 불필요** |
| ORS 라우팅 | 실호출 (선유도→여의도) | 한강 자전거길 정상 주행. 고도값은 6.5 참조 |

---

## 3. 배경

POI 인프라 적재를 시작하면서 각 테이블의 소스 데이터를 실제로 열어봤다.
두 군데에서 스키마를 작성할 때의 가정과 실제 데이터가 어긋났다.

| 테이블 | 스키마 작성 시 가정 | 실제 데이터 |
|---|---|---|
| `route_facility` | 소스 구분값이 인증센터·화장실·급수대 3종 | **공기주입기를 포함한 4종** |
| `bike_parking` | 소스가 V-World WFS (속성 스키마 유동적, 고유키 특정 불가) | 소스를 **행안부 자전거보관소정보 API**로 확정. 고유 관리번호 제공 |

---

## 4. 변경 1 — `route_facility`에 `AIR_PUMP` 추가

### 4.1 무엇을

```sql
-- AS-IS
CONSTRAINT chk_facility_type CHECK (facility_type IN ('CERT_CENTER','TOILET','WATER','ETC'))

-- TO-BE
CONSTRAINT chk_facility_type CHECK (facility_type IN ('CERT_CENTER','TOILET','WATER','AIR_PUMP','ETC'))
```

컬럼 추가가 아니라 **허용값 한 개 추가**다.

### 4.2 왜 필요한가 — 지금 상태로는 적재가 실패한다

이 테이블의 소스인 **행정안전부 자전거길 DB**(`★국토종주자전거길 주변시설 좌표정보.csv`)를
직접 열어보니 `구분` 컬럼의 값이 4종이었다.

| 구분 | 건수 (중복 제거 후) | CHECK 허용 여부 |
|---|---|---|
| 화장실 | 688 | ✅ `TOILET` |
| 급수대 | 185 | ✅ `WATER` |
| 인증센터 | 88 | ✅ `CERT_CENTER` |
| **공기주입기** | **63** | ❌ **없음** |

**이 CSV를 그대로 적재하면 63건이 CHECK 제약 위반으로 실패한다.**
새로운 데이터를 넣겠다는 요구가 아니라, 원래 이 테이블이 받기로 한 소스에 있던
구분값이 스키마에 반영되지 않은 것이다.

### 4.3 MVP 구간 실측치 (참고)

한강 서울 구간(위도 37.500~37.610 / 경도 126.780~127.130) 기준이다.
서비스 영역에 실제로 적재될 양이다.

| 구분 | 원본 | 중복 제거 후 |
|---|---|---|
| 화장실 | 187 | 97 |
| 급수대 | 152 | **75** |
| 인증센터 | 5 | 4 |
| 공기주입기 | 0 | 0 |
| **합계** | 267 | **176** |

급수대 75건이 아라한강갑문(경도 126.817)부터 잠실(127.123)까지 고르게 분포한다.
코스 추천 API의 `includeWaterFountains` 옵션은 이 데이터만으로 정상 동작한다.

### 4.4 이 변경이 함께 열어주는 것 — 서울시 공기주입기 1,099건

위 표에서 보듯 **MVP 구간에 공기주입기가 0건**이다. 국토종주 노선 기준 데이터라
서울 도심 구간이 비어 있다.

이 공백은 별도 소스인 **서울시 자전거 편의시설 API**(`tvBicycleEtc`)로 채울 수 있다.
전량 3,375건을 수집해 분포를 확인했다.

| 시설 종류 | 건수 | 적재 대상 |
|---|---|---|
| 거치대·보관대 | 약 1,715 | `bike_parking` (변경 2 참조) |
| **공기주입기** | **약 1,099** | **`route_facility` AIR_PUMP** |
| 수리센터 | 약 23 | `repair_shop` |

좌표 결측 0건, `USE_YN` 전 건 "사용"으로 품질이 확인됐다.

**이 적재를 위해 추가로 필요한 스키마 변경은 없다.** DDL은 4.1과 동일하다.
행안부 63건만으로도 변경이 필요하지만, 서울 데이터까지 함께 밝혀 두어
나중에 같은 논의를 반복하지 않으려 한다.

### 4.5 왜 `route_facility`가 맞는가

이 테이블의 정의가 "자전거길 주변 시설"이고, 공기주입기는 원 소스가 이미 같은
`구분` 컬럼으로 관리하는 항목이다. 별도 분류가 아니다.

기존 컬럼을 그대로 쓴다.

| 컬럼 | 채울 값 |
|---|---|
| `facility_type` | `'AIR_PUMP'` |
| `facility_name` | 시설명 (4.6 참조) |
| `geom` | 좌표 (두 소스 모두 WGS84, 결측 0건) |
| `national_bike_route_id` | 행안부 = 노선 매핑 / 서울시 = `NULL` (이미 nullable) |

조회 API도 그대로 동작한다. `PoiItemDTO.facilityType`에 값 하나만 늘리면
`GET /pois/nearby?types=ROUTE_FACILITY`로 조회된다.

### 4.6 검토했다가 접은 대안

| 대안 | 접은 이유 |
|---|---|
| **`facility_type = 'ETC'`로 뭉갠다** | 스키마 변경은 없지만 급수대·화장실과 구분이 사라진다. 프론트가 마커 아이콘을 결정할 수 없고, 추천 파이프라인이 "공기주입기만" 고를 수 없다. 원 소스가 구분해서 주는 정보를 버리는 셈이다 |
| **`air_pump` 신규 테이블** | 컬럼 구성이 `route_facility`와 사실상 같다. 조회 시 UNION이 하나 늘고 `PoiItemDTO`에 타입이 추가된다. 무엇보다 원 소스에서 같은 컬럼으로 오는 값을 굳이 분리하게 된다 |
| **공기주입기 행을 걸러내고 적재** | 63건을 버리는 것이고, 소스가 갱신되면 다시 문제가 된다. CHECK 위반을 피하려고 데이터를 버리는 건 순서가 뒤바뀐 해결이다 |

### 4.7 영향 범위

- 기존 데이터: **없음** (테이블이 비어 있음)
- 기존 코드: **없음** (이 테이블을 읽는 코드가 아직 없음)
- 이후 반영: `PoiItemDTO.facilityType` 주석에 `AIR_PUMP` 추가

---

## 5. 변경 2 — `bike_parking`에 `mgmt_no` 추가

### 5.1 무엇을

```sql
mgmt_no  VARCHAR(30)  NOT NULL UNIQUE   -- 행안부 관리번호(MNG_NO). 재적재 idempotency
```

### 5.2 왜 필요한가

**현재 `bike_parking`에는 같은 데이터를 두 번 넣으면 그대로 중복이 쌓인다.**

```sql
CREATE TABLE bike_parking (
    bike_parking_id  BIGSERIAL              PRIMARY KEY,
    region_id        BIGINT                 REFERENCES region(region_id),
    parking_name     VARCHAR(200),
    geom             GEOMETRY(Point, 4326)  NOT NULL,
    mgmt_agency      VARCHAR(100),
    raw_attrs        JSONB,
    created_at       TIMESTAMPTZ            DEFAULT NOW() NOT NULL
);
```

UNIQUE 제약이 하나도 없다. 원래 소스로 가정했던 V-World WFS는 속성 스키마가
유동적이라 고유키를 특정할 수 없어서 `raw_attrs`로 원본을 통째로 보존하는 설계였다.

확정된 소스인 **행안부 자전거보관소정보 조회서비스**는 `MNG_NO`라는 고유 관리번호를 준다.

```json
"MNG_NO": "202630700000300110",
"BCCL_STRGE_NM": "성북초등학교 후문",
"WGS84_LAT": "37.594052",
"WGS84_LOT": "126.998777",
"KPNGBX_CNT": "17"
```

이 데이터는 **매일 갱신된다**(명세 명시). 즉 재적재가 예외가 아니라 전제다.

### 5.3 다른 테이블과 같은 방식이다

재적재 대상 테이블은 이미 전부 소스 고유키에 UNIQUE를 걸어 두었다.

| 테이블 | 멱등 키 | 소스 |
|---|---|---|
| `tour_attraction` | `content_id` UNIQUE | TourAPI contentid |
| `accident_zone` | `(afos_fid, data_year)` UNIQUE | TAAS |
| `bike_station` | `station_code` UNIQUE | 따릉이 `RENT_ID` |
| **`bike_parking`** | **없음** | ← 여기만 예외 |

`bike_parking`만 예외로 두면 적재 코드 패턴이 갈린다. 다른 테이블은 전부
`INSERT ... ON CONFLICT (고유키) DO UPDATE` 한 줄로 끝나는데 여기만 다른 방식이 된다.

### 5.4 검토했다가 접은 대안

| 대안 | 접은 이유 |
|---|---|
| **전량 DELETE 후 INSERT** | 적재 때마다 `bike_parking_id`가 바뀐다. 프론트가 POI id를 들고 있거나 나중에 저장경로가 참조하면 조용히 깨진다. 적재 중 조회하면 빈 결과가 나가는 구간도 생긴다 |
| **`raw_attrs`의 `MNG_NO`에 표현식 UNIQUE 인덱스** | 동작은 하지만 조회·조인에서 `raw_attrs->>'MNG_NO'`를 매번 써야 한다. 고유키를 스키마에서 눈으로 확인할 수 없다 |
| **`(parking_name, geom)` 복합 UNIQUE** | 좌표가 소수점 아래에서 미세하게 바뀌면 다른 행이 된다. 이름이 빈 행도 있을 수 있다 |

### 5.5 소스 선택 근거 — V-World와 실제로 비교했다

기존 스키마가 지정한 V-World WFS도 인증키를 발급받아 직접 호출해 비교했다.

```
GET https://api.vworld.kr/req/wfs
    ?SERVICE=WFS&REQUEST=GetFeature&VERSION=1.1.0
    &TYPENAME=lt_p_bycracks          ← 자전거보관소 레이어
    &OUTPUT=application/json&SRSNAME=EPSG:4326
    &KEY=…&DOMAIN=…
```

| 항목 | 행안부 API | V-World WFS |
|---|---|---|
| 좌표 | WGS84 | WGS84 |
| 응답 형식 | JSON | GeoJSON |
| **고유키** | **`MNG_NO`** | **없음 (WFS FID `lt_p_bycracks.13268`뿐)** |
| 갱신 주기 | 매일 (명세 명시) | 불명 |
| 페이징 | `pageNo`/`numOfRows` | 1,000건 제한, 문서상 버전별 파라미터가 모순 |
| 지역 필터 | 주소 LIKE | BBOX (공간 조회) |
| 규모 | 서울 2,698건 확인 | 미확인 |

**행안부를 선택한 이유는 고유키다.** V-World 응답의 속성에는 관리번호가 없고,
`id`는 WFS 내부 FID라 데이터 갱신 시 유지된다는 보장이 없다.
고유키가 없으면 UPSERT를 할 수 없고 전량 교체만 가능하다(5.4 참조).

### 5.6 두 소스는 같은 원천으로 보인다

속성이 서로 대응한다.

| V-World | 행안부 | 의미 |
|---|---|---|
| `byc_cnt` | `KPNGBX_CNT` | 보관대수 |
| `rak_typ` | `INSTL_SHP` | 설치·거치 형태 |
| `pump_yn` / `pump_typ` | `AIRPMP_FRNSH_YN` / `AIRPMP_TYPE_NM` | 공기주입기 |
| `fix_yn` | `RPRSTD_INSTL_YN` | 수리대 |
| `org_nam` / `org_tel` | `MNG_INST_NM` / `MNG_INST_TELNO` | 관리기관 |

자치단체가 올린 같은 데이터를 두 부처가 각각 배포하는 것으로 보인다.
따라서 "어느 쪽이 정확한가"가 아니라 **"어느 배포 채널이 쓰기 편한가"**의 문제이며,
고유키·명확한 페이징·매일 갱신을 갖춘 행안부가 낫다는 판단이다.

> ⚠️ 행안부 명세 문서에 "좌표계 : 보정계수 안들어간 Bessel 중부원점TM(EPSG:5174)"라고
> 적혀 있으나, 실제 응답값은 `37.594052 / 126.998777`로 **WGS84가 맞다.**
> 명세 설명이 잘못된 것으로 판단해 `ST_Transform` 없이 적재한다.

### 5.7 같이 검토 부탁 (선택)

**`updated_at TIMESTAMPTZ DEFAULT NOW() NOT NULL`도 추가할지.**

`tour_attraction`·`bike_station` 등 재적재 대상 테이블은 모두 갖고 있는 컬럼이다.
UPSERT 시 갱신 시각을 남길 수 있다. 없어도 적재는 동작하므로 변경 2와 분리해 판단해도 된다.

### 5.8 테이블 주석 갱신 필요

소스가 바뀌었으므로 현재 주석이 사실과 다르다.

```sql
-- AS-IS
COMMENT ON TABLE bike_parking IS 'V-World WFS 자전거보관소 레이어. ...';

-- TO-BE
COMMENT ON TABLE bike_parking IS '행안부 자전거보관소정보 조회서비스(전국 자치단체 취합). 매일 갱신되며 MNG_NO 기준 UPSERT.';
```

### 5.9 영향 범위

- 기존 데이터: **없음** (테이블이 비어 있음)
- 기존 코드: **없음**
- `NOT NULL` 추가지만 빈 테이블이라 기본값 없이 바로 걸 수 있다

---

## 6. 스키마 변경은 아니지만 공유할 것

소스 데이터를 확인하며 발견한 사실들이다. DDL 변경은 필요 없지만 정본 관리 차원에서 남긴다.

### 6.1 `route_facility.facility_name`에 넣을 값이 부족하다

행안부 CSV의 `이름` 컬럼이 **시설명이 아니라 노선명**이다.

```
급수대   한강종주길              ← MVP 구간 75건이 전부 같은 값
화장실   한강종주길
인증센터  아라한강갑문인증센터      ← 인증센터만 실제 시설명
```

그대로 넣으면 지도에서 급수대 75개가 전부 "한강종주길"로 표시된다.
적재 시 `facility_type`과 조합하거나(`한강종주길 급수대`) NULL로 두고 타입으로 표시하는
방향을 검토 중이다. 스키마 변경 없이 적재 로직에서 처리한다.

다만 이 컬럼 덕분에 **`national_bike_route_id` FK를 채울 근거**가 생긴다.

### 6.2 `national_bike_route` — 주석이 맞았다 (13개 노선)

소스 CSV의 노선 코드가 41개라 처음엔 주석("국토종주 자전거길 13개 노선")이
틀린 줄 알았으나, **데이터셋 공식 설명을 확인하니 주석이 맞다.**

> 행정안전부_자전거길 DB (`data.go.kr/data/3038533`)
> "**국토종주 자전거길 13개**에 대한 도로 위도 및 경도에 대한 정보를 제공하며
> 인증센터, 화장실, 급수대 등 국토종주 자전거길 주변에 있는 시설들에 대한
> 위치정보를 제공합니다."

```
코드 1~13    국토종주 13길 ← 본 데이터
             아라·한강종주·남한강·새재·낙동강·금강·영산강·북한강·섬진강·오천·
             동해안(강원)·동해안(경북)·제주환상
코드 14~46   지역 자전거길 (강릉 경포호 산소길, 파주 DMZ 자전거길 등) ← 부가 제공분
```

**코드 1~13만 적재한다.** 테이블 주석과 스키마 변경 모두 필요 없다.

좌표는 총 53,418점이며 그중 한강종주자전거길이 877점, 아라자전거길이 118점이다.
MVP 구간에 해당하는 두 노선만으로 995점이다.

### 6.3 소스 CSV에 중복이 있다

행안부 시설 CSV 1,133행 중 **109건이 중복**이다(같은 구분·이름·좌표가 2회).
MVP 구간만 보면 267 → 176건으로 34%가 중복이다.
적재 시 중복 제거가 필요하며, 이는 적재 로직에서 처리한다.

### 6.4 TAAS `sido_sgg_nm`에 일련번호가 붙는다

```
"서울 강남구1", "서울 강남구2", "서울 강남구3"
```

`accident_zone.sido_sgg_name`에 그대로 넣으면 표시용으로 쓸 수 없다.
`spot_nm`(`서울 강남구 압구정동(압구정그래피티 부근)`)이 훨씬 구체적이므로 그쪽을 쓴다.
스키마 변경은 필요 없다.

### 6.5 `recommended_route`의 고도·소요시간이 과대 계상된다

ORS 라우팅을 실호출해 확인했다. 선유도공원 → 여의도한강공원(4.8km) 구간이다.

```
ascent  137.9 m      ← 응답값
descent 126.6 m
고도 범위 1.0 ~ 32.0 m  ← 같은 응답의 bbox
```

**표고차가 31m인데 누적 상승이 138m**로 나온다. 한강변은 거의 평지라 실제로는
20~30m 수준이어야 한다.

영향받는 컬럼은 두 개다.

| 컬럼 | 영향 |
|---|---|
| `total_ascent_m` | `intensity_level` 산출 기준이라 **평지 코스가 `HARD`로 분류될 수 있다** |
| `estimated_duration_min` | 상승고도를 반영해 속도가 낮아진다. 4.8km에 21.7분(13.3km/h)으로 실제보다 느리다 |

#### 원인 분석 (2026-08-14)

W3 ORS 클라이언트로 재호출해 좌표 116점의 고도를 직접 계산했다.
`elevation=true`면 좌표가 `[경도, 위도, 고도]`로 오므로 응답만으로 검증할 수 있다.

**ORS는 양의 고도 변화를 그냥 전부 더한다.**

```
단순 양의 변화량 합산 = 137.8 m      (ORS 응답 137.9 m)
```

소수점까지 재현된다. 어떤 필터도 걸려 있지 않다.

**노이즈는 작은 흔들림이 아니라 거대한 스파이크다.** 자전거로 오를 수 없는 경사가 섞여 있다.

| 구간 | 수평 | 고도 변화 | 경사 |
|---|---|---|---|
| 48→49 | 12.7 m | +15.1 m | **119%** |
| 8→9 | 14.8 m | +11.3 m | 76% |
| 26→27 | 36.6 m | +17.0 m | 46% |

경사 20% 초과 구간이 13개이고 **여기서만 68.6m** — 전체 상승의 절반이
물리적으로 불가능한 구간에서 나온다.

**원인은 표본 간격이다.** 좌표 점 간격 중앙값이 33m인데 SRTM 격자가 30~90m다.
점마다 다른 셀의 오차를 독립적으로 집으므로, 경로를 촘촘히 뜰수록 노이즈가 누적된다.

#### ~~보정 방법 — 크기 임계값이 아니라 경사 상한이다~~ (2026-08-26 폐기)

> ⚠️ **이 절이 지시하던 경사 상한 방식은 실측으로 반증됐다. 만들지 말 것.**
>
> 아래 표는 선유도-여의도 한 구간만 본 것이다. 그때 남긴 숙제("여러 구간을 실측해 비교해야 한다")를 수행한 결과 **방법 자체가 무너졌다.**
>
> | 구간 | 표고차 | ORS 원값 | 경사 6% 상한 |
> |---|---|---|---|
> | 선유도-여의도 (평지) | 31.0 | 119.5 | 21.7 |
> | **남산 (오르막)** | **236.0** | 265.0 | **12.9** |
> | **광교산 (오르막)** | **251.4** | 441.7 | **28.5** |
>
> 236m를 오르는 코스가 12.9m로 나온다. 점 간격이 18~25m라 평균 경사 8%여도 구간별로 크게 흔들리고, **상한이 노이즈와 실제 급경사를 구분하지 못해 둘 다 잘라낸다.**
>
> 리샘플링과 국토지리정보원 DEM도 검토했고 전부 막혔다. **결론은 고도를 강도 판정에 넣지 않는 것이다.** 전체 실측과 근거는 `SPIKE_ELEVATION.md`에 있다.

아래는 폐기 전 기록이다. 선유도-여의도 4.8km 한 구간 기준이다.

| 필터 | 누적 상승 |
|---|---|
| 없음 (ORS 원값) | 137.8 m |
| 크기 임계 3 m | 129.9 m |
| 크기 임계 10 m | 104.0 m |
| 경사 상한 15% | 51.9 m |
| 경사 상한 6% | 21.7 m |

**크기 임계값이 안 통한다는 관찰은 여전히 유효하다.** 노이즈가 작은 흔들림이 아니라 큰 스파이크로 오기 때문이다. 다만 그 대안으로 제시한 경사 상한이 답이 아니었을 뿐이다.

#### 대응

스키마 변경은 필요 없다. ORS 값을 그대로 저장하되 **`intensity_level` 산출에서 고도를 빼고
거리만 쓴다.** 표시용 값(`total_ascent_m`)은 원값을 유지한다 — 우리가 보정한 숫자를
사용자에게 보여주려면 근거가 더 필요하고, **그 근거를 만들 방법이 지금은 없다.**

```
IntensityCalculator          거리만으로 판정
  └ AscentEstimator          주입 가능
      └ RawAscentEstimator          ORS 값 그대로 — 유일한 구현
```

`GradientCappedEstimator`를 만들었다가 폐기했다. `OrsRouteResult.geometryGeoJson`이 3D 좌표를
담고 있어 재계산 입력은 확보돼 있지만, **입력이 있어도 맞는 값을 낼 방법이 없다.**

> 다시 볼 조건 — 5m급 DEM 접근권을 얻거나, ORS를 셀프호스팅하며 고품질 고도를 넣거나,
> 실제 주행 GPS 고도 기록이 쌓여 정답을 알게 될 때다. `SPIKE_ELEVATION.md` 10장 참조.

---

## 7. 지금은 제안하지 않는 것

같은 맥락에서 나올 법한 이야기인데, 근거가 약하거나 불필요해서 뺐다.

| 항목 | 뺀 이유 |
|---|---|
| `bike_station` 변경 | 따릉이 API 실호출로 확인. `RENT_ID`·`RENT_NM`·`HOLD_NUM`·좌표가 기존 컬럼과 그대로 대응한다. **변경 불필요** |
| `accident_zone` 변경 | TAAS API 실호출로 확인. `geom_json`이 GeoJSON Polygon 문자열로 와서 `ST_GeomFromGeoJSON`에 바로 넣을 수 있다. **변경 불필요** |
| `repair_shop`에 고유키 추가 | 서울 수리센터가 23건 규모라 전량 교체로 충분하다. 소스의 `FCLT_ID` 형식이 자치구마다 제각각(`광진구_자전거종합서비스센터`, `11103419_1740730605325`)이라 고유키로 삼기 불안하다 |
| `route_facility`에 고유키 추가 | 소스별로 `facility_type`이 갈리므로 `DELETE WHERE facility_type='AIR_PUMP'` 후 INSERT로 부분 교체가 가능하다. 다른 타입 데이터를 건드리지 않는다 |
| `bike_road` 관련 일체 | 소스는 확정됐으나 적재 시점이 이후다. **9장(차후 반영 계획)** 참조 |

---

## 8. 마이그레이션

`db/schema.sql`(정본)을 수정하고, 이미 로컬 DB를 만들어 둔 팀원은 아래를 실행한다.
통합 테스트는 컨테이너를 새로 띄우며 정본 DDL을 적용하므로 별도 조치가 필요 없다.

```sql
BEGIN;

-- 변경 1: route_facility 허용값 보완
ALTER TABLE route_facility DROP CONSTRAINT chk_facility_type;
ALTER TABLE route_facility ADD  CONSTRAINT chk_facility_type
      CHECK (facility_type IN ('CERT_CENTER','TOILET','WATER','AIR_PUMP','ETC'));

COMMENT ON COLUMN route_facility.facility_type IS
    'CERT_CENTER(인증센터) / TOILET(화장실) / WATER(급수대) / AIR_PUMP(공기주입기) / ETC(기타)';

-- 변경 2: bike_parking 멱등 키
ALTER TABLE bike_parking ADD COLUMN mgmt_no VARCHAR(30) NOT NULL;
ALTER TABLE bike_parking ADD CONSTRAINT uk_parking_mgmt_no UNIQUE (mgmt_no);

COMMENT ON COLUMN bike_parking.mgmt_no IS
    '행안부 관리번호(MNG_NO). 매일 갱신되는 소스라 이 값 기준으로 UPSERT한다.';
COMMENT ON TABLE  bike_parking IS
    '행안부 자전거보관소정보 조회서비스(전국 자치단체 취합). 매일 갱신되며 MNG_NO 기준 UPSERT.';

COMMIT;
```

### 롤백

```sql
BEGIN;
ALTER TABLE bike_parking DROP CONSTRAINT uk_parking_mgmt_no;
ALTER TABLE bike_parking DROP COLUMN mgmt_no;

ALTER TABLE route_facility DROP CONSTRAINT chk_facility_type;
ALTER TABLE route_facility ADD  CONSTRAINT chk_facility_type
      CHECK (facility_type IN ('CERT_CENTER','TOILET','WATER','ETC'));
COMMIT;
```

두 테이블 모두 비어 있어 데이터 손실 없이 되돌릴 수 있다.

---

## 9. 차후 반영 계획

이번 제안 범위는 아니지만 소스가 확정됐거나 방향이 정해진 것들이다.
지금 승인받을 필요는 없고, 착수 시점에 필요한 변경만 별도로 제안하겠다.

### 9.1 `bike_road` — 전국자전거도로표준데이터

소스가 확정됐다.

```
GET https://api.data.go.kr/openapi/tn_pubr_public_bike_road_api
    ?serviceKey=…&type=json&ctpvNm=서울특별시&pageNo=1&numOfRows=1000
```

**응답 22개 항목 중 20개가 기존 컬럼과 1:1로 대응한다.** 스키마가 이 소스를 보고
설계됐음이 확인된다.

| API | 컬럼 | | API | 컬럼 |
|---|---|---|---|---|
| `rteNm` / `routeNum` | `route_name` / `route_no` | | `majorStopover` | `via_points` |
| `ctpvNm` / `sggNm` | `sido_name` / `sigungu_name` | | `totalLength` | `total_length_km` |
| `roadStPoint*Addr` | `start_addr_road` / `_jibun` | | `roadWidth` / `bikeRoadWidth` | `road_width_m` / `bike_width_m` |
| `roadEdPoint*Addr` | `end_addr_road` / `_jibun` | | `bikeRoadType` / `bikeRoadNotiChk` | `road_type` / `is_notified` |
| `roadStPointLat` / `Lon` | `start_geom` | | `mngInstNm` / `instTelno` | `mgmt_agency` / `mgmt_phone` |
| `roadEdPointLat` / `Lon` | `end_geom` | | `crtrYmd` | `data_std_date` |

대응하지 않는 `instt_code`·`instt_nm`(제공기관)은 사용처가 없다.

**적재를 뒤로 미루는 이유**

- 이 API는 **노선 형상(LineString)을 제공하지 않는다.** 기점·종점 점 두 개뿐이라
  경로 선호·회피에 쓸 수 없다
- `preferBikeOnlyRoads` 옵션은 **ORS가 처리한다.** 한강 자전거길을 정상 주행하는 것을
  실호출로 확인했다(2장 검증 근거)
- `/pois/nearby`는 점 데이터만 다룬다. 선 데이터용 조회 API가 아직 없다

즉 지금 적재해도 소비처가 없다. **W3 파이프라인 관통 이후**로 미룬다.

**착수 시 예상되는 검토 항목**

| 항목 | 내용 |
|---|---|
| 고유키 | 응답에 관리번호가 없다. `bike_road`에도 UNIQUE가 없어 재적재 시 중복이 쌓인다. `bike_parking`과 같은 문제 |
| 좌표 결측 | 기존 주석이 "결측률이 높음"이라고 경고한다. 실측 후 판단 |
| `line_geom` | 형상 소스가 여전히 미확인이다. **10장 참조** |

### 9.2 `repair_shop` — 수자원공사 자전거길 대여·수리점

`한국수자원공사_자전거대여수리점안내`(15155614)를 확인했다. CSV 205행,
중복 제거 시 96개 지점이다.

**서울이 0건**이고 경기도 24건도 양평·여주·가평이라 MVP 구간(경도 126.780~127.130)
밖이다. 서울 수리센터는 서울시 편의시설 API(약 23건)로 채운다.

국토종주 코스로 서비스 영역을 넓힐 때 그대로 쓸 수 있어 **보류만 하고 폐기하지는 않는다.**

---

## 10. 확인 요청 — V-World WFS

스키마의 두 곳이 **V-World WFS**를 소스로 지정하고 있다.

```sql
-- bike_parking
-- 소스: 국토교통부_전국자전거보관소 (V-World WMS/WFS), data.go.kr/data/15059118
COMMENT ON TABLE bike_parking IS 'V-World WFS 자전거보관소 레이어. 같은 WFS의
  자전거길(라인)/자전거길노드 레이어는 bike_road.line_geom 보강용으로 활용.';

-- bike_road
-- 형상은 V-World WFS 자전거길 레이어(15059118)로 별도 보강.
COMMENT ON COLUMN bike_road.line_geom IS '... V-World WFS 등으로 후보강';
```

### 자전거보관소 레이어는 확인 완료

인증키를 발급받아 `lt_p_bycracks`를 직접 호출했다. 결과는 5.5·5.6에 정리했다.
**고유키가 없어 행안부 API를 쓰기로 판단했다.**

### 확인이 필요한 것 — 자전거길 형상 레이어

`bike_road.line_geom`에 넣을 **노선 형상(LineString)** 소스를 찾지 못했다.

> 전국자전거도로표준데이터(9.1)는 이 문제를 해결하지 못한다.
> 그 API는 기점·종점 좌표만 주고 형상을 제공하지 않으며,
> 기존 주석도 그 점을 명시하며 형상은 V-World로 보강한다고 적고 있다.

주석이 참조하는 **자전거길(라인)·자전거길노드 레이어를 찾지 못했다.**
V-World WMS/WFS 레이어 목록 169종 전체를 확인했으나 해당 레이어가 없다.

```
체육 (5종):  국립자연공원 / 군립자연공원 / 도립자연공원 / 등산로 / 자전거보관소
교통 (4종):  교통CCTV / 교통노드 / 교통링크 / 도로중심선
```

등산로(`lt_l_frstclimb`)는 있는데 자전거길은 없다.

다만 **국토교통부_레저정보도**(data.go.kr 15058383)라는 별도 항목이 있고,
소개 문구에 "하이킹 코스·자전거길노드·자전거길·자전거보관소를 제공한다"고 되어 있다.
같은 V-World 계열이지만 위 169종 목록과는 다른 서비스일 가능성이 있다.

**질문**

1. 주석에 적은 자전거길 레이어를 **어디서 확인했는지** — 레이어명이나 화면을 알려주면
   `bike_road.line_geom` 보강 계획을 이어갈 수 있다
2. 레저정보도(15058383)를 의도한 것이라면 그쪽 명세를 확인하겠다
3. 확인된 소스가 아니라면 `line_geom`을 **채우지 않는 컬럼으로 유지**하거나
   대체 소스를 다시 찾겠다 (현재 이 컬럼을 읽는 코드가 없어 급하지 않다)

### 이 확인이 막는 것은 없다

변경 1·2와 무관하다. `bike_road`는 이번 제안 범위 밖이고, `bike_parking` 소스는
V-World를 직접 호출해 비교한 결과로 결정했다. 회신 전에 승인해도 된다.

---

## 11. 결정 필요 항목

- [ ] 변경 1 — `route_facility`에 `AIR_PUMP` 추가 *(누락 보완. 미적용 시 행안부 CSV 63건 적재 실패)*
- [ ] 변경 2 — `bike_parking`에 `mgmt_no` 추가
- [ ] 변경 2-1 (선택) — `bike_parking`에 `updated_at` 추가
- [x] ~~6.2 — `national_bike_route` 적재 범위~~ **해소.** 공식 설명이 "국토종주 13개"를
      명시해 주석이 맞았다. 코드 1~13만 적재하며 스키마 변경 없음
- [ ] **10장 — V-World 자전거길 형상 레이어를 어디서 확인했는지 (`bike_road.line_geom` 존폐)**
- [ ] 스키마 버전을 v1.2로 올릴지
