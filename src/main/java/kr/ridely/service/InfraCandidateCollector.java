package kr.ridely.service;

import kr.ridely.common.GeoDistance;
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
     * 조회 배수. 걸러질 몫을 감안해 목표 개수보다 넉넉히 뽑는다.
     *
     * 걸러지는 이유가 둘이다. 근접 중복이 실측 최악 8건 중 3건(37%)이었고, 축 밖 후보가 관광지 48건 중 25건(52%)이었다. 둘이 겹치지 않는다고 보면 목표 개수를 채우는 데 3배가 필요하다.
     */
    private static final int OVER_FETCH_FACTOR = 3;

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
        candidates.setTours(tourCandidateCollector.collect(
                startLng, startLat, endLng, endLat, radiusM, fetchLimit(tourCount)));
        candidates.setWaters(toCandidates(TYPE_WATER, poiSpatialDao.findRouteFacilities(
                startLng, startLat, endLng, endLat, radiusM, WATER_FACILITY_TYPES,
                fetchLimit(waterCount))));
        candidates.setRepairShops(toCandidates(TYPE_REPAIR_SHOP, poiSpatialDao.findRepairShops(
                startLng, startLat, endLng, endLat, radiusM, fetchLimit(repairShopCount))));
        candidates.setBikeStations(toCandidates(TYPE_BIKE_STATION, poiSpatialDao.findBikeStations(
                startLng, startLat, endLng, endLat, radiusM, fetchLimit(bikeStationCount))));

        // 진행도를 먼저 채우고 그다음에 자른다. 순서가 반대면 축 밖 후보가 자리를 차지한 채 잘려
        // 최종 목록이 목표 개수에 못 미친다
        fillProgressRatio(candidates, startLng, startLat, endLng, endLat);

        candidates.setTours(selectOnSpan(candidates.getTours(), tourCount));
        candidates.setWaters(selectOnSpan(candidates.getWaters(), waterCount));
        candidates.setRepairShops(selectOnSpan(candidates.getRepairShops(), repairShopCount));
        candidates.setBikeStations(selectOnSpan(candidates.getBikeStations(), bikeStationCount));

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
     * 축을 벗어난 후보를 버리고, 근접 중복을 접은 뒤 목표 개수까지 자른다.
     *
     * 축 밖이란 출발지 이전이나 도착지 너머로 투영되는 지점이다. 여의도로 가는 코스에 국립중앙박물관이 후보로 들어오는 식이고, 실측에서 관광지 48건 중 25건이 그랬다. 이런 후보는 들러도 "가는 길"이 아니라 지나쳤다가 되돌아오는 것이라 코스가 성립하지 않는다.
     *
     * 버리는 데는 부수 효과가 하나 더 있다. 조회의 distanceM은 축 선분까지의 거리라, 끝점 너머의 점에서는 "옆으로 벗어난 거리"가 아니라 "끝점에서 더 나간 거리"를 뜻한다. 축 밖을 걷어내면 남은 후보의 distanceM이 모두 같은 의미가 되고, 원거리 후보를 뽑는 정렬(PoiSpatialDao.PICK_BOTH_ENDS)도 그제야 의도대로 측면 후보를 집는다.
     *
     * 순환 코스는 축이 없어 진행도가 null이다. 그때는 거를 기준이 없으므로 전부 남긴다.
     */
    private List<CandidateDTO> selectOnSpan(List<CandidateDTO> fetched, int limit) {
        List<CandidateDTO> onSpan = fetched.stream()
                .filter(c -> c.getProgressRatio() == null
                        || (c.getProgressRatio() >= 0.0 && c.getProgressRatio() <= 1.0))
                .toList();
        return dedupeNearby(onSpan, limit);
    }

    /**
     * 근접 중복을 접고 목표 개수까지 자른다.
     *
     * 입력 순서는 조회가 정한 우선순위다. 축에서 가까운 후보와 먼 후보가 번갈아 오므로(PoiSpatialDao.PICK_BOTH_ENDS) 그 순서를 그대로 지켜야 근거리·원거리 비율이 유지된다. 앞엣것을 남기고 뒤엣것을 버리며, 이미 채택한 후보 중 NEAR_DUPLICATE_M 안에 있는 것이 하나라도 있으면 같은 지점으로 본다.
     *
     * 중복 쌍은 거리가 몇 미터 차이라 우선순위도 서로 붙어 있다. 어느 쪽이 먼저 오든 남는 지점은 같다.
     *
     * 목록이 타입당 수백 건을 넘지 않아 이중 순회로 둔다. 상한(candidate-max-count)의 두 배가 실제 입력 크기이고, 그 제곱이어도 한 요청에서 무시할 비용이다. 공간 색인을 쓸 규모가 아니다.
     */
    private List<CandidateDTO> dedupeNearby(List<CandidateDTO> byPickOrder, int limit) {
        List<CandidateDTO> kept = new ArrayList<>();
        for (CandidateDTO candidate : byPickOrder) {
            if (kept.size() >= limit) {
                break;
            }
            boolean duplicate = kept.stream().anyMatch(k ->
                    GeoDistance.haversineM(k.getLng(), k.getLat(), candidate.getLng(), candidate.getLat())
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
            double axisKm = GeoDistance.haversineM(startLng, startLat, endLng, endLat) / KM_TO_M;
            double slackKm = Math.max(0, targetDistanceKm - axisKm);
            derivedM = slackKm / SLACK_TO_DETOUR * KM_TO_M;
        } else {
            derivedM = targetDistanceKm * KM_TO_M / CIRCUMFERENCE_TO_RADIUS;
        }

        double cappedM = Math.min(derivedM, routeProperties.candidateRadiusKm() * KM_TO_M);
        return (int) Math.max(routeProperties.candidateMinRadiusM(), cappedM);
    }

    /**
     * 후보를 출발~도착 축 위로 투영해 진행도를 채운다.
     *
     * 조회 결과의 distanceM은 축에서 수직으로 떨어진 거리라 "경로의 어느 지점인가"를 알려주지 않는다. LLM이 경유지를 순서대로 배치하려면 그 값이 필요하다.
     *
     * 투영은 평면 근사로 한다. 축 길이가 수 km 규모라 위경도를 그대로 벡터로 다뤄도 오차가 무시할 수준이고, 경도는 위도에 따라 좁아지므로 cos(위도)로 보정한다.
     *
     * ⚠️ 0~1로 자르지 않는다. 예전에는 잘랐는데, 그러면 축 밖 후보가 전부 0.0이나 1.0이 되어 출발지·도착지에 딱 붙은 후보와 구분되지 않는다. 실제로 서로 다른 관광지 스물다섯 곳이 프롬프트에 모두 "12km 지점"으로 찍혀 나갔다. 범위를 벗어난 값은 벗어난 채로 두어야 selectOnSpan이 걸러낼 수 있다.
     *
     * 도착지가 없는 순환 코스는 축이 없어 진행도를 정의할 수 없다. null로 남기고 프롬프트에서 위치 표현을 생략한다.
     */
    private void fillProgressRatio(RouteCandidatesDTO candidates,
                                   double startLng, double startLat, Double endLng, Double endLat) {
        if (endLng == null || endLat == null) {
            return;
        }
        double lngScale = Math.cos(Math.toRadians(startLat));
        double axisX = (endLng - startLng) * lngScale;
        double axisY = endLat - startLat;
        double axisLengthSquared = axisX * axisX + axisY * axisY;
        if (axisLengthSquared == 0) {
            return;
        }

        for (CandidateDTO c : candidates.all()) {
            double px = (c.getLng() - startLng) * lngScale;
            double py = c.getLat() - startLat;
            c.setProgressRatio((px * axisX + py * axisY) / axisLengthSquared);
        }
    }

    /** PoiItemDTO를 후보 형태로 줄인다. 프롬프트에 쓰지 않는 필드를 여기서 떨군다 */
    private List<CandidateDTO> toCandidates(String type, List<PoiItemDTO> items) {
        return items.stream()
                .map(p -> new CandidateDTO(type, p.getId(), p.getName(),
                        p.getLat(), p.getLng(), p.getDistanceM()))
                .toList();
    }
}
