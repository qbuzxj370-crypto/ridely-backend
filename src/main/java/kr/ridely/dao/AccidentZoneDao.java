package kr.ridely.dao;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 자전거 사고다발지역 DAO.
 *
 * PostGIS 함수(ST_GeomFromGeoJSON·ST_Multi)를 사용하므로 JdbcClient를 사용한다 (ADR-002).
 */
@Repository
public class AccidentZoneDao {

    private final JdbcClient jdbcClient;

    public AccidentZoneDao(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * 사고다발지역을 저장하거나 갱신한다.
     *
     * @param regionCode  법정동코드 앞 2자리(서울=11). region에 없으면 NOT NULL 위반이므로
     *                    호출 전에 서비스가 걸러야 한다
     * @param geoJson     폴리곤 GeoJSON 문자열. null이면 polygon_geom은 NULL
     * @param dangerLevel CAUTION / WARNING / DANGER (서비스에서 파생)
     * @return 항상 1 (INSERT 또는 UPDATE)
     */
    public int upsert(String regionCode, String afosFid, String bjdCode, String spotCode,
                      String sidoSggName, String spotName,
                      int occurrenceCount, int casualtyCount, int deathCount,
                      int seriousInjCount, int minorInjCount, int reportInjCount,
                      String dangerLevel, int dataYear,
                      double lng, double lat, String geoJson) {

        /*
         * [핵심 구문]
         *   ST_GeomFromGeoJSON  API가 GeoJSON 문자열을 그대로 주므로 파싱 없이 넣는다.
         *   ST_SetSRID(..,4326) GeoJSON에 crs가 없으면 SRID 0이 된다. 명시하지 않으면
         *                       컬럼 제약(4326)에 걸린다.
         *   ST_Multi            명세는 Polygon만 예시하지만 실제로 MultiPolygon이 오는
         *                       연도가 있다. 감싸서 타입을 하나로 통일한다 (V3).
         *   ON CONFLICT (afos_fid, data_year)
         *                       uk_accident_fid_year 기준 멱등. 갱신주기가 연 1회라
         *                       같은 연도를 다시 받아도 행이 늘지 않는다.
         *                       DO UPDATE인 이유: danger_level 파생 규칙을 바꾸면
         *                       재적재로 반영돼야 한다.
         */
        return jdbcClient.sql("""
                        INSERT INTO accident_zone (
                            region_id, afos_fid, bjd_code, spot_code, sido_sgg_name, spot_name,
                            occurrence_count, casualty_count, death_count,
                            serious_inj_count, minor_inj_count, report_inj_count,
                            danger_level, data_year, center_geom, polygon_geom
                        ) VALUES (
                            (SELECT region_id FROM region WHERE region_code = :regionCode),
                            :afosFid, :bjdCode, :spotCode, :sidoSggName, :spotName,
                            :occurrenceCount, :casualtyCount, :deathCount,
                            :seriousInjCount, :minorInjCount, :reportInjCount,
                            :dangerLevel, :dataYear,
                            ST_SetSRID(ST_MakePoint(:lng, :lat), 4326),
                            CASE WHEN :geoJson IS NULL THEN NULL
                                 ELSE ST_Multi(ST_SetSRID(ST_GeomFromGeoJSON(:geoJson), 4326))
                            END
                        )
                        ON CONFLICT (afos_fid, data_year) DO UPDATE SET
                            region_id         = EXCLUDED.region_id,
                            bjd_code          = EXCLUDED.bjd_code,
                            spot_code         = EXCLUDED.spot_code,
                            sido_sgg_name     = EXCLUDED.sido_sgg_name,
                            spot_name         = EXCLUDED.spot_name,
                            occurrence_count  = EXCLUDED.occurrence_count,
                            casualty_count    = EXCLUDED.casualty_count,
                            death_count       = EXCLUDED.death_count,
                            serious_inj_count = EXCLUDED.serious_inj_count,
                            minor_inj_count   = EXCLUDED.minor_inj_count,
                            report_inj_count  = EXCLUDED.report_inj_count,
                            danger_level      = EXCLUDED.danger_level,
                            center_geom       = EXCLUDED.center_geom,
                            polygon_geom      = EXCLUDED.polygon_geom
                        """)
                .param("regionCode", regionCode)
                .param("afosFid", afosFid)
                .param("bjdCode", bjdCode)
                .param("spotCode", spotCode)
                .param("sidoSggName", sidoSggName)
                .param("spotName", spotName)
                .param("occurrenceCount", occurrenceCount)
                .param("casualtyCount", casualtyCount)
                .param("deathCount", deathCount)
                .param("seriousInjCount", seriousInjCount)
                .param("minorInjCount", minorInjCount)
                .param("reportInjCount", reportInjCount)
                .param("dangerLevel", dangerLevel)
                .param("dataYear", dataYear)
                .param("lng", lng)
                .param("lat", lat)
                .param("geoJson", geoJson)
                .update();
    }

    public int countAll() {
        return jdbcClient.sql("SELECT COUNT(*) FROM accident_zone").query(Integer.class).single();
    }

    /** 등급별 건수 */
    public Map<String, Integer> countByDangerLevel() {
        return jdbcClient.sql("""
                        SELECT danger_level, COUNT(*) AS cnt
                        FROM accident_zone
                        GROUP BY danger_level
                        ORDER BY cnt DESC
                        """)
                .query(rs -> {
                    Map<String, Integer> counts = new LinkedHashMap<>();
                    while (rs.next()) {
                        counts.put(rs.getString("danger_level"), rs.getInt("cnt"));
                    }
                    return counts;
                });
    }

    /**
     * MVP 구간(한강 서울) 안에 든 건수.
     *
     * 사고다발지는 도심 교차로에 생기고 한강 자전거도로에는 거의 없다.
     * 회피·근접 알림 기능이 실제로 보일지를 가늠하는 지표라 응답에 실어 둔다.
     */
    public int countInMvpArea(double minLng, double maxLng, double minLat, double maxLat) {
        return jdbcClient.sql("""
                        SELECT COUNT(*) FROM accident_zone
                        WHERE ST_Intersects(
                            center_geom,
                            ST_MakeEnvelope(:minLng, :minLat, :maxLng, :maxLat, 4326))
                        """)
                .param("minLng", minLng)
                .param("maxLng", maxLng)
                .param("minLat", minLat)
                .param("maxLat", maxLat)
                .query(Integer.class)
                .single();
    }

    /** 폴리곤이 MultiPolygon으로 통일됐는지 확인용 */
    public Map<String, Integer> countByGeometryType() {
        return jdbcClient.sql("""
                        SELECT COALESCE(ST_GeometryType(polygon_geom), '(없음)') AS geom_type,
                               COUNT(*) AS cnt
                        FROM accident_zone
                        GROUP BY geom_type
                        """)
                .query(rs -> {
                    Map<String, Integer> counts = new LinkedHashMap<>();
                    while (rs.next()) {
                        counts.put(rs.getString("geom_type"), rs.getInt("cnt"));
                    }
                    return counts;
                });
    }
}
