package kr.ridely.service;

import kr.ridely.dto.tour.TourNearbyResponseDTO;

import java.util.List;

/**
 * 관광 콘텐츠 조회 서비스.
 *
 * 조회는 적재해 둔 tour_attraction을 PostGIS로 검색한다.
 * TourAPI를 실시간 호출하지 않는다 (개발계정 트래픽이 오퍼레이션당 일 1,000건).
 */
public interface TourService {

    /**
     * 좌표 주변 관광지를 가까운 순으로 조회한다.
     *
     * @param lat            중심 위도
     * @param lng            중심 경도
     * @param radiusM        반경 (m)
     * @param contentTypeIds 관광타입 목록 (비어 있으면 기본값 적용)
     * @throws kr.ridely.common.BusinessException POI-001 (반경 내 결과 없음)
     */
    TourNearbyResponseDTO findNearby(double lat, double lng, int radiusM, List<String> contentTypeIds);
}
