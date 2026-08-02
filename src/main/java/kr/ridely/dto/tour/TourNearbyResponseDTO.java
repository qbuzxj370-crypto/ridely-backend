package kr.ridely.dto.tour;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 주변 관광지 조회 응답 (GET /api/v1/tours/nearby).
 *
 * 목록만 주지 않고 조회 조건(center·radiusM)을 함께 돌려준다.
 * 클라이언트가 응답만으로 "어느 지점 기준 몇 m 범위인지" 알 수 있어
 * 지도 중심·반경 원을 다시 그릴 때 요청값을 따로 보관하지 않아도 된다.
 *
 * 응답 예시 (ApiResponse로 감싸진 상태):
 * {
 *   "success": true,
 *   "data": {
 *     "center": {"lat": 37.5434, "lng": 126.8997},
 *     "radiusM": 2000,
 *     "items": [ ... ],
 *     "totalCount": 12
 *   },
 *   "error": null
 * }
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TourNearbyResponseDTO {

    /** 조회 중심 좌표 (요청값 그대로 반환) */
    private Center center;

    /** 조회 반경 (m) */
    private int radiusM;

    /** 가까운 순으로 정렬된 관광지 목록 */
    private List<TourAttractionDTO> items;

    /** items의 개수 */
    private int totalCount;

    /** 조회 중심 좌표 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Center {
        private double lat;
        private double lng;
    }
}
