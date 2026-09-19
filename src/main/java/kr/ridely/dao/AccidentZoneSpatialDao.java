package kr.ridely.dao;

import kr.ridely.dto.poi.PoiItemDTO;
import kr.ridely.dto.route.PassingDangerZoneDTO;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 사고다발지역 공간 조회 DAO.
 *
 * PostGIS 연산자(ST_Intersects·ST_GeomFromGeoJSON)를 사용하므로 JdbcClient를 쓴다 (ADR-002).
 *
 * 적재용 AccidentZoneDao와 나눈 이유는 생명주기가 다르기 때문이다. 저쪽은 배치가 부르고 이쪽은 사용자 요청이 부른다. TourIngestDao와 TourSpatialDao를 나눈 것과 같은 기준이다.
 */
@Repository
public class AccidentZoneSpatialDao {

    /** PoiItemDTO.type 값 */
    private static final String TYPE_ACCIDENT_ZONE = "ACCIDENT_ZONE";

    private final JdbcClient jdbcClient;

    public AccidentZoneSpatialDao(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * 사고다발지역을 한 점 반경에서 가까운 순으로 조회한다. 지도 레이어용이다.
     *
     * <b>거리는 폴리곤이 아니라 중심점으로 잰다.</b> 폴리곤 경계까지의 거리로 재면 구역 안에 서 있을 때 0m가 되어 정렬이 무너진다. 화면에 찍을 마커도 중심점이라, 경계로 재면 거리를 잰 지점과 마커 위치가 어긋난다.
     *
     * ⚠️ <b>등급으로 거르지 않는다.</b> {@link #findAvoidGeometry}는 회피 대상만 고르지만 여기는 보여주는 것이 목적이라 주의 등급도 지도에 나와야 한다. 무엇을 숨길지는 화면이 정한다.
     *
     * 최신 연도만 본다. 이유는 {@link #findPassing} 참조.
     */
    public List<PoiItemDTO> findNearby(double lng, double lat, int radiusM, int limit) {
        String sql = """
                WITH center AS (
                    SELECT ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography AS g
                )
                SELECT
                    a.accident_zone_id AS id,
                    a.spot_name        AS name,
                    a.danger_level,
                    a.occurrence_count,
                    a.death_count,
                    ST_Y(a.center_geom) AS lat,
                    ST_X(a.center_geom) AS lng,
                    ST_AsGeoJSON(a.polygon_geom) AS polygon_geo_json,
                    ROUND(ST_Distance(a.center_geom::geography, c.g))::int AS distance_m
                FROM accident_zone a, center c
                WHERE a.data_year = (SELECT MAX(data_year) FROM accident_zone)
                  AND ST_DWithin(a.center_geom::geography, c.g, :radiusM)
                ORDER BY distance_m
                LIMIT :limit
                """;

        return jdbcClient.sql(sql)
                .param("lng", lng)
                .param("lat", lat)
                .param("radiusM", radiusM)
                .param("limit", limit)
                .query((rs, rowNum) -> {
                    PoiItemDTO dto = new PoiItemDTO(TYPE_ACCIDENT_ZONE, rs.getLong("id"),
                            rs.getString("name"), rs.getDouble("lat"), rs.getDouble("lng"),
                            rs.getInt("distance_m"));
                    dto.setDangerLevel(rs.getString("danger_level"));
                    dto.setOccurrenceCount(rs.getInt("occurrence_count"));
                    dto.setDeathCount(rs.getInt("death_count"));
                    dto.setPolygonGeoJson(rs.getString("polygon_geo_json"));
                    return dto;
                })
                .list();
    }

    /**
     * 사고다발지역 전부(최신 연도). 앱이 통째로 받아 폰에서 GPS로 거르는 전체 조회용이다
     * ({@code GET /pois/all}).
     *
     * <b>폴리곤은 담지 않는다.</b> 마커와 이름·등급·건수만 있으면 주변 표시가 되고, 폴리곤은
     * 구역 하나가 수십 개 좌표라 전체를 담으면 응답이 이 종류 하나로 커진다. 도형이 필요한 화면은
     * 코스 추천 응답의 통과 구역(passingDangerZones)이나 반경 조회를 쓴다.
     *
     * 기준점이 없으므로 좌표·반경 인자를 두지 않는다 — 인자가 생기면 위치정보를 서버로 받지
     * 않는다는 「전부 준다」 계약이 깨진다. 최신 연도만 보는 이유는 {@link #findPassing} 참조.
     * ORDER BY id는 응답 본문(ETag)을 실행마다 같게 하려는 것이다.
     */
    public List<PoiItemDTO> findAll() {
        return jdbcClient.sql("""
                        SELECT
                            a.accident_zone_id AS id,
                            a.spot_name        AS name,
                            a.danger_level,
                            a.occurrence_count,
                            a.death_count,
                            ST_Y(a.center_geom) AS lat,
                            ST_X(a.center_geom) AS lng
                        FROM accident_zone a
                        WHERE a.data_year = (SELECT MAX(data_year) FROM accident_zone)
                        ORDER BY a.accident_zone_id
                        """)
                .query((rs, rowNum) -> {
                    PoiItemDTO dto = new PoiItemDTO(TYPE_ACCIDENT_ZONE, rs.getLong("id"),
                            rs.getString("name"), rs.getDouble("lat"), rs.getDouble("lng"), null);
                    dto.setDangerLevel(rs.getString("danger_level"));
                    dto.setOccurrenceCount(rs.getInt("occurrence_count"));
                    dto.setDeathCount(rs.getInt("death_count"));
                    return dto;
                })
                .list();
    }

    /**
     * 회피 대상 구역을 하나의 도형으로 합쳐 GeoJSON으로 돌려준다. 라우팅 엔진의 avoid_polygons에 그대로 넣는 값이다.
     *
     * 등급으로 거른다. 전 등급을 피하면 우회가 거리를 크게 늘리는데(실측 12km 목표에 15.5km, +29%) 그 우회의 대부분이 주의 등급 구역 때문이다. 무엇을 피할지가 곧 거리를 조절하는 손잡이라, 제약을 이진으로 켜고 끄는 대신 강도로 다룬다. 기본값은 application.yml의 ridely.route.avoid-danger-levels에 있다.
     *
     * 적재된 구역 전체를 대상으로 한다. 지역이나 경로 주변으로 좁히지 않는다.
     *
     * 경로 주변으로 좁히는 방법은 쓸 수 없다. 거리 보정 연장점이 어디에 붙을지는 경로를 그려 봐야 알기 때문에, 출발지·도착지를 잇는 축 주변으로 자르면 그 연장 구간이 조회 범위 밖으로 나가 회피가 뚫린다.
     *
     * <b>서비스 지역(ridely.mvp-area)으로 좁히는 것도 쓸 수 없다.</b> 예전에는 그 경계 상자로 걸렀는데, 그것이 <b>요청 검증이 경로 전체를 서비스 지역 안에 가둔다</b>는 전제 위에 있었다. 그 전제가 틀렸다 - 검증은 출발지·도착지 두 점만 보고 그 사이를 잇는 선은 제한하지 않는다. 경계에서 1km 안쪽에서 출발한 10km 순환은 밖으로 나가고, 실제로 나가서 경계 밖 위험 등급 구역을 지났다.
     *
     * 통과 판정(findPassing)에는 그 필터가 없어 <b>피하지 않은 구역을 잡아내는 비대칭</b>이 있었다. 회피를 켰는데 위험·경고 구역이 응답에 나오는 상태였다. 2024년 111건 중 38건이 경계 밖이다 (ADR-011).
     *
     * ⚠️ 구역 수가 늘면 라우팅 엔진이 거부하거나 느려질 수 있다. 서울 최신 연도 111건 전부를 넘겨 동작하는 것은 확인했다. 적재 범위가 넓어지면 이 방식을 다시 봐야 한다.
     *
     * @param dangerLevels 회피할 등급. 비어 있으면 회피하지 않는다
     * @return 합쳐진 MultiPolygon GeoJSON. 대상이 없으면 비어 있다
     */
    public Optional<String> findAvoidGeometry(List<String> dangerLevels) {
        if (dangerLevels == null || dangerLevels.isEmpty()) {
            return Optional.empty();
        }
        /*
         * [핵심 구문]
         *   ST_Union    겹치는 폴리곤을 하나로 녹인다. 라우팅 엔진에 같은 영역을
         *               두 번 넘길 이유가 없다.
         *   ST_Multi    ST_Union 결과가 단일 Polygon으로 나올 수 있다. avoid_polygons는
         *               둘 다 받지만 타입을 하나로 고정해야 호출부가 분기하지 않는다.
         */
        String sql = """
                SELECT ST_AsGeoJSON(ST_Multi(ST_Union(polygon_geom)))
                FROM accident_zone
                WHERE data_year = (SELECT MAX(data_year) FROM accident_zone)
                  AND danger_level = ANY(:dangerLevels)
                """;

        // 집계 함수라 대상이 없어도 행은 하나 나온다. 값이 NULL일 뿐이다
        String geoJson = jdbcClient.sql(sql)
                .param("dangerLevels", dangerLevels.toArray(new String[0]))
                .query(String.class)
                .single();
        return Optional.ofNullable(geoJson);
    }

    /**
     * 경로가 통과하는 사고다발지역을 주행 순서대로 조회한다.
     *
     * 여기서 가리는 것은 "코스가 이 구역을 지나는가"뿐이다. 위험 등급(danger_level)은 적재 때 사고 건수와 사망자 수로 이미 매겨져 있어 그대로 실어 보낸다.
     *
     * 통과 여부는 폴리곤 교차로 본다. 중심점에서 반경 몇 m 식으로 재지 않는 이유는 실측에서 둘이 같은 결과를 주기 때문이다 — 사고다발지에서 자전거도로까지의 거리가 0~44m에 여덟 건 몰려 있고 그다음이 307m라, 그 사이 어떤 값을 잡아도 결과가 같다. 기준이 하나면 경계값을 정할 일도 없다.
     *
     * polygon_geom이 NULL인 건은 ST_Intersects가 NULL을 돌려주어 저절로 빠진다. 적재 실적이 111건 전부 폴리곤을 가지고 있어 중심점 대체 분기는 두지 않았다.
     *
     * ⚠️ data_year 최댓값만 보는 것은 잠정 조치다. 이 컬럼은 사고 시점이 아니라 TAAS가 다발지역을 선정·발표한 분석 연도다. 한 해 안에서 같은 지점에 반복된 사고는 occurrence_count에 이미 합산돼 있으므로 행이 쪼개지지 않는다. 필터를 둔 것은 여러 연도를 적재했을 때 같은 지점이 연도 수만큼 중복되는 것을 막기 위해서다.
     *
     * 다만 최신 연도만 보면 잃는 정보가 있다. 3년 연속 선정된 지점과 올해 처음 오른 지점이 구분되지 않고, 작년 12건에서 올해 3건으로 떨어져 목록에서 빠진 곳은 아예 안 잡힌다. 개선된 곳이면 맞는 동작이지만 그해 우연히 기준 미달이면 놓치는 것이다.
     *
     * 지금 적재된 것이 2024년뿐이라 어느 방식이든 결과가 같다. <b>다년 적재를 시작할 때 이 선택을 다시 판단한다.</b> 후보는 최신 연도만 보기, 전 연도 합집합, 반복 선정 횟수를 위험 신호로 쓰기다.
     *
     * @param routeGeoJson ORS가 돌려준 경로 형상. GeoJSON LineString 문자열
     * @return 통과하는 구역 목록. 없으면 빈 목록
     */
    public List<PassingDangerZoneDTO> findPassing(String routeGeoJson) {
        /*
         * [핵심 구문]
         *   ST_GeomFromGeoJSON  결과 SRID가 0이라 ST_SetSRID로 4326을 명시해야 한다.
         *   ST_Force2D          ORS 형상은 고도를 포함한 3차원이다. 2D 폴리곤과 맞춘다.
         *                       (두 함정 모두 RouteDao에 같은 설명이 있다)
         *   ST_LineLocatePoint  경로 위에서 구역 중심이 어느 지점인지를 0~1로 준다.
         *                       정렬과 진행 거리 양쪽에 쓴다.
         *   ST_Length(::geography)
         *                       경로 실측 길이(m). 진행도에 곱해 km를 낸다. 총거리를
         *                       파라미터로 받지 않는 이유는 이 형상만으로 답이 나오기 때문이다.
         *   data_year 최댓값     아래 주의 참조.
         */
        String sql = """
                WITH route AS (
                    SELECT ST_Force2D(ST_SetSRID(ST_GeomFromGeoJSON(:routeGeoJson), 4326)) AS g
                )
                SELECT
                    a.accident_zone_id,
                    a.spot_name,
                    a.danger_level,
                    a.occurrence_count,
                    a.death_count,
                    ST_Y(a.center_geom) AS lat,
                    ST_X(a.center_geom) AS lng,
                    ST_AsGeoJSON(a.polygon_geom) AS polygon_geo_json,
                    ROUND((ST_LineLocatePoint(route.g, a.center_geom)
                           * ST_Length(route.g::geography) / 1000)::numeric, 1)
                        AS distance_from_start_km
                FROM accident_zone a, route
                WHERE a.data_year = (SELECT MAX(data_year) FROM accident_zone)
                  AND ST_Intersects(route.g, a.polygon_geom)
                ORDER BY ST_LineLocatePoint(route.g, a.center_geom)
                """;

        return jdbcClient.sql(sql)
                .param("routeGeoJson", routeGeoJson)
                .query((rs, rowNum) -> {
                    PassingDangerZoneDTO dto = new PassingDangerZoneDTO();
                    dto.setAccidentZoneId(rs.getLong("accident_zone_id"));
                    dto.setSpotName(rs.getString("spot_name"));
                    dto.setDangerLevel(rs.getString("danger_level"));
                    dto.setOccurrenceCount(rs.getInt("occurrence_count"));
                    dto.setDeathCount(rs.getInt("death_count"));
                    dto.setLat(rs.getDouble("lat"));
                    dto.setLng(rs.getDouble("lng"));
                    dto.setPolygonGeoJson(rs.getString("polygon_geo_json"));
                    dto.setDistanceFromStartKm(rs.getBigDecimal("distance_from_start_km"));
                    return dto;
                })
                .list();
    }
}
