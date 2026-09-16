package kr.ridely.service;

import kr.ridely.dto.geo.GeoSearchResponseDTO;

/**
 * 장소명·주소를 좌표로 바꾼다.
 *
 * 출발지 검색 화면이 쓴다. 사용자가 좌표를 알 리 없으므로 이름으로 찾게 한다.
 */
public interface GeoSearchService {

    /**
     * 검색어로 장소를 찾는다.
     *
     * @throws kr.ridely.common.BusinessException 외부 호출이 실패하면 GEO-001(502)
     */
    GeoSearchResponseDTO search(String query);
}
