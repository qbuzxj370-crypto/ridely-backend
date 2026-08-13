package kr.ridely.dao;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 따릉이 대여소 DAO.
 *
 * PostGIS 함수(ST_MakePoint)를 사용하므로 MyBatis가 아닌 JdbcClient를 사용한다 (ADR-002).
 */
@Repository
public class BikeStationDao {

    private final JdbcClient jdbcClient;

    public BikeStationDao(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * 대여소를 저장하거나 갱신한다.
     *
     * <p>다른 적재와 달리 {@code DO NOTHING}이 아니라 <b>{@code DO UPDATE}</b>다.
     * 대여소는 1회성 시드가 아니라 <b>운영 중 바뀌는 마스터</b>라서다 —
     * 이전·개명·거치대 증설이 실제로 일어난다. RENT_ID가 고유키라 갱신이 안전하다.
     *
     * @param regionCode 지역 코드(서울=11). region에 없으면 NULL
     * @param rackCount  거치대 수. 값이 없으면 null
     * @return 항상 1 (INSERT 또는 UPDATE)
     */
    public int upsert(String regionCode, String stationCode, String stationName,
                      double lng, double lat, Integer rackCount) {

        /*
         * [핵심 구문]
         *   ST_MakePoint(경도, 위도)  PostGIS는 경도를 먼저 받는다.
         *   ON CONFLICT (station_code) 서울시 RENT_ID 기준. 스키마에 UNIQUE가 이미 있다.
         *   updated_at = NOW()        아래 deactivateStale()의 판정 기준이 된다.
         *                             한 트랜잭션 안에서 NOW()는 트랜잭션 시작 시각으로 고정되므로
         *                             이번 회차에 건드린 행이 모두 같은 값을 갖는다.
         */
        return jdbcClient.sql("""
                        INSERT INTO bike_station (
                            region_id, station_code, station_name, geom, rack_count
                        ) VALUES (
                            (SELECT region_id FROM region WHERE region_code = :regionCode),
                            :stationCode,
                            :stationName,
                            ST_SetSRID(ST_MakePoint(:lng, :lat), 4326),
                            :rackCount
                        )
                        ON CONFLICT (station_code) DO UPDATE SET
                            region_id    = EXCLUDED.region_id,
                            station_name = EXCLUDED.station_name,
                            geom         = EXCLUDED.geom,
                            rack_count   = EXCLUDED.rack_count,
                            is_active    = TRUE,
                            updated_at   = NOW()
                        """)
                .param("regionCode", regionCode)
                .param("stationCode", stationCode)
                .param("stationName", stationName)
                .param("lng", lng)
                .param("lat", lat)
                .param("rackCount", rackCount)
                .update();
    }

    /**
     * 이번 회차에 응답에 없던 대여소를 비활성으로 돌린다.
     *
     * <p>API는 운영 중인 대여소만 돌려준다. 폐쇄된 대여소를 그냥 두면
     * 지도에 없는 대여소가 계속 뜬다. 그렇다고 삭제하면 과거 라이딩 기록의 참조가 끊긴다.
     *
     * <p>판정은 {@code updated_at}으로 한다. 이번 트랜잭션에서 upsert된 행은
     * {@code updated_at = NOW()}(트랜잭션 시작 시각)이고, 응답에 없던 행은 그보다 과거다.
     * 별도 목록을 넘길 필요가 없다.
     *
     * <p>다시 나타나면 upsert가 {@code is_active = TRUE}로 되돌린다.
     *
     * @return 상태가 바뀐 행 수 (활성→비활성, 비활성→활성 모두 포함)
     */
    public int deactivateStale() {
        return jdbcClient.sql("""
                        UPDATE bike_station
                        SET is_active = (updated_at >= NOW())
                        WHERE is_active <> (updated_at >= NOW())
                        """)
                .update();
    }

    public int countAll() {
        return jdbcClient.sql("SELECT COUNT(*) FROM bike_station").query(Integer.class).single();
    }

    public int countActive() {
        return jdbcClient.sql("SELECT COUNT(*) FROM bike_station WHERE is_active")
                .query(Integer.class).single();
    }
}
