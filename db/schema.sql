-- ============================================================
-- Ridely DDL (PostgreSQL 16+ / PostGIS / pgvector)
-- ============================================================
--
-- 명명 규칙: 테이블명 단수형 (Oracle/PostgreSQL 전통 SQL 컨벤션)
-- 예외: user_settings (settings는 영어 관용 복수형)
--
-- 사용 확장:
--   - postgis      : 공간 쿼리 (GEOMETRY, ST_Intersects, ST_DWithin 등)
--   - vector       : pgvector RAG 임베딩 검색 (Google text-embedding-004 = 768차원)
--
-- 좌표계: SRID 4326 (WGS84, 위경도 표준)
-- ============================================================

-- ============================================================
-- 0. 확장 설치
-- ============================================================
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS vector;


-- ============================================================
-- 1. region : 지역 코드 (확장성 확보용)
-- ============================================================
CREATE TABLE region (
    region_id      BIGSERIAL    PRIMARY KEY,
    region_code    VARCHAR(20)  NOT NULL UNIQUE,
    region_name    VARCHAR(50)  NOT NULL,
    parent_id      BIGINT       REFERENCES region(region_id),
    created_at     TIMESTAMPTZ  DEFAULT NOW() NOT NULL
);

COMMENT ON TABLE region IS '지역 코드 마스터. MVP는 수도권만, 향후 전국 확장 시 데이터만 추가.';

-- 시드 데이터
INSERT INTO region (region_code, region_name) VALUES
    ('11', '서울특별시'),
    ('28', '인천광역시'),
    ('41', '경기도');


-- ============================================================
-- 2. accident_zone : 자전거 사고다발지역
--    소스: 한국도로교통공단_자전거 교통사고 다발지역 (TAAS, REST API)
--    data.go.kr/data/15056681
--    선정 기준: 반경 200m 내 자전거사고 4건 이상 (사망사고 포함 시 3건 이상)
--    API 제공 필드: afos_fid, 법정동코드, 지점코드, 시도시군구명, 지점명,
--                   발생건수, 사상자수, 사망자수, 중상자수, 경상자수,
--                   부상신고자수, 경위도, 다발지역폴리곤(GeoJSON)
-- ============================================================
CREATE TABLE accident_zone (
    accident_zone_id   BIGSERIAL                    PRIMARY KEY,
    region_id          BIGINT                       NOT NULL REFERENCES region(region_id),
    afos_fid           VARCHAR(30)                  NOT NULL,          -- API 고유 식별자
    bjd_code           VARCHAR(10),                                    -- 법정동코드
    spot_code          VARCHAR(30),                                    -- 지점코드
    sido_sgg_name      VARCHAR(100),                                   -- 시도시군구명
    spot_name          VARCHAR(200),                                   -- 지점명
    occurrence_count   INT                          NOT NULL DEFAULT 0, -- 발생건수
    casualty_count     INT                          NOT NULL DEFAULT 0, -- 사상자수
    death_count        INT                          NOT NULL DEFAULT 0, -- 사망자수
    serious_inj_count  INT                          NOT NULL DEFAULT 0, -- 중상자수
    minor_inj_count    INT                          NOT NULL DEFAULT 0, -- 경상자수
    report_inj_count   INT                          NOT NULL DEFAULT 0, -- 부상신고자수
    danger_level       VARCHAR(20)                  NOT NULL,           -- 자체 파생 등급
    data_year          INT                          NOT NULL,           -- 조회 사고년도
    center_geom        GEOMETRY(Point, 4326)        NOT NULL,           -- 경위도
    polygon_geom       GEOMETRY(Polygon, 4326),                         -- 다발지역폴리곤(GeoJSON 변환)
    created_at         TIMESTAMPTZ                  DEFAULT NOW() NOT NULL,
    CONSTRAINT chk_danger_level CHECK (danger_level IN ('CAUTION','WARNING','DANGER')),
    CONSTRAINT uk_accident_fid_year UNIQUE (afos_fid, data_year)        -- 재적재 idempotency
);

CREATE INDEX idx_accident_center_gist  ON accident_zone USING GIST (center_geom);
CREATE INDEX idx_accident_polygon_gist ON accident_zone USING GIST (polygon_geom);
CREATE INDEX idx_accident_region       ON accident_zone (region_id);
CREATE INDEX idx_accident_level        ON accident_zone (danger_level);
CREATE INDEX idx_accident_year         ON accident_zone (data_year);

COMMENT ON TABLE  accident_zone IS 'TAAS 자전거 사고다발지역. 지도 히트맵 + 라이딩 중 근접 알림. 설정 ON 시 ORS avoid_polygons로 회피.';
COMMENT ON COLUMN accident_zone.danger_level IS '자체 파생: CAUTION(4-5건) / WARNING(6-9건) / DANGER(10건+ 또는 사망사고 포함). 적재 배치에서 계산.';
COMMENT ON COLUMN accident_zone.polygon_geom IS 'API의 다발지역폴리곤 GeoJSON을 변환 적재. 결측 시 center_geom 반경 200m 버퍼로 대체 가능.';


-- ============================================================
-- 3. bike_road : 자전거 도로 (속성 데이터)
--    소스: 전국자전거도로표준데이터 (CSV/API)
--    data.go.kr/data/15100063
--    주의: 이 표준데이터는 노선 형상(LineString)을 제공하지 않음.
--          기점/종점 주소·위경도만 제공하며 좌표 결측률이 높음.
--          형상은 V-World WFS 자전거길 레이어(15059118)로 별도 보강.
-- ============================================================
CREATE TABLE bike_road (
    bike_road_id       BIGSERIAL                    PRIMARY KEY,
    region_id          BIGINT                       NOT NULL REFERENCES region(region_id),
    route_name         VARCHAR(200)                 NOT NULL,           -- 노선명
    route_no           VARCHAR(50),                                     -- 노선번호
    sido_name          VARCHAR(50),                                     -- 시도명
    sigungu_name       VARCHAR(50),                                     -- 시군구명
    start_addr_road    VARCHAR(300),                                    -- 기점도로명주소
    start_addr_jibun   VARCHAR(300),                                    -- 기점지번주소
    end_addr_road      VARCHAR(300),                                    -- 종점도로명주소
    end_addr_jibun     VARCHAR(300),                                    -- 종점지번주소
    start_geom         GEOMETRY(Point, 4326),                           -- 기점위경도 (결측 多)
    end_geom           GEOMETRY(Point, 4326),                           -- 종점위경도 (결측 多)
    via_points         VARCHAR(500),                                    -- 주요경유지
    total_length_km    NUMERIC(7,2),                                    -- 총길이(km)
    road_width_m       NUMERIC(5,1),                                    -- 일반도로너비(m)
    bike_width_m       NUMERIC(5,1),                                    -- 자전거도로너비(m)
    road_type          VARCHAR(50),                                     -- 자전거도로종류
    is_notified        BOOLEAN,                                         -- 자전거도로고시유무 (결측 시 NULL)
    mgmt_agency        VARCHAR(100),                                    -- 관리기관명
    mgmt_phone         VARCHAR(30),                                     -- 관리기관전화번호
    data_std_date      DATE,                                            -- 데이터기준일자
    line_geom          GEOMETRY(LineString, 4326),                      -- V-World WFS 등으로 후보강
    created_at         TIMESTAMPTZ                  DEFAULT NOW() NOT NULL
);

CREATE INDEX idx_bikeroad_start_gist ON bike_road USING GIST (start_geom);
CREATE INDEX idx_bikeroad_line_gist  ON bike_road USING GIST (line_geom);
CREATE INDEX idx_bikeroad_region     ON bike_road (region_id);
CREATE INDEX idx_bikeroad_type       ON bike_road (road_type);

COMMENT ON TABLE  bike_road IS '전국자전거도로표준데이터 속성. 소스에 노선 형상 없음 — line_geom은 V-World WFS 자전거길 레이어로 별도 보강.';
COMMENT ON COLUMN bike_road.road_type IS '자전거전용도로 / 자전거보행자겸용도로 / 자전거전용차로 / 자전거우선도로 (소스 표기 그대로, 결측 존재)';


-- ============================================================
-- 4. national_bike_route : 국토종주 자전거길 (13개 노선)
--    소스: 행정안전부_자전거길 DB (CSV 파일)
--    data.go.kr/data/3038533
--    도로 위경도 좌표 시퀀스 → 적재 시 LineString으로 변환
-- ============================================================
CREATE TABLE national_bike_route (
    national_bike_route_id  BIGSERIAL                        PRIMARY KEY,
    route_name              VARCHAR(100)                     NOT NULL UNIQUE,  -- 예: 한강종주자전거길
    start_desc              VARCHAR(200),                                      -- 예: 아라한강갑문
    end_desc                VARCHAR(200),                                      -- 예: 충주댐
    total_length_km         NUMERIC(6,1),                                      -- 예: 192.0
    line_geom               GEOMETRY(LineString, 4326)       NOT NULL,         -- 좌표 시퀀스 변환
    created_at              TIMESTAMPTZ                      DEFAULT NOW() NOT NULL
);

CREATE INDEX idx_natroute_line_gist ON national_bike_route USING GIST (line_geom);

COMMENT ON TABLE national_bike_route IS '국토종주 자전거 13길. CSV의 도로 위경도 시퀀스를 ST_MakeLine으로 변환 적재. 전국 노선이라 region FK 없음.';


-- ============================================================
-- 5. route_facility : 자전거길 주변 시설 (인증센터/화장실/급수대/공기주입기)
--    소스: 행정안전부_자전거길 DB (CSV 파일, 위와 동일) + 서울시 자전거 편의시설 API(tvBicycleEtc, AIR_PUMP)
--    급수대가 여기 포함되므로 별도 water_fountain 테이블 불필요
-- ============================================================
CREATE TABLE route_facility (
    route_facility_id       BIGSERIAL                PRIMARY KEY,
    national_bike_route_id  BIGINT                   REFERENCES national_bike_route(national_bike_route_id) ON DELETE SET NULL,
    facility_type           VARCHAR(20)              NOT NULL,
    facility_name           VARCHAR(200),
    geom                    GEOMETRY(Point, 4326)    NOT NULL,
    created_at              TIMESTAMPTZ              DEFAULT NOW() NOT NULL,
    CONSTRAINT chk_facility_type CHECK (facility_type IN ('CERT_CENTER','TOILET','WATER','AIR_PUMP','ETC'))
);

CREATE INDEX idx_facility_geom_gist ON route_facility USING GIST (geom);
CREATE INDEX idx_facility_type      ON route_facility (facility_type);
CREATE INDEX idx_facility_route     ON route_facility (national_bike_route_id);

COMMENT ON COLUMN route_facility.facility_type IS 'CERT_CENTER(인증센터) / TOILET(화장실) / WATER(급수대) / AIR_PUMP(공기주입기) / ETC(기타)';


-- ============================================================
-- 6. bike_parking : 자전거 보관소
--    소스: 행안부 자전거보관소정보 조회서비스 (data.go.kr/data/15059118)
--    매일 갱신되며 MNG_NO 기준 UPSERT.
--    주의: 보관소(주차 거치대)이며 따릉이 대여소가 아님.
--          따릉이 대여소가 필요하면 서울열린데이터광장 API를 별도 소스로 추가.
-- ============================================================
CREATE TABLE bike_parking (
    bike_parking_id    BIGSERIAL                PRIMARY KEY,
    region_id          BIGINT                   REFERENCES region(region_id),  -- 좌표 역매핑, 실패 시 NULL
    mgmt_no            VARCHAR(30)              NOT NULL UNIQUE,               -- 행안부 관리번호(MNG_NO). 재적재 idempotency
    parking_name       VARCHAR(200),
    geom               GEOMETRY(Point, 4326)    NOT NULL,
    mgmt_agency        VARCHAR(100),
    raw_attrs          JSONB,                                                  -- 원본 속성 보존
    created_at         TIMESTAMPTZ              DEFAULT NOW() NOT NULL
);

CREATE INDEX idx_parking_geom_gist ON bike_parking USING GIST (geom);
CREATE INDEX idx_parking_region    ON bike_parking (region_id);

COMMENT ON TABLE bike_parking IS '행안부 자전거보관소정보 조회서비스(전국 자치단체 취합). 매일 갱신되며 MNG_NO 기준 UPSERT.';
COMMENT ON COLUMN bike_parking.mgmt_no IS '행안부 관리번호(MNG_NO). 매일 갱신되는 소스라 이 값 기준으로 UPSERT한다.';


-- ============================================================
-- 7. tour_attraction : 관광 콘텐츠 통합 (관광지/문화시설/축제/음식점 등)
--    소스: 한국관광공사_국문 관광정보 서비스_GW (TourAPI 4.0, 15101578)
--    MVP: contentTypeId 12(관광지)·14(문화시설)·39(음식점) 우선, 15(축제) 확장
--    scenery 가중치 + AI 경유지 추천 + 라이딩 중 근접 알림 소재
-- ============================================================
CREATE TABLE tour_attraction (
    tour_attraction_id  BIGSERIAL                PRIMARY KEY,
    region_id           BIGINT                   REFERENCES region(region_id),  -- areacode 매핑, 실패 시 NULL
    content_id          VARCHAR(20)              NOT NULL UNIQUE,   -- TourAPI contentid (재적재 idempotency)
    content_type_id     VARCHAR(2)               NOT NULL,          -- 12/14/15/25/28/32/38/39
    title               VARCHAR(300)             NOT NULL,
    cat1                VARCHAR(10),                                 -- 대분류 코드
    cat2                VARCHAR(10),                                 -- 중분류 코드
    cat3                VARCHAR(10),                                 -- 소분류 코드
    addr1               VARCHAR(300),
    addr2               VARCHAR(100),
    geom                GEOMETRY(Point, 4326)    NOT NULL,           -- mapx(경도)/mapy(위도)
    tel                 VARCHAR(50),
    first_image_url     VARCHAR(500),
    thumbnail_url       VARCHAR(500),                                -- firstimage2
    overview            TEXT,                                         -- detailCommon
    event_start_date    DATE,                                        -- 행사(15) 전용
    event_end_date      DATE,                                        -- 행사(15) 전용
    area_code           VARCHAR(10),
    sigungu_code        VARCHAR(10),
    source_modified_at  TIMESTAMPTZ,                                 -- modifiedtime (증분 동기화 기준)
    created_at          TIMESTAMPTZ              DEFAULT NOW() NOT NULL,
    updated_at          TIMESTAMPTZ              DEFAULT NOW() NOT NULL,
    CONSTRAINT chk_tour_content_type CHECK (content_type_id IN ('12','14','15','25','28','32','38','39'))
);

CREATE INDEX idx_tour_geom_gist  ON tour_attraction USING GIST (geom);
CREATE INDEX idx_tour_type       ON tour_attraction (content_type_id);
CREATE INDEX idx_tour_region     ON tour_attraction (region_id);
CREATE INDEX idx_tour_modified   ON tour_attraction (source_modified_at);
CREATE INDEX idx_tour_event_date ON tour_attraction (event_start_date, event_end_date)
    WHERE content_type_id = '15';

COMMENT ON TABLE  tour_attraction IS 'TourAPI 관광 콘텐츠 통합. 경로 주변 ST_DWithin 검색으로 경유지 후보 추출. 축제(15)는 event 기간으로 유효성 필터.';
COMMENT ON COLUMN tour_attraction.content_type_id IS '12:관광지 14:문화시설 15:축제공연행사 25:여행코스 28:레포츠 32:숙박 38:쇼핑 39:음식점';
COMMENT ON COLUMN tour_attraction.source_modified_at IS 'TourAPI 동기화 목록(modifiedtime) 기반 증분 업데이트.';


-- ============================================================
-- 8. bike_station : 따릉이 대여소
--    소스: 서울열린데이터광장 공공자전거 대여소 정보 (마스터) + 실시간 대여정보 API
--    MVP 인프라 필수 (제안서: 따릉이 이용자 타깃)
-- ============================================================
CREATE TABLE bike_station (
    bike_station_id    BIGSERIAL                PRIMARY KEY,
    region_id          BIGINT                   REFERENCES region(region_id),
    station_code       VARCHAR(20)              NOT NULL UNIQUE,   -- 서울시 대여소 ID (재적재 idempotency)
    station_name       VARCHAR(200)             NOT NULL,
    geom               GEOMETRY(Point, 4326)    NOT NULL,
    rack_count         INT,                                         -- 거치대 수
    is_active          BOOLEAN                  DEFAULT TRUE NOT NULL,
    created_at         TIMESTAMPTZ              DEFAULT NOW() NOT NULL,
    updated_at         TIMESTAMPTZ              DEFAULT NOW() NOT NULL
);

CREATE INDEX idx_station_geom_gist ON bike_station USING GIST (geom);
CREATE INDEX idx_station_region    ON bike_station (region_id);

COMMENT ON TABLE bike_station IS '따릉이 대여소 마스터. 실시간 잔여대수는 DB 적재 없이 서울시 실시간 API 직접 호출 (변동 잦음).';


-- ============================================================
-- 9. repair_shop : 자전거 수리센터
--    소스: 서울시 25개 자치구 수리센터 현황 (자치구별 공공데이터, 수동 취합 가능성 있음)
--    MVP 인프라 필수 (제안서: 경유점·근접 알림)
-- ============================================================
CREATE TABLE repair_shop (
    repair_shop_id   BIGSERIAL                PRIMARY KEY,
    region_id        BIGINT                   REFERENCES region(region_id),
    shop_name        VARCHAR(200)             NOT NULL,
    geom             GEOMETRY(Point, 4326)    NOT NULL,
    addr             VARCHAR(300),
    is_free          BOOLEAN                  DEFAULT FALSE NOT NULL,   -- 자치구 무료 수리소 여부
    operating_hours  VARCHAR(200),
    tel              VARCHAR(50),
    created_at       TIMESTAMPTZ              DEFAULT NOW() NOT NULL
);

CREATE INDEX idx_repair_geom_gist ON repair_shop USING GIST (geom);
CREATE INDEX idx_repair_region    ON repair_shop (region_id);

COMMENT ON TABLE repair_shop IS '자치구별 수리센터. 자치구마다 데이터 포맷이 달라 초기엔 CSV 수동 정제 적재 가능성 높음.';


-- ============================================================
-- 10. app_user : 회원
-- ============================================================
CREATE TABLE app_user (
    user_id        BIGSERIAL       PRIMARY KEY,
    login_id       VARCHAR(50)     NOT NULL UNIQUE,
    password_hash  VARCHAR(255)    NOT NULL,
    nickname       VARCHAR(50)     NOT NULL,
    email          VARCHAR(100),
    status         VARCHAR(20)     DEFAULT 'ACTIVE' NOT NULL,
    last_login_at  TIMESTAMPTZ,
    created_at     TIMESTAMPTZ     DEFAULT NOW() NOT NULL,
    updated_at     TIMESTAMPTZ     DEFAULT NOW() NOT NULL,
    CONSTRAINT chk_user_status CHECK (status IN ('ACTIVE','SUSPENDED','WITHDRAWN'))
);

CREATE INDEX idx_user_email  ON app_user (email);
CREATE INDEX idx_user_status ON app_user (status);


-- ============================================================
-- 11. refresh_token : JWT 리프레시 토큰
-- ============================================================
CREATE TABLE refresh_token (
    refresh_token_id  BIGSERIAL       PRIMARY KEY,
    user_id           BIGINT          NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    token_hash        VARCHAR(255)    NOT NULL,
    expires_at        TIMESTAMPTZ     NOT NULL,
    revoked_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ     DEFAULT NOW() NOT NULL
);

CREATE INDEX idx_rtoken_user    ON refresh_token (user_id);
CREATE INDEX idx_rtoken_expires ON refresh_token (expires_at);


-- ============================================================
-- 12. user_settings : 사용자 환경설정 (1:1)
-- ============================================================
CREATE TABLE user_settings (
    user_settings_id              BIGSERIAL       PRIMARY KEY,
    user_id                       BIGINT          NOT NULL UNIQUE REFERENCES app_user(user_id) ON DELETE CASCADE,
    avoid_danger_zones            BOOLEAN         DEFAULT FALSE NOT NULL,
    default_priority_convenience  NUMERIC(3,2)    DEFAULT 0.50 NOT NULL,
    default_priority_exercise     NUMERIC(3,2)    DEFAULT 0.30 NOT NULL,
    default_priority_scenery      NUMERIC(3,2)    DEFAULT 0.20 NOT NULL,
    units                         VARCHAR(10)     DEFAULT 'km' NOT NULL,
    notification_enabled          BOOLEAN         DEFAULT TRUE NOT NULL,
    vibration_enabled             BOOLEAN         DEFAULT TRUE NOT NULL,
    created_at                    TIMESTAMPTZ     DEFAULT NOW() NOT NULL,
    updated_at                    TIMESTAMPTZ     DEFAULT NOW() NOT NULL,
    CONSTRAINT chk_usettings_units     CHECK (units IN ('km','mile')),
    CONSTRAINT chk_usettings_p_conv    CHECK (default_priority_convenience BETWEEN 0 AND 1),
    CONSTRAINT chk_usettings_p_exer    CHECK (default_priority_exercise    BETWEEN 0 AND 1),
    CONSTRAINT chk_usettings_p_scen    CHECK (default_priority_scenery     BETWEEN 0 AND 1)
);

COMMENT ON TABLE  user_settings IS '회원당 1행. 코스 작성·UI 환경설정. 합계=1.00 검증은 애플리케이션 레이어.';
COMMENT ON COLUMN user_settings.avoid_danger_zones IS 'TRUE면 코스 작성 시 ORS avoid_polygons 자동 적용';


-- ============================================================
-- 13. recommended_route : AI 추천 경로 기록
-- ============================================================
CREATE TABLE recommended_route (
    recommended_route_id        BIGSERIAL                       PRIMARY KEY,
    user_id                     BIGINT                          REFERENCES app_user(user_id) ON DELETE SET NULL,
    start_geom                  GEOMETRY(Point, 4326)           NOT NULL,
    end_geom                    GEOMETRY(Point, 4326),
    target_distance_km          NUMERIC(5,2),
    priority_convenience        NUMERIC(3,2)                    DEFAULT 0.50,
    priority_exercise           NUMERIC(3,2)                    DEFAULT 0.30,
    priority_scenery            NUMERIC(3,2)                    DEFAULT 0.20,
    avoid_danger_zones_applied  BOOLEAN                         DEFAULT FALSE,
    route_geom                  GEOMETRY(LineString, 4326),
    total_distance_km           NUMERIC(7,3),
    estimated_duration_min      INT,
    total_ascent_m              INT,
    total_descent_m             INT,
    intensity_level             VARCHAR(20),
    waypoints_json              JSONB,
    passing_danger_zones_json   JSONB,
    ai_title                    VARCHAR(200),
    ai_highlights               JSONB,
    ai_coach_comment            TEXT,
    ai_danger_zone_alert        TEXT,
    ai_next_step_suggestion     VARCHAR(500),
    llm_provider                VARCHAR(30),
    created_at                  TIMESTAMPTZ                     DEFAULT NOW() NOT NULL,
    CONSTRAINT chk_priority_conv CHECK (priority_convenience BETWEEN 0 AND 1),
    CONSTRAINT chk_priority_exer CHECK (priority_exercise    BETWEEN 0 AND 1),
    CONSTRAINT chk_priority_scen CHECK (priority_scenery     BETWEEN 0 AND 1),
    CONSTRAINT chk_intensity     CHECK (intensity_level IN ('LIGHT','MODERATE','HARD','CHALLENGE'))
);

CREATE INDEX idx_recroute_user        ON recommended_route (user_id);
CREATE INDEX idx_recroute_created     ON recommended_route (created_at DESC);
CREATE INDEX idx_recroute_start_gist  ON recommended_route USING GIST (start_geom);
CREATE INDEX idx_recroute_route_gist  ON recommended_route USING GIST (route_geom);
-- JSONB GIN 인덱스 (waypoints 검색용)
CREATE INDEX idx_recroute_waypoints   ON recommended_route USING GIN (waypoints_json);

COMMENT ON COLUMN recommended_route.intensity_level IS 'LIGHT(가볍게) / MODERATE(적당히) / HARD(끌어올림) / CHALLENGE(도전)';


-- ============================================================
-- 14. saved_route : 사용자가 저장한 경로
-- ============================================================
CREATE TABLE saved_route (
    saved_route_id        BIGSERIAL       PRIMARY KEY,
    user_id               BIGINT          NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    recommended_route_id  BIGINT          NOT NULL REFERENCES recommended_route(recommended_route_id) ON DELETE CASCADE,
    custom_name           VARCHAR(100),
    memo                  TEXT,
    is_favorite           BOOLEAN         DEFAULT FALSE NOT NULL,
    created_at            TIMESTAMPTZ     DEFAULT NOW() NOT NULL,
    CONSTRAINT uk_saved_user_route UNIQUE (user_id, recommended_route_id)
);

CREATE INDEX idx_saved_user      ON saved_route (user_id);
CREATE INDEX idx_saved_favorite  ON saved_route (user_id, is_favorite);
CREATE INDEX idx_saved_created   ON saved_route (user_id, created_at DESC);


-- ============================================================
-- 15. recommendation_cache : 동일 요청 결과 캐싱
-- ============================================================
CREATE TABLE recommendation_cache (
    recommendation_cache_id  BIGSERIAL       PRIMARY KEY,
    cache_key                VARCHAR(64)     NOT NULL UNIQUE,
    response_payload         JSONB           NOT NULL,
    expires_at               TIMESTAMPTZ     NOT NULL,
    created_at               TIMESTAMPTZ     DEFAULT NOW() NOT NULL
);

CREATE INDEX idx_cache_expires ON recommendation_cache (expires_at);

COMMENT ON COLUMN recommendation_cache.cache_key IS 'SHA256(start + end + priorities + targetDistance + avoid_danger_zones)';


-- ============================================================
-- 16. riding_session : 라이딩 세션 기록 (실주행)
--     제안서 측정 체계의 원천: 라이딩 세션 수, 완주율, 페이스,
--     방문 관광지 수 집계. riding_history_embedding의 임베딩 원천.
-- ============================================================
CREATE TABLE riding_session (
    riding_session_id     BIGSERIAL                    PRIMARY KEY,
    user_id               BIGINT                       NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    recommended_route_id  BIGINT                       REFERENCES recommended_route(recommended_route_id) ON DELETE SET NULL,
    started_at            TIMESTAMPTZ                  NOT NULL,
    ended_at              TIMESTAMPTZ,                                   -- NULL = 진행 중
    distance_km           NUMERIC(6,2),                                  -- 실주행 거리
    avg_speed_kmh         NUMERIC(4,1),                                  -- 평균 페이스
    is_completed          BOOLEAN                      DEFAULT FALSE NOT NULL,  -- 완주 여부 (완주율 분자)
    visited_poi_count     INT                          DEFAULT 0 NOT NULL,      -- 방문 관광지 수 (근접 인정 기준)
    alert_received_count  INT                          DEFAULT 0 NOT NULL,      -- 사고다발지 알림 수신 수
    track_geom            GEOMETRY(LineString, 4326),                    -- GPS 트랙 (수집 시)
    created_at            TIMESTAMPTZ                  DEFAULT NOW() NOT NULL,
    CONSTRAINT chk_session_time CHECK (ended_at IS NULL OR ended_at >= started_at)
);

CREATE INDEX idx_session_user       ON riding_session (user_id, started_at DESC);
CREATE INDEX idx_session_route      ON riding_session (recommended_route_id);
CREATE INDEX idx_session_track_gist ON riding_session USING GIST (track_geom);

COMMENT ON TABLE  riding_session IS '실주행 세션. 완주율 = is_completed 비율. 추천 없이 자유 라이딩하면 recommended_route_id NULL.';
COMMENT ON COLUMN riding_session.is_completed IS '추천 경로의 도착점 반경 도달 여부 등 완주 판정 로직은 앱 레이어에서 정의.';
COMMENT ON COLUMN riding_session.track_geom IS 'GPS 포인트 시퀀스. 배터리/저장량 고려해 샘플링 간격은 앱에서 조절 (예: 10초/50m).';


-- ============================================================
-- 17. riding_history_embedding : RAG용 라이딩 이력 벡터 (pgvector)
--     임베딩 원천: riding_session (세션 요약 텍스트 → 벡터화)
-- ============================================================
CREATE TABLE riding_history_embedding (
    riding_history_embedding_id  BIGSERIAL       PRIMARY KEY,
    user_id                      BIGINT          NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    recommended_route_id         BIGINT          REFERENCES recommended_route(recommended_route_id) ON DELETE CASCADE,
    distance_km                  NUMERIC(7,3),
    intensity_level              VARCHAR(20),
    preference_text              TEXT            NOT NULL,
    embedding                    vector(768)     NOT NULL,
    created_at                   TIMESTAMPTZ     DEFAULT NOW() NOT NULL,
    CONSTRAINT chk_embedding_intensity CHECK (intensity_level IN ('LIGHT','MODERATE','HARD','CHALLENGE'))
);

-- 사용자별 일반 인덱스 (검색 시 1차 필터)
CREATE INDEX idx_riding_embed_user    ON riding_history_embedding (user_id);
CREATE INDEX idx_riding_embed_created ON riding_history_embedding (user_id, created_at DESC);

-- HNSW 인덱스 (코사인 거리 기준 유사도 검색)
CREATE INDEX idx_riding_embed_hnsw ON riding_history_embedding
    USING HNSW (embedding vector_cosine_ops);

COMMENT ON TABLE  riding_history_embedding IS 'RAG용 라이딩 이력 임베딩. 라이딩 종료 시 RidingHistoryIndexer가 비동기 적재.';
COMMENT ON COLUMN riding_history_embedding.embedding IS 'Google text-embedding-004 (768차원)';
COMMENT ON COLUMN riding_history_embedding.preference_text IS '"{distance}km {intensity} 코스, 주요 경유: {waypoints}, 출발지역: {start_area}" 형식';


-- ============================================================
-- 끝
-- ============================================================
-- 테이블 17개:
--   [공공데이터]
--   1. region                    (지역 코드 마스터)
--   2. accident_zone             (TAAS 자전거 사고다발지역, API 15056681)
--   3. bike_road                 (전국자전거도로표준데이터, 15100063)
--   4. national_bike_route       (행안부 자전거길 DB 국토종주 13길, 3038533)
--   5. route_facility            (자전거길 주변 시설: 인증센터/화장실/급수대/공기주입기, 3038533)
--   6. bike_parking              (행안부 자전거보관소정보 조회서비스, 15059118)
--   7. tour_attraction           (관광공사 국문 관광정보 서비스_GW, 15101578)
--   8. bike_station              (따릉이 대여소, 서울열린데이터광장)
--   9. repair_shop               (자치구 수리센터, 자치구별 데이터)
--   [서비스]
--   10. app_user
--   11. refresh_token
--   12. user_settings
--   13. recommended_route
--   14. saved_route
--   15. recommendation_cache
--   16. riding_session           (실주행 기록 — 측정 체계 원천)
--   17. riding_history_embedding (RAG, 원천: riding_session)

