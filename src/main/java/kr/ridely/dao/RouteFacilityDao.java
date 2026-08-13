package kr.ridely.dao;

import kr.ridely.infra.seed.RouteFacilityCsvReader.Facility;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 자전거길 주변시설 DAO.
 *
 * PostGIS 함수(ST_MakePoint·ST_DWithin)를 사용하므로 MyBatis가 아닌 JdbcClient를 사용한다 (ADR-002).
 */
@Repository
public class RouteFacilityDao {

    private final JdbcClient jdbcClient;

    public RouteFacilityDao(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * 시설을 저장한다. 이미 있는 건 건너뛴다.
     *
     * <p>한 건씩 넣는 이유: 1천여 건짜리 1회성 시드라 배치가 필요할 규모가 아니고,
     * 한 트랜잭션 안에서 도는 로컬 왕복이라 체감 차이가 없다.
     * 대신 코드가 단순해지고 ADR-002의 JdbcClient 사용 원칙을 그대로 지킨다.
     *
     * @return 실제로 INSERT된 건수. 중복이라 건너뛴 건은 세지 않는다
     */
    public int insertIgnoringDuplicates(List<Facility> facilities) {
        int inserted = 0;
        for (Facility facility : facilities) {
            inserted += insertIgnoringDuplicate(
                    facility.getFacilityType(), facility.getName(),
                    facility.getLng(), facility.getLat());
        }
        return inserted;
    }

    /**
     * 시설 한 건을 저장한다. 이미 있으면 건너뛴다.
     *
     * <p>파일이 아닌 소스(서울시 자전거 편의시설 API의 AIR_PUMP)도 이 테이블을 쓰므로
     * 리더의 DTO에 묶이지 않는 단건 진입점을 열어 둔다. 자연키가 같으므로
     * 소스가 섞여도 중복이 쌓이지 않는다.
     *
     * @return 1이면 저장, 0이면 이미 있어 건너뜀
     */
    public int insertIgnoringDuplicate(String facilityType, String facilityName,
                                       double lng, double lat) {

        /*
         * [핵심 구문]
         *   ST_MakePoint(경도, 위도)   PostGIS는 경도를 먼저 받는다. 순서가 바뀌면
         *                              조용히 엉뚱한 위치가 된다(원본 CSV는 경도가 3열).
         *   ST_SetSRID(..., 4326)      좌표계를 WGS84로 명시. 빼면 SRID 0이 되어
         *                              geom 컬럼 제약(4326)에 걸린다.
         *   ON CONFLICT DO NOTHING     uq_facility_natural(종류·이름·좌표) 기준 멱등.
         *                              이 테이블은 CSV와 서울시 API가 함께 쓰므로
         *                              DELETE 후 INSERT를 쓸 수 없다. 상세는 V1 스키마 주석
         */
        return jdbcClient.sql("""
                        INSERT INTO route_facility (facility_type, facility_name, geom)
                        VALUES (
                            :facilityType,
                            :facilityName,
                            ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)
                        )
                        ON CONFLICT DO NOTHING
                        """)
                .param("facilityType", facilityType)
                .param("facilityName", facilityName)
                .param("lng", lng)
                .param("lat", lat)
                .update();
    }

    /**
     * 시설을 가장 가까운 국토종주 노선에 연결한다.
     *
     * <p>원본 CSV에 노선 코드가 없어 좌표로 추정할 수밖에 없다.
     * 반경 안에 노선이 없으면 NULL로 남긴다 — FK가 nullable인 이유다.
     *
     * <p>노선 형상이 먼저 적재돼 있어야 한다. 비어 있으면 전부 NULL이 된다.
     *
     * @param maxDistanceM 이 거리 안의 노선만 후보로 본다 (m)
     * @return 갱신된 행 수 (연결 실패해 NULL이 된 행도 포함)
     */
    public int linkToNearestRoute(int maxDistanceM) {

        /*
         * [핵심 구문]
         *   상관 서브쿼리        UPDATE ... SET x = (SELECT ...) 형태.
         *                        LATERAL로 쓰면 갱신 대상 테이블을 FROM에서 참조하지 못한다.
         *   ::geography          미터 단위 판정. geometry끼리 비교하면 단위가 "도"라 의미가 없다.
         *   ST_DWithin           반경 필터. GIST 인덱스를 탄다.
         *   <->                  거리순 정렬(KNN). 반경 안에서 가장 가까운 노선 하나를 고른다.
         */
        String sql = """
                UPDATE route_facility f
                SET national_bike_route_id = (
                    SELECT r.national_bike_route_id
                    FROM national_bike_route r
                    WHERE ST_DWithin(r.line_geom::geography, f.geom::geography, :maxDistanceM)
                    ORDER BY r.line_geom::geography <-> f.geom::geography
                    LIMIT 1
                )
                """;

        return jdbcClient.sql(sql).param("maxDistanceM", maxDistanceM).update();
    }

    /** 노선에 연결된 시설 수. 좌표 매칭이 얼마나 먹혔는지 보는 지표 */
    public int countLinked() {
        return jdbcClient.sql("SELECT COUNT(*) FROM route_facility WHERE national_bike_route_id IS NOT NULL")
                .query(Integer.class)
                .single();
    }

    /** 종류별 적재 건수. 응답에 실어 눈으로 대조한다 */
    public Map<String, Integer> countByType() {
        // RowMapper 대신 ResultSetExtractor를 쓴다. 행 하나를 객체 하나로 옮기는 게 아니라
        // 결과 전체를 맵 하나로 접는 작업이라 의미가 맞다
        return jdbcClient.sql("""
                        SELECT facility_type, COUNT(*) AS cnt
                        FROM route_facility
                        GROUP BY facility_type
                        ORDER BY cnt DESC
                        """)
                .query(rs -> {
                    Map<String, Integer> counts = new LinkedHashMap<>();
                    while (rs.next()) {
                        counts.put(rs.getString("facility_type"), rs.getInt("cnt"));
                    }
                    return counts;
                });
    }
}
