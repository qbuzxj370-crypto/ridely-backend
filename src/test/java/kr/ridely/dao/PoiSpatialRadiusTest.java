package kr.ridely.dao;

import kr.ridely.dto.poi.PoiItemDTO;
import kr.ridely.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 인프라 POI 반경 조회 DAO 통합 테스트.
 *
 * <b>단위 테스트가 못 보는 것을 본다.</b> {@code PoiNearbySelectionTest}는 DAO를 익명 서브클래스로 흉내 내는데, 그러면 {@code ST_DWithin}의 반경 필터와 {@code ST_Distance}의 거리 계산이 통째로 사라진다. 종류를 고르고 합치는 것만 확인될 뿐이다.
 *
 * <pre>
 * ST_DWithin      반경 밖을 잘라낸다
 * ST_Distance     미터 단위로 잰다 (geography 캐스팅이 빠지면 도 단위가 된다)
 * ORDER BY        가까운 순. 축 조회의 PICK_BOTH_ENDS와 반대다
 * is_active       폐쇄된 대여소를 뺀다
 * </pre>
 *
 * 여의도한강공원을 중심으로 거리가 다른 지점을 심는다.
 */
class PoiSpatialRadiusTest extends AbstractIntegrationTest {

    private static final double 중심_경도 = 126.9339;
    private static final double 중심_위도 = 37.5265;

    /** 중심에서 약 1.1km 동쪽 */
    private static final double 중간_경도 = 126.9465;

    /** 중심에서 약 5.4km 동쪽 */
    private static final double 먼_경도 = 126.9950;

    @Autowired
    private PoiSpatialDao poiSpatialDao;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void 데이터_준비() {
        jdbcClient.sql("TRUNCATE TABLE route_facility RESTART IDENTITY CASCADE").update();
        jdbcClient.sql("TRUNCATE TABLE repair_shop RESTART IDENTITY CASCADE").update();
        jdbcClient.sql("TRUNCATE TABLE bike_station RESTART IDENTITY CASCADE").update();
    }

    @Test
    @DisplayName("반경 안을 가까운 순으로 돌려준다")
    void 거리순_정렬() {
        시설("WATER", "가까운 급수대", 중심_경도, 중심_위도);
        시설("WATER", "중간 급수대", 중간_경도, 중심_위도);
        시설("TOILET", "중간 화장실", 중간_경도, 중심_위도);

        List<PoiItemDTO> result = poiSpatialDao.findRouteFacilitiesInRadius(
                중심_경도, 중심_위도, 2000, List.of("WATER", "TOILET"), 100);

        assertThat(result).extracting(PoiItemDTO::getName).startsWith("가까운 급수대");
        assertThat(result).extracting(PoiItemDTO::getDistanceM).isSorted();
    }

    @Test
    @DisplayName("반경 밖은 잘라낸다")
    void 반경_필터() {
        시설("WATER", "가까운 급수대", 중심_경도, 중심_위도);
        시설("WATER", "먼 급수대", 먼_경도, 중심_위도);

        // 먼 급수대는 약 5.4km 지점이라 2km에서는 빠지고 10km에서는 들어온다
        List<PoiItemDTO> 좁게 = poiSpatialDao.findRouteFacilitiesInRadius(
                중심_경도, 중심_위도, 2000, List.of("WATER"), 100);
        List<PoiItemDTO> 넓게 = poiSpatialDao.findRouteFacilitiesInRadius(
                중심_경도, 중심_위도, 10000, List.of("WATER"), 100);

        assertThat(좁게).extracting(PoiItemDTO::getName).containsExactly("가까운 급수대");
        assertThat(넓게).extracting(PoiItemDTO::getName)
                .containsExactly("가까운 급수대", "먼 급수대");
    }

    @Test
    @DisplayName("거리를 미터로 잰다")
    void 미터_단위() {
        // geography 캐스팅이 빠지면 도(degree) 단위가 된다. 그때 반경 필터는
        // 1000을 1000도로 읽어 지구 전체를 통과시키므로 위 반경_필터 테스트가 먼저 깨진다.
        // 다만 그쪽은 "먼 급수대가 왜 들어왔나"로 실패해 원인이 안 보인다.
        // 거리 값을 직접 보면 0.01 같은 숫자가 나와 단위 문제임이 바로 드러난다
        시설("WATER", "중간 급수대", 중간_경도, 중심_위도);

        List<PoiItemDTO> result = poiSpatialDao.findRouteFacilitiesInRadius(
                중심_경도, 중심_위도, 5000, List.of("WATER"), 100);

        assertThat(result.getFirst().getDistanceM()).isBetween(900, 1300);
    }

    @Test
    @DisplayName("요청하지 않은 시설 종류는 빠진다")
    void 종류_필터() {
        시설("WATER", "급수대", 중심_경도, 중심_위도);
        시설("AIR_PUMP", "공기주입기", 중심_경도, 중심_위도);

        List<PoiItemDTO> result = poiSpatialDao.findRouteFacilitiesInRadius(
                중심_경도, 중심_위도, 2000, List.of("WATER"), 100);

        assertThat(result).extracting(PoiItemDTO::getFacilityType).containsExactly("WATER");
    }

    @Test
    @DisplayName("상한을 넘으면 가까운 것부터 자른다")
    void 건수_상한() {
        시설("WATER", "1번", 중심_경도, 중심_위도);
        시설("WATER", "2번", 중간_경도, 중심_위도);
        시설("WATER", "3번", 먼_경도, 중심_위도);

        List<PoiItemDTO> result = poiSpatialDao.findRouteFacilitiesInRadius(
                중심_경도, 중심_위도, 10000, List.of("WATER"), 2);

        assertThat(result).extracting(PoiItemDTO::getName).containsExactly("1번", "2번");
    }

    @Test
    @DisplayName("폐쇄된 대여소는 빠진다")
    void 비활성_대여소_제외() {
        // 적재 시 응답에 없던 대여소를 지우지 않고 비활성으로 돌린다(BikeStationDao.deactivateStale).
        // 여기서 거르지 않으면 지도에 없어진 대여소가 찍힌다
        대여소("ST-001", "운영 중", 중심_경도, 중심_위도, true);
        대여소("ST-002", "폐쇄됨", 중심_경도, 중심_위도, false);

        List<PoiItemDTO> result =
                poiSpatialDao.findBikeStationsInRadius(중심_경도, 중심_위도, 2000, 100);

        assertThat(result).extracting(PoiItemDTO::getName).containsExactly("운영 중");
    }

    @Test
    @DisplayName("수리소의 타입별 필드가 채워진다")
    void 수리소_필드() {
        수리소("무료 수리센터", 중심_경도, 중심_위도, true, "평일 09:00~18:00", "02-000-0000");

        PoiItemDTO item = poiSpatialDao
                .findRepairShopsInRadius(중심_경도, 중심_위도, 2000, 100)
                .getFirst();

        assertThat(item.getType()).isEqualTo("REPAIR_SHOP");
        assertThat(item.getIsFree()).isTrue();
        assertThat(item.getOperatingHours()).isEqualTo("평일 09:00~18:00");
        assertThat(item.getTel()).isEqualTo("02-000-0000");
    }

    @Test
    @DisplayName("반경 안에 아무것도 없으면 빈 목록이다")
    void 결과_없음() {
        // 예외가 아니라 빈 목록이어야 한다. 서비스가 이 값을 그대로 200에 싣는다
        List<PoiItemDTO> result = poiSpatialDao.findRepairShopsInRadius(
                중심_경도, 중심_위도, 1000, 100);

        assertThat(result).isEmpty();
    }

    // ===== 도우미 =====

    private void 시설(String type, String name, double lng, double lat) {
        jdbcClient.sql("""
                        INSERT INTO route_facility (facility_type, facility_name, geom)
                        VALUES (:type, :name, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326))
                        """)
                .param("type", type).param("name", name)
                .param("lng", lng).param("lat", lat)
                .update();
    }

    private void 대여소(String code, String name, double lng, double lat, boolean active) {
        jdbcClient.sql("""
                        INSERT INTO bike_station (station_code, station_name, geom, rack_count, is_active)
                        VALUES (:code, :name, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326), 10, :active)
                        """)
                .param("code", code).param("name", name)
                .param("lng", lng).param("lat", lat).param("active", active)
                .update();
    }

    private void 수리소(String name, double lng, double lat,
                     boolean free, String hours, String tel) {
        jdbcClient.sql("""
                        INSERT INTO repair_shop (shop_name, geom, addr, is_free, operating_hours, tel)
                        VALUES (:name, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326),
                                '서울 영등포구', :free, :hours, :tel)
                        """)
                .param("name", name).param("lng", lng).param("lat", lat)
                .param("free", free).param("hours", hours).param("tel", tel)
                .update();
    }
}
