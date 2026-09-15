package kr.ridely.service;

import kr.ridely.common.BusinessException;
import kr.ridely.common.ErrorCode;
import kr.ridely.config.MvpAreaProperties;
import kr.ridely.dao.AccidentZoneSpatialDao;
import kr.ridely.dao.PoiSpatialDao;
import kr.ridely.dto.poi.PoiItemDTO;
import kr.ridely.dto.poi.PoiNearbyResponseDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 인프라 POI 조회 종류 선택·정렬 단위 테스트.
 *
 * DB 없이 분기만 본다. DAO 둘은 익명 서브클래스로 흉내 낸다 - 목 라이브러리를 들이지 않고 {@code RecommendationReplayStoreTest}가 쓴 방식과 맞춘다.
 *
 * <b>여기서 고정하려는 것은 셋이다.</b> 요청한 종류만 조회하는지, 종류별 결과를 합친 뒤 거리순으로 다시 정렬하는지, 그리고 <b>0건이 예외가 아닌지</b>다. 마지막이 이 API를 관광지 조회와 가르는 지점이다.
 *
 * 반경 필터와 거리 계산은 여기서 다루지 않는다. {@code ST_DWithin}과 {@code ST_Distance}가 하는 일이라 DAO를 흉내 내는 순간 사라진다.
 */
class PoiNearbySelectionTest {

    /** 서비스 지역 안. 여의도한강공원 */
    private static final double IN_LAT = 37.5265;
    private static final double IN_LNG = 126.9339;

    private static final int RADIUS_M = 1000;

    /** 어느 DAO 메서드가 불렸는지 기록한다 */
    private final List<String> called = new ArrayList<>();

    private final PoiServiceImpl service =
            new PoiServiceImpl(fakePoiDao(), fakeAccidentDao(), mvpArea());

    @Test
    @DisplayName("종류를 지정하지 않으면 전부 조회한다")
    void queriesEveryTypeByDefault() {
        service.findNearby(IN_LAT, IN_LNG, RADIUS_M, List.of());

        assertThat(called).containsExactlyInAnyOrder(
                "routeFacilities", "repairShops", "bikeStations", "accidentZones");
    }

    @Test
    @DisplayName("지정한 종류만 조회한다")
    void queriesOnlyRequestedTypes() {
        // 레이어를 하나만 켠 화면이 매번 네 테이블을 훑으면 안 된다
        service.findNearby(IN_LAT, IN_LNG, RADIUS_M, List.of("BIKE_STATION"));

        assertThat(called).containsExactly("bikeStations");
    }

    @Test
    @DisplayName("route_facility 종류는 한 번의 조회로 묶인다")
    void groupsFacilityTypesIntoOneQuery() {
        // WATER·TOILET·CERT_CENTER·AIR_PUMP는 같은 테이블이다. 따로 부르면 질의가 네 배가 된다
        service.findNearby(IN_LAT, IN_LNG, RADIUS_M, List.of("WATER", "TOILET", "AIR_PUMP"));

        assertThat(called).containsExactly("routeFacilities");
    }

    @Test
    @DisplayName("종류가 달라도 거리순으로 합쳐진다")
    void mergesAcrossTypesByDistance() {
        // 종류별로 따로 조회하므로 합친 직후에는 종류 순서로 뭉쳐 있다.
        // 다시 정렬하지 않으면 지도 목록에서 먼 급수대가 가까운 따릉이보다 위에 온다
        PoiNearbyResponseDTO response =
                service.findNearby(IN_LAT, IN_LNG, RADIUS_M, List.of());

        assertThat(response.getItems())
                .extracting(PoiItemDTO::getDistanceM)
                .containsExactly(10, 20, 30, 40);
    }

    @Test
    @DisplayName("조회 조건을 응답에 그대로 싣는다")
    void echoesQueryCondition() {
        PoiNearbyResponseDTO response =
                service.findNearby(IN_LAT, IN_LNG, 2500, List.of());

        assertThat(response.getCenter().getLat()).isEqualTo(IN_LAT);
        assertThat(response.getCenter().getLng()).isEqualTo(IN_LNG);
        assertThat(response.getRadiusM()).isEqualTo(2500);
        assertThat(response.getTotalCount()).isEqualTo(response.getItems().size());
    }

    @Test
    @DisplayName("0건이어도 예외를 내지 않는다")
    void emptyResultIsNotAnError() {
        // ★ 관광지 조회와 갈리는 지점이다. 서비스 지역 격자의 93.8%가 반경 1km에
        //   수리소 0건이라(2026-09-15 측정) 404로 내면 정상 동작의 대부분이 에러가 된다
        PoiServiceImpl empty = new PoiServiceImpl(emptyPoiDao(), emptyAccidentDao(), mvpArea());

        PoiNearbyResponseDTO response = empty.findNearby(IN_LAT, IN_LNG, RADIUS_M, List.of());

        assertThat(response.getItems()).isEmpty();
        assertThat(response.getTotalCount()).isZero();
    }

    @Test
    @DisplayName("서비스 지역 밖이면 ROUTE-003이다")
    void outsideServiceAreaIsRejected() {
        // 지역 안 0건과 달리 여기는 지도를 아무리 옮겨도 안 나온다. 같은 응답을 주면 안 된다
        assertThatThrownBy(() -> service.findNearby(35.1796, 129.0756, RADIUS_M, List.of()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ROUTE_003);
    }

    @Test
    @DisplayName("지역 밖이면 DB를 뒤지지 않는다")
    void outsideServiceAreaSkipsQueries() {
        assertThatThrownBy(() -> service.findNearby(35.1796, 129.0756, RADIUS_M, List.of()))
                .isInstanceOf(BusinessException.class);

        assertThat(called).isEmpty();
    }

    // ===== 도우미 =====

    /** application.yml의 ridely.mvp-area와 같은 값 */
    private MvpAreaProperties mvpArea() {
        return new MvpAreaProperties(37.500, 37.610, 126.780, 127.130);
    }

    private PoiItemDTO item(String type, int distanceM) {
        return new PoiItemDTO(type, 1L, type + "-" + distanceM, IN_LAT, IN_LNG, distanceM);
    }

    /** 종류마다 한 건씩 돌려주되 거리를 뒤섞어 둔다. 정렬이 실제로 도는지 보기 위해서다 */
    private PoiSpatialDao fakePoiDao() {
        return new PoiSpatialDao(null) {

            @Override
            public List<PoiItemDTO> findRouteFacilitiesInRadius(double lng, double lat, int radiusM,
                                                                List<String> facilityTypes, int limit) {
                called.add("routeFacilities");
                return List.of(item("ROUTE_FACILITY", 30));
            }

            @Override
            public List<PoiItemDTO> findRepairShopsInRadius(double lng, double lat,
                                                            int radiusM, int limit) {
                called.add("repairShops");
                return List.of(item("REPAIR_SHOP", 10));
            }

            @Override
            public List<PoiItemDTO> findBikeStationsInRadius(double lng, double lat,
                                                             int radiusM, int limit) {
                called.add("bikeStations");
                return List.of(item("BIKE_STATION", 40));
            }
        };
    }

    private AccidentZoneSpatialDao fakeAccidentDao() {
        return new AccidentZoneSpatialDao(null) {

            @Override
            public List<PoiItemDTO> findNearby(double lng, double lat, int radiusM, int limit) {
                called.add("accidentZones");
                return List.of(item("ACCIDENT_ZONE", 20));
            }
        };
    }

    private PoiSpatialDao emptyPoiDao() {
        return new PoiSpatialDao(null) {

            @Override
            public List<PoiItemDTO> findRouteFacilitiesInRadius(double lng, double lat, int radiusM,
                                                                List<String> facilityTypes, int limit) {
                return List.of();
            }

            @Override
            public List<PoiItemDTO> findRepairShopsInRadius(double lng, double lat,
                                                            int radiusM, int limit) {
                return List.of();
            }

            @Override
            public List<PoiItemDTO> findBikeStationsInRadius(double lng, double lat,
                                                             int radiusM, int limit) {
                return List.of();
            }
        };
    }

    private AccidentZoneSpatialDao emptyAccidentDao() {
        return new AccidentZoneSpatialDao(null) {

            @Override
            public List<PoiItemDTO> findNearby(double lng, double lat, int radiusM, int limit) {
                return List.of();
            }
        };
    }
}
