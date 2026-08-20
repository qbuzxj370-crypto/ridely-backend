package kr.ridely.dao;

import kr.ridely.dto.route.PassingDangerZoneDTO;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 사고다발지역 공간 조회 DAO.
 *
 * PostGIS 연산자(ST_Intersects·ST_GeomFromGeoJSON)를 사용하므로 JdbcClient를 쓴다 (ADR-002).
 *
 * 적재용 AccidentZoneDao와 나눈 이유는 생명주기가 다르기 때문이다. 저쪽은 배치가 부르고 이쪽은 사용자 요청이 부른다. TourIngestDao와 TourSpatialDao를 나눈 것과 같은 기준이다.
 */
@Repository
public class AccidentZoneSpatialDao {

    private final JdbcClient jdbcClient;

    public AccidentZoneSpatialDao(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
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
