package kr.ridely.dao;

import kr.ridely.dto.poi.PoiItemDTO;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 자전거 인프라 POI 공간 조회 DAO.
 *
 * PostGIS 연산자(ST_MakeLine·ST_DWithin·ST_Distance)를 사용하므로 JdbcClient를 쓴다 (ADR-002).
 *
 * 용도는 코스 추천의 후보 수집이다. TourSpatialDao가 "한 점 주변"을 보는 것과 달리
 * 여기서는 출발지~도착지를 잇는 축(corridor) 주변을 본다. 라이딩 경로는 선이라
 * 출발점 반경만 보면 도착지 쪽 시설이 통째로 빠진다.
 *
 * 반환은 PoiItemDTO 하나로 통일한다. 테이블이 달라도 LLM 입력에서는
 * "이름·좌표·거리"만 쓰이고, 타입별 필드는 코멘트 생성에 곁들이는 정도다.
 */
@Repository
public class PoiSpatialDao {

    /** PoiItemDTO.type 값. 프론트가 이 값으로 마커를 나눈다 */
    private static final String TYPE_ROUTE_FACILITY = "ROUTE_FACILITY";
    private static final String TYPE_REPAIR_SHOP = "REPAIR_SHOP";
    private static final String TYPE_BIKE_STATION = "BIKE_STATION";

    /*
     * [축 주변 조회의 공통 부분]
     *
     * 도착지가 없으면(순환 코스) 출발점 하나를, 있으면 두 점을 이은 선을 기준 도형으로 삼는다.
     * CASE의 두 갈래가 Point와 LineString으로 달라 보이지만 PostGIS에서는 둘 다 geometry라
     * 타입이 맞는다.
     *
     * :hasEnd를 따로 받는 이유 — :endLng를 NULL로 넘기면 PostgreSQL이 파라미터 타입을
     * 추론하지 못해 "could not determine data type of parameter"로 깨진다.
     * 그래서 도착지가 없을 때 Java가 출발지 좌표를 채워 넣고, 사용 여부는 이 플래그로 가른다.
     *
     * geom::geography  — 미터 단위로 다루기 위한 캐스팅. 빼면 :corridorM이 도(degree)로 해석된다.
     * ST_DWithin       — GIST 인덱스를 타는 형태다. ST_Distance(...) < R 보다 빠르다.
     */
    private static final String CORRIDOR_CTE = """
            WITH corridor AS (
                SELECT (CASE
                          WHEN :hasEnd THEN ST_MakeLine(
                                   ST_SetSRID(ST_MakePoint(:startLng, :startLat), 4326),
                                   ST_SetSRID(ST_MakePoint(:endLng,   :endLat),   4326))
                          ELSE ST_SetSRID(ST_MakePoint(:startLng, :startLat), 4326)
                        END)::geography AS g
            )
            """;

    private final JdbcClient jdbcClient;

    public PoiSpatialDao(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * 자전거길 주변시설을 축 주변에서 가까운 순으로 조회한다.
     *
     * @param facilityTypes 조회할 종류. WATER(급수대)·TOILET(화장실)·CERT_CENTER(인증센터)·AIR_PUMP(공기주입기)
     * @see #findRepairShops 파라미터 설명은 이쪽 javadoc 참조
     */
    public List<PoiItemDTO> findRouteFacilities(double startLng, double startLat,
                                                Double endLng, Double endLat,
                                                int corridorM, List<String> facilityTypes, int limit) {
        String sql = CORRIDOR_CTE + """
                SELECT
                    f.route_facility_id AS id,
                    f.facility_name     AS name,
                    f.facility_type,
                    ST_Y(f.geom) AS lat,
                    ST_X(f.geom) AS lng,
                    ROUND(ST_Distance(f.geom::geography, c.g))::int AS distance_m
                FROM route_facility f, corridor c
                WHERE ST_DWithin(f.geom::geography, c.g, :corridorM)
                  AND f.facility_type = ANY(:facilityTypes)
                ORDER BY distance_m
                LIMIT :limit
                """;

        return corridorQuery(sql, startLng, startLat, endLng, endLat, corridorM, limit)
                .param("facilityTypes", facilityTypes.toArray(new String[0]))
                .query((rs, rowNum) -> {
                    PoiItemDTO dto = base(TYPE_ROUTE_FACILITY, rs.getLong("id"),
                            rs.getString("name"), rs.getDouble("lat"), rs.getDouble("lng"),
                            rs.getInt("distance_m"));
                    dto.setFacilityType(rs.getString("facility_type"));
                    return dto;
                })
                .list();
    }

    /**
     * 자전거 수리센터를 축 주변에서 가까운 순으로 조회한다.
     *
     * @param startLng   출발지 경도
     * @param startLat   출발지 위도
     * @param endLng     도착지 경도. null이면 순환 코스로 보고 출발점 반경만 본다
     * @param endLat     도착지 위도. null이면 위와 같다
     * @param corridorM  축에서 이 거리 안까지 후보로 본다 (m)
     * @param limit      최대 반환 건수
     */
    public List<PoiItemDTO> findRepairShops(double startLng, double startLat,
                                            Double endLng, Double endLat,
                                            int corridorM, int limit) {
        String sql = CORRIDOR_CTE + """
                SELECT
                    s.repair_shop_id AS id,
                    s.shop_name      AS name,
                    s.addr,
                    s.tel,
                    s.is_free,
                    s.operating_hours,
                    ST_Y(s.geom) AS lat,
                    ST_X(s.geom) AS lng,
                    ROUND(ST_Distance(s.geom::geography, c.g))::int AS distance_m
                FROM repair_shop s, corridor c
                WHERE ST_DWithin(s.geom::geography, c.g, :corridorM)
                ORDER BY distance_m
                LIMIT :limit
                """;

        return corridorQuery(sql, startLng, startLat, endLng, endLat, corridorM, limit)
                .query((rs, rowNum) -> {
                    PoiItemDTO dto = base(TYPE_REPAIR_SHOP, rs.getLong("id"),
                            rs.getString("name"), rs.getDouble("lat"), rs.getDouble("lng"),
                            rs.getInt("distance_m"));
                    dto.setAddr(rs.getString("addr"));
                    dto.setTel(rs.getString("tel"));
                    dto.setIsFree(rs.getBoolean("is_free"));
                    dto.setOperatingHours(rs.getString("operating_hours"));
                    return dto;
                })
                .list();
    }

    /**
     * 따릉이 대여소를 축 주변에서 가까운 순으로 조회한다.
     *
     * 폐쇄된 대여소(is_active = FALSE)는 제외한다. 적재 시 응답에 없던 대여소를
     * 지우지 않고 비활성으로 돌리므로(BikeStationDao.deactivateStale) 여기서 걸러야 한다.
     */
    public List<PoiItemDTO> findBikeStations(double startLng, double startLat,
                                             Double endLng, Double endLat,
                                             int corridorM, int limit) {
        String sql = CORRIDOR_CTE + """
                SELECT
                    b.bike_station_id AS id,
                    b.station_name    AS name,
                    b.rack_count,
                    b.is_active,
                    ST_Y(b.geom) AS lat,
                    ST_X(b.geom) AS lng,
                    ROUND(ST_Distance(b.geom::geography, c.g))::int AS distance_m
                FROM bike_station b, corridor c
                WHERE ST_DWithin(b.geom::geography, c.g, :corridorM)
                  AND b.is_active
                ORDER BY distance_m
                LIMIT :limit
                """;

        return corridorQuery(sql, startLng, startLat, endLng, endLat, corridorM, limit)
                .query((rs, rowNum) -> {
                    PoiItemDTO dto = base(TYPE_BIKE_STATION, rs.getLong("id"),
                            rs.getString("name"), rs.getDouble("lat"), rs.getDouble("lng"),
                            rs.getInt("distance_m"));
                    dto.setRackCount((Integer) rs.getObject("rack_count"));
                    dto.setIsActive(rs.getBoolean("is_active"));
                    return dto;
                })
                .list();
    }

    /** 축 파라미터를 한곳에서 묶는다. 도착지가 없으면 출발지 좌표로 채우고 hasEnd로 가른다 */
    private JdbcClient.StatementSpec corridorQuery(String sql,
                                                   double startLng, double startLat,
                                                   Double endLng, Double endLat,
                                                   int corridorM, int limit) {
        boolean hasEnd = endLng != null && endLat != null;
        return jdbcClient.sql(sql)
                .param("startLng", startLng)
                .param("startLat", startLat)
                .param("endLng", hasEnd ? endLng : startLng)
                .param("endLat", hasEnd ? endLat : startLat)
                .param("hasEnd", hasEnd)
                .param("corridorM", corridorM)
                .param("limit", limit);
    }

    private PoiItemDTO base(String type, long id, String name, double lat, double lng, int distanceM) {
        return new PoiItemDTO(type, id, name, lat, lng, distanceM);
    }
}
