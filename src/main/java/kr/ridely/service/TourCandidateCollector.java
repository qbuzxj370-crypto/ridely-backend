package kr.ridely.service;

import kr.ridely.dao.TourSpatialDao;
import kr.ridely.dto.route.CandidateDTO;
import kr.ridely.dto.tour.TourAttractionDTO;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 코스 설계용 관광 후보 수집기.
 *
 * 출발지~도착지 축 주변의 관광지를 뽑는다. 실제 조회는 TourSpatialDao가 하고, 반경과 개수는 호출부(InfraCandidateCollector)가 목표 거리에서 유도해 넘긴다. 축에 가까운 것과 먼 것이 번갈아 오는 순서이므로 여기서 다시 정렬하지 않는다.
 */
@Component
public class TourCandidateCollector {

    /**
     * 수집 대상 관광타입. 12=관광지, 14=문화시설.
     *
     * 39(음식점)는 넣지 않는다. 자전거 코스의 경유지로 식당을 제안하면 코스 성격이 흐려지고, 영업시간·휴무 정보가 없어 실제로 문을 열었는지 알 수 없다. 15(축제)는 기간이 지난 항목이 섞여 별도 판정이 필요해 제외한다.
     */
    private static final List<String> CONTENT_TYPE_IDS = List.of("12", "14");

    private static final String TYPE_TOUR = "TOUR";

    private final TourSpatialDao tourSpatialDao;

    public TourCandidateCollector(TourSpatialDao tourSpatialDao) {
        this.tourSpatialDao = tourSpatialDao;
    }

    /**
     * 축 주변 관광 후보를 모은다.
     *
     * @param endLng    도착지 경도. null이면 순환 코스로 보고 출발점 반경만 본다
     * @param endLat    도착지 위도. null이면 위와 같다
     * @param corridorM  축에서 이 거리 안까지 후보로 본다 (m)
     * @param fetchLimit 조회 건수. 근접 중복을 걸러낼 몫까지 포함해 호출부가 넉넉히 넘긴다
     */
    public List<CandidateDTO> collect(double startLng, double startLat,
                                      Double endLng, Double endLat, int corridorM, int fetchLimit) {
        List<TourAttractionDTO> found = tourSpatialDao.findAlongCorridor(
                startLng, startLat, endLng, endLat, corridorM, CONTENT_TYPE_IDS, fetchLimit);

        return found.stream()
                .map(t -> new CandidateDTO(TYPE_TOUR, t.getTourAttractionId(), t.getTitle(),
                        t.getLat(), t.getLng(), t.getDistanceM()))
                .toList();
    }
}
