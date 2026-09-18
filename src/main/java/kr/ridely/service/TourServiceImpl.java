package kr.ridely.service;

import kr.ridely.common.BusinessException;
import kr.ridely.common.ErrorCode;
import kr.ridely.dao.TourSpatialDao;
import kr.ridely.dto.tour.TourAttractionDTO;
import kr.ridely.dto.tour.TourNearbyResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 관광 콘텐츠 조회 구현.
 */
@Service
@RequiredArgsConstructor
public class TourServiceImpl implements TourService {

    /** 타입을 지정하지 않았을 때 조회할 관광타입 (관광지·문화시설·음식점) */
    private static final List<String> DEFAULT_CONTENT_TYPE_IDS = List.of("12", "14", "39");

    /**
     * 한 번에 반환할 최대 건수.
     *
     * 지도에 찍을 마커 수와 응답 크기를 감안한 값이다. 반경을 넓게 잡으면
     * 수백 건이 걸리는데(적재된 한강 구간만 734건), 그대로 내보내면 응답이 커지고
     * 클라이언트 렌더링도 느려진다. 거리순 정렬이라 가까운 것부터 채워진다.
     */
    private static final int MAX_RESULTS = 100;

    private final TourSpatialDao tourSpatialDao;

    @Override
    public TourNearbyResponseDTO findNearby(double lat, double lng, int radiusM,
                                            List<String> contentTypeIds) {

        List<String> types = (contentTypeIds == null || contentTypeIds.isEmpty())
                ? DEFAULT_CONTENT_TYPE_IDS
                : contentTypeIds;

        List<TourAttractionDTO> items =
                tourSpatialDao.findNearby(lng, lat, radiusM, types, MAX_RESULTS);

        // 반경 내에 아무것도 없으면 POI-001 (API 명세)
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCode.POI_001);
        }

        return new TourNearbyResponseDTO(
                new TourNearbyResponseDTO.Center(lat, lng),
                radiusM,
                items,
                items.size());
    }

    @Override
    public TourAttractionDTO findById(long tourAttractionId) {
        /*
         * 없는 번호는 POI-001이 아니라 COMMON-004다. POI-001은 「반경 안에 아무것도 없다」는
         * 조회 조건의 문제라 반경을 넓히면 달라지지만, 이쪽은 그 번호가 존재하지 않는 것이라
         * 요청을 어떻게 고쳐도 같은 결과다. /routes/{id} 재조회와 같은 규칙으로 맞춘다.
         */
        return tourSpatialDao.findById(tourAttractionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMON_004));
    }
}
