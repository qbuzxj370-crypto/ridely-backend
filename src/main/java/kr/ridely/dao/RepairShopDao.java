package kr.ridely.dao;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 자전거 수리센터 DAO.
 *
 * PostGIS 함수(ST_MakePoint)를 사용하므로 MyBatis가 아닌 JdbcClient를 사용한다 (ADR-002).
 */
@Repository
public class RepairShopDao {

    private final JdbcClient jdbcClient;

    public RepairShopDao(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * 수리센터 한 건을 저장한다. 이미 있으면 건너뛴다.
     *
     * @param regionCode 지역 코드(서울=11). region에 없으면 region_id는 NULL로 남는다
     * @return 1이면 저장, 0이면 이미 있어 건너뜀
     */
    public int insertIgnoringDuplicate(String regionCode, String shopName,
                                       double lng, double lat, String addr,
                                       boolean isFree, String operatingHours) {

        /*
         * [핵심 구문]
         *   region_id 서브쿼리   좌표 역매핑 대신 코드 조회로 채운다. region 시드가
         *                        시도 3건(11 서울·28 인천·41 경기)뿐이라 이걸로 충분하다.
         *                        없으면 NULL — 컬럼이 nullable이다.
         *   ST_MakePoint(경도, 위도)  PostGIS는 경도를 먼저 받는다.
         *   ON CONFLICT DO NOTHING    uq_repair_shop_natural(이름·좌표) 기준 멱등 (V2).
         *                             소스에 신뢰할 만한 고유키가 없어 자연키를 쓴다.
         */
        return jdbcClient.sql("""
                        INSERT INTO repair_shop (
                            region_id, shop_name, geom, addr, is_free, operating_hours
                        ) VALUES (
                            (SELECT region_id FROM region WHERE region_code = :regionCode),
                            :shopName,
                            ST_SetSRID(ST_MakePoint(:lng, :lat), 4326),
                            :addr,
                            :isFree,
                            :operatingHours
                        )
                        ON CONFLICT DO NOTHING
                        """)
                .param("regionCode", regionCode)
                .param("shopName", shopName)
                .param("lng", lng)
                .param("lat", lat)
                .param("addr", addr)
                .param("isFree", isFree)
                .param("operatingHours", operatingHours)
                .update();
    }

    /** 전체 건수 */
    public int countAll() {
        return jdbcClient.sql("SELECT COUNT(*) FROM repair_shop").query(Integer.class).single();
    }

    /** 무료 여부별 건수. 파생 규칙(수리범위에 "무상" 포함)이 먹혔는지 눈으로 본다 */
    public Map<String, Integer> countByFree() {
        return jdbcClient.sql("""
                        SELECT is_free, COUNT(*) AS cnt
                        FROM repair_shop
                        GROUP BY is_free
                        ORDER BY is_free DESC
                        """)
                .query(rs -> {
                    Map<String, Integer> counts = new LinkedHashMap<>();
                    while (rs.next()) {
                        counts.put(rs.getBoolean("is_free") ? "무료" : "유료·미상", rs.getInt("cnt"));
                    }
                    return counts;
                });
    }
}
