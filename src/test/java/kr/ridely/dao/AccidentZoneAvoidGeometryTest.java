package kr.ridely.dao;

import kr.ridely.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 사고다발지 회피 도형 조회 통합 테스트.
 *
 * <b>이 클래스의 존재 이유는 첫 번째 테스트다.</b> 회피 도형을 뽑을 때 서비스 지역(ridely.mvp-area) 경계 상자로 대상을 걸렀던 적이 있다. 그것은 요청 검증이 경로를 서비스 지역 안에 가둔다는 전제 위에 있었는데, 검증은 출발지·도착지 두 점만 본다. 경로는 밖으로 나가고 그쪽 사고다발지는 회피 대상에서 빠진 채 통과 판정에만 잡혔다 - 회피를 켰는데 위험 등급 구역이 응답에 나왔다.
 *
 * 필터는 SQL 한 줄이라 되살리기 쉽고 되살려도 컴파일은 통과한다. 그래서 테스트로 고정한다 (ADR-011).
 */
class AccidentZoneAvoidGeometryTest extends AbstractIntegrationTest {

    /** application.yml의 ridely.mvp-area 경계. 이 테스트가 검증하는 대상이라 값을 직접 적는다 */
    private static final double MVP_MAX_LNG = 127.130;

    /** 경계 안쪽. 잠실 부근 */
    private static final double 안쪽_경도 = 127.080;
    private static final double 안쪽_위도 = 37.518;

    /** 경계 바깥. 강동구 길동 부근 - 실제로 이 위치에 WARNING 등급 구역이 있다 */
    private static final double 바깥_경도 = 127.139;
    private static final double 바깥_위도 = 37.534;

    @Autowired
    private AccidentZoneSpatialDao accidentZoneSpatialDao;

    @Autowired
    private JdbcClient jdbcClient;

    /** 마이그레이션이 심는 서울 region. accident_zone.region_id가 NOT NULL FK다 */
    private long 서울_지역번호;

    @BeforeEach
    void 데이터_준비() {
        jdbcClient.sql("TRUNCATE TABLE accident_zone RESTART IDENTITY").update();

        서울_지역번호 = jdbcClient.sql("SELECT region_id FROM region WHERE region_code = '11'")
                .query(Long.class)
                .single();

        insert("경계 안쪽 위험구역", "DANGER", 안쪽_경도, 안쪽_위도);
        insert("경계 바깥 경고구역", "WARNING", 바깥_경도, 바깥_위도);
        insert("경계 안쪽 주의구역", "CAUTION", 127.070, 37.520);
    }

    /**
     * 사고다발지 한 건을 심는다.
     *
     * 폴리곤은 중심점 주변 약 100m 사각형이다. 실제 데이터의 형태를 흉내 낼 필요는 없고 회피 도형에 포함되는지만 보면 된다.
     */
    private void insert(String spotName, String dangerLevel, double lng, double lat) {
        jdbcClient.sql("""
                        INSERT INTO accident_zone (
                            region_id, afos_fid, data_year, spot_name, danger_level,
                            occurrence_count, death_count, center_geom, polygon_geom
                        ) VALUES (
                            :regionId, :afosFid, 2024, :spotName, :dangerLevel,
                            10, 0,
                            ST_SetSRID(ST_MakePoint(:lng, :lat), 4326),
                            ST_SetSRID(ST_MakeEnvelope(
                                :lng - 0.0005, :lat - 0.0005,
                                :lng + 0.0005, :lat + 0.0005), 4326)
                        )
                        """)
                .param("regionId", 서울_지역번호)
                .param("afosFid", spotName)
                .param("spotName", spotName)
                .param("dangerLevel", dangerLevel)
                .param("lng", lng)
                .param("lat", lat)
                .update();
    }

    /** 도형이 특정 좌표를 덮는지. 회피 대상에 들었는지를 이걸로 판정한다 */
    private boolean 덮는가(String geoJson, double lng, double lat) {
        return Boolean.TRUE.equals(jdbcClient.sql("""
                        SELECT ST_Intersects(
                            ST_GeomFromGeoJSON(:geoJson),
                            ST_SetSRID(ST_MakePoint(:lng, :lat), 4326))
                        """)
                .param("geoJson", geoJson)
                .param("lng", lng)
                .param("lat", lat)
                .query(Boolean.class)
                .single());
    }

    @Test
    @DisplayName("서비스 지역 경계 밖 사고다발지도 회피 대상에 든다")
    void 경계_밖_사고다발지도_회피_대상이다() {
        String geoJson = accidentZoneSpatialDao.findAvoidGeometry(List.of("DANGER", "WARNING"))
                .orElseThrow(() -> new AssertionError("회피 도형이 비어 있다"));

        // 경계 상자 필터가 살아 있으면 이 단언이 깨진다.
        // 바깥 좌표가 MVP 경계를 실제로 넘는지부터 확인해 테스트 자체가 무의미해지지 않게 한다
        assertThat(바깥_경도).isGreaterThan(MVP_MAX_LNG);
        assertThat(덮는가(geoJson, 바깥_경도, 바깥_위도)).isTrue();

        assertThat(덮는가(geoJson, 안쪽_경도, 안쪽_위도)).isTrue();
    }

    @Test
    @DisplayName("회피 대상 등급만 도형에 든다")
    void 등급으로_거른다() {
        String geoJson = accidentZoneSpatialDao.findAvoidGeometry(List.of("DANGER"))
                .orElseThrow(() -> new AssertionError("회피 도형이 비어 있다"));

        assertThat(덮는가(geoJson, 안쪽_경도, 안쪽_위도)).isTrue();
        assertThat(덮는가(geoJson, 바깥_경도, 바깥_위도)).isFalse();   // WARNING이라 빠진다
        assertThat(덮는가(geoJson, 127.070, 37.520)).isFalse();      // CAUTION이라 빠진다
    }

    @Test
    @DisplayName("등급을 지정하지 않으면 회피하지 않는다")
    void 등급이_비면_도형이_없다() {
        assertThat(accidentZoneSpatialDao.findAvoidGeometry(List.of())).isEmpty();
        assertThat(accidentZoneSpatialDao.findAvoidGeometry(null)).isEmpty();
    }
}
