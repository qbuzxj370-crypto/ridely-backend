-- ============================================================
-- V3 : accident_zone.polygon_geom 을 MultiPolygon 으로
-- ============================================================
--
-- 배경
--   TAAS 활용가이드(v1.1)의 geom_json 항목은 Polygon 예시만 싣고 있다.
--     geom_json | 다발지역폴리곤 | 4000 | 필수
--       샘플: {"type":"Polygon","coordinates":[[[127.0377743,37.6515711],...
--
--   그런데 실호출에서 MultiPolygon 이 돌아왔다. 같은 API, 같은 자치구, 연도만 다르다.
--     searchYearCd=2023  서울 강남구  ->  "type":"Polygon"
--     searchYearCd=2022  서울 강남구  ->  "type":"MultiPolygon"
--
--   명세와 실제가 다르므로 실제를 따른다. Polygon 컬럼에 MultiPolygon 을 넣으면
--   "Geometry type does not match column type" 으로 적재가 통째로 실패한다
--   (national_bike_route 에서 이미 같은 방식으로 한 번 터졌다).
--
--   PostGIS 는 단일 파트도 MultiPolygon 에 담을 수 있으므로 ST_Multi() 로 통일하면
--   적재·조회 코드에 타입 분기가 생기지 않는다. V1 의 line_geom 과 같은 판단이다.
--
-- 영향
--   accident_zone 은 아직 비어 있어 데이터 손실이 없다.
--   USING 절은 이미 들어간 행이 있어도 안전하도록 붙였다.
-- ============================================================

ALTER TABLE accident_zone
    ALTER COLUMN polygon_geom TYPE GEOMETRY(MultiPolygon, 4326)
    USING ST_Multi(polygon_geom);

COMMENT ON COLUMN accident_zone.polygon_geom IS
    'API의 다발지역폴리곤 GeoJSON을 ST_Multi로 감싸 적재. 결측 시 center_geom 반경 200m 버퍼로 대체 가능.
     명세는 Polygon만 예시하지만 실제로 MultiPolygon이 오는 연도가 있어 MultiPolygon으로 통일한다.';
