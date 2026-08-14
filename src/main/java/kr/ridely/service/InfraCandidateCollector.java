package kr.ridely.service;

import kr.ridely.config.RouteProperties;
import kr.ridely.dao.PoiSpatialDao;
import kr.ridely.dto.poi.PoiItemDTO;
import kr.ridely.dto.route.CandidateDTO;
import kr.ridely.dto.route.RouteCandidatesDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 코스 설계용 후보 수집기.
 *
 * 급수대·수리소·따릉이를 축 주변에서 뽑고 관광 후보(TourCandidateCollector)까지 합쳐 RouteCandidatesDTO 한 덩어리로 만든다. 오케스트레이터는 이 메서드 하나만 부르면 된다.
 *
 * 반경과 개수를 모두 목표 거리에서 유도하는 것이 이 클래스의 핵심이다. 고정값을 쓰면 5km 코스에서는 과잉 수집하고 40km 코스에서는 코스 후반부에 배치할 후보가 없다.
 */
@Component
public class InfraCandidateCollector {

    private static final Logger log = LoggerFactory.getLogger(InfraCandidateCollector.class);

    /** route_facility에서 급수대만 뽑는다. 화장실·인증센터는 경유지로 제안할 대상이 아니다 */
    private static final List<String> WATER_FACILITY_TYPES = List.of("WATER");

    private static final String TYPE_WATER = "WATER";
    private static final String TYPE_REPAIR_SHOP = "REPAIR_SHOP";
    private static final String TYPE_BIKE_STATION = "BIKE_STATION";

    /**
     * 지구 반지름(m). 축 길이를 재는 데 쓴다.
     *
     * BikeRouteIngestServiceImpl에도 같은 상수와 하버사인 식이 있다. 아직 두 곳이라 공통 유틸로 빼지 않았다. 세 번째가 생기면 그때 옮긴다.
     */
    private static final double EARTH_RADIUS_M = 6371008.8;

    /** 원의 둘레에서 반지름을 얻는 계수. 순환 코스의 검색 반경을 목표 거리에서 유도할 때 쓴다 */
    private static final double CIRCUMFERENCE_TO_RADIUS = 2 * Math.PI;

    /** 왕복이므로 여유 거리의 절반이 축에서 벗어날 수 있는 최대 거리다 */
    private static final double SLACK_TO_DETOUR = 2.0;

    private static final double KM_TO_M = 1000.0;

    /**
     * 같은 타입에서 이 거리 안에 있으면 같은 지점으로 보고 하나만 남긴다 (m).
     *
     * 원본에 좌표가 몇 미터씩 어긋난 중복 등록이 있다. 실측에서 급수대 8건 중 3건이 2.2~5.5m 거리의 쌍이었고(id 808↔807, 828↔829, 790↔791), 관광지 상수동 카페거리와 로렌스길은 좌표가 완전히 같다. CSV 적재 때 (종류·이름·좌표) 완전 일치만 걸러서 이런 건은 통과했다.
     *
     * 30m로 잡은 이유는 자전거 경유지 기준이다. 30m 안의 두 지점은 같은 정차지이므로 LLM에게 선택지로 둘 다 줄 이유가 없다. 65m 떨어진 쌍(785↔786)은 별개 시설일 수 있어 남긴다.
     */
    private static final int NEAR_DUPLICATE_M = 30;

    /**
     * 조회 배수. 근접 중복으로 걸러질 몫을 감안해 목표 개수보다 넉넉히 뽑는다.
     *
     * 실측 최악이 8건 중 3건(37%)이 중복이었다. 2배면 그보다 나쁜 경우도 흡수한다.
     */
    private static final int OVER_FETCH_FACTOR = 2;

    private final PoiSpatialDao poiSpatialDao;
    private final TourCandidateCollector tourCandidateCollector;
    private final RouteProperties routeProperties;

    public InfraCandidateCollector(PoiSpatialDao poiSpatialDao,
                                   TourCandidateCollector tourCandidateCollector,
                                   RouteProperties routeProperties) {
        this.poiSpatialDao = poiSpatialDao;
        this.tourCandidateCollector = tourCandidateCollector;
        this.routeProperties = routeProperties;
    }

    /**
     * 코스 설계에 넘길 후보를 전부 모은다.
     *
     * @param endLng           도착지 경도. null이면 순환 코스로 본다
     * @param endLat           도착지 위도. null이면 위와 같다
     * @param targetDistanceKm 목표 거리. 반경과 개수를 모두 여기서 유도한다
     */
    public RouteCandidatesDTO collect(double startLng, double startLat,
                                      Double endLng, Double endLat, double targetDistanceKm) {
        int radiusM = searchRadiusM(startLng, startLat, endLng, endLat, targetDistanceKm);
        RouteProperties.CandidatePerKm perKm = routeProperties.candidatePerKm();

        int tourCount = routeProperties.candidateCount(perKm.tour(), targetDistanceKm);
        int waterCount = routeProperties.candidateCount(perKm.water(), targetDistanceKm);
        int repairShopCount = routeProperties.candidateCount(perKm.repairShop(), targetDistanceKm);
        int bikeStationCount = routeProperties.candidateCount(perKm.bikeStation(), targetDistanceKm);

        RouteCandidatesDTO candidates = new RouteCandidatesDTO();
        candidates.setTours(dedupeNearby(tourCandidateCollector.collect(
                startLng, startLat, endLng, endLat, radiusM, fetchLimit(tourCount)), tourCount));
        candidates.setWaters(dedupeNearby(toCandidates(TYPE_WATER, poiSpatialDao.findRouteFacilities(
                startLng, startLat, endLng, endLat, radiusM, WATER_FACILITY_TYPES,
                fetchLimit(waterCount))), waterCount));
        candidates.setRepairShops(dedupeNearby(toCandidates(TYPE_REPAIR_SHOP, poiSpatialDao.findRepairShops(
                startLng, startLat, endLng, endLat, radiusM, fetchLimit(repairShopCount))), repairShopCount));
        candidates.setBikeStations(dedupeNearby(toCandidates(TYPE_BIKE_STATION, poiSpatialDao.findBikeStations(
                startLng, startLat, endLng, endLat, radiusM, fetchLimit(bikeStationCount))), bikeStationCount));

        log.debug("후보 수집: 목표 {}km, 반경 {}m — 관광 {} / 급수대 {} / 수리소 {} / 따릉이 {} (합 {})",
                targetDistanceKm, radiusM, candidates.getTours().size(), candidates.getWaters().size(),
                candidates.getRepairShops().size(), candidates.getBikeStations().size(),
                candidates.totalCount());
        return candidates;
    }

    private int fetchLimit(int targetCount) {
        return targetCount * OVER_FETCH_FACTOR;
    }

    /**
     * 근접 중복을 접고 목표 개수까지 자른다.
     *
     * 입력은 축에서 가까운 순으로 정렬돼 있으므로 앞엣것을 남기고 뒤엣것을 버린다. 이미 채택한 후보 중 NEAR_DUPLICATE_M 안에 있는 것이 하나라도 있으면 같은 지점으로 본다.
     *
     * 목록이 최대 30건이라 이중 순회로 둔다. 공간 색인을 쓸 규모가 아니다.
     */
    private List<CandidateDTO> dedupeNearby(List<CandidateDTO> sortedByDistance, int limit) {
        List<CandidateDTO> kept = new ArrayList<>();
        for (CandidateDTO candidate : sortedByDistance) {
            if (kept.size() >= limit) {
                break;
            }
            boolean duplicate = kept.stream().anyMatch(k ->
                    haversineM(k.getLng(), k.getLat(), candidate.getLng(), candidate.getLat())
                            < NEAR_DUPLICATE_M);
            if (!duplicate) {
                kept.add(candidate);
            }
        }
        return kept;
    }

    /**
     * 검색 반경을 목표 거리에서 유도한다.
     *
     * 도착지가 있으면 직선거리와 목표 거리의 차이가 "얼마나 돌아갈 수 있는가"다. 왕복이므로 그 여유의 절반이 축에서 벗어날 수 있는 최대 거리가 된다. 선유도에서 여의도까지 직선 3.9km인데 12km를 타려면 축에서 최대 4km까지 벗어날 수 있고, 그 범위의 시설이 실제 경로 주변이 된다.
     *
     * 도착지가 없는 순환 코스는 축이 없으므로 목표 거리를 둘레로 보고 반지름을 역산한다. 15km 순환이면 약 2.4km다. 실제 코스가 원은 아니지만 얼마나 멀리 나갈 수 있는지의 상한으로는 맞다.
     *
     * 두 경우 모두 설정의 하한·상한으로 자른다. 하한은 한강 폭에서 나온 값이라 짧은 코스에서도 건너편 강안을 놓치지 않게 하고, 상한은 코스와 무관한 지역이 후보로 들어오는 것을 막는다.
     */
    private int searchRadiusM(double startLng, double startLat,
                              Double endLng, Double endLat, double targetDistanceKm) {
        double derivedM;
        if (endLng != null && endLat != null) {
            double axisKm = haversineM(startLng, startLat, endLng, endLat) / KM_TO_M;
            double slackKm = Math.max(0, targetDistanceKm - axisKm);
            derivedM = slackKm / SLACK_TO_DETOUR * KM_TO_M;
        } else {
            derivedM = targetDistanceKm * KM_TO_M / CIRCUMFERENCE_TO_RADIUS;
        }

        double cappedM = Math.min(derivedM, routeProperties.candidateRadiusKm() * KM_TO_M);
        return (int) Math.max(routeProperties.candidateMinRadiusM(), cappedM);
    }

    /** 두 좌표 사이의 대권 거리(m) */
    private double haversineM(double lng1, double lat1, double lng2, double lat2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dPhi = phi2 - phi1;
        double dLambda = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(a));
    }

    /** PoiItemDTO를 후보 형태로 줄인다. 프롬프트에 쓰지 않는 필드를 여기서 떨군다 */
    private List<CandidateDTO> toCandidates(String type, List<PoiItemDTO> items) {
        return items.stream()
                .map(p -> new CandidateDTO(type, p.getId(), p.getName(),
                        p.getLat(), p.getLng(), p.getDistanceM()))
                .toList();
    }
}
