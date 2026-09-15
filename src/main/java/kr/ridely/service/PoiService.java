package kr.ridely.service;

import kr.ridely.dto.poi.PoiNearbyResponseDTO;

import java.util.List;

/**
 * 자전거 인프라 POI 조회.
 *
 * 화면의 지도 레이어가 쓴다. 종류를 켜고 끌 때마다 부르므로 외부 호출 없이 적재된 데이터만 본다.
 */
public interface PoiService {

    /**
     * 한 점 반경의 인프라 POI를 종류에 상관없이 거리순으로 합쳐 돌려준다.
     *
     * @param types 조회할 종류. 비어 있으면 전체
     * @throws kr.ridely.common.BusinessException 중심 좌표가 서비스 지역 밖이면 ROUTE-003
     */
    PoiNearbyResponseDTO findNearby(double lat, double lng, int radiusM, List<String> types);
}
