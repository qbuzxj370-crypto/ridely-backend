package kr.ridely.dto.poi;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 주변 인프라 POI 조회 응답 (GET /api/v1/pois/nearby).
 *
 * 조회 조건(center·radiusM)을 함께 돌려주는 것은 {@code /tours/nearby}와 같다. 지도 중심과 반경 원을 다시 그릴 때 요청값을 따로 보관하지 않아도 된다.
 *
 * <b>비어 있는 응답이 정상이다.</b> 관광지 쪽은 0건이면 {@code POI-001}(404)을 내지만 여기는 200에 빈 배열을 준다. 인프라는 종류마다 밀도가 크게 달라서다 - 2026-09-15 측정에서 서비스 지역 격자의 93.8%가 반경 1km에 수리소 0건이었다. 404로 내면 정상 동작의 대부분이 에러 응답이 된다.
 *
 * 응답 예시 (ApiResponse로 감싸진 상태):
 * <pre>
 * {
 *   "success": true,
 *   "data": {
 *     "center": {"lat": 37.5265, "lng": 126.9339},
 *     "radiusM": 1000,
 *     "items": [ ... ],
 *     "totalCount": 12
 *   },
 *   "error": null
 * }
 * </pre>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PoiNearbyResponseDTO {

    /** 조회 중심 좌표 (요청값 그대로 반환) */
    private Center center;

    /** 조회 반경 (m) */
    private int radiusM;

    /** 가까운 순으로 정렬된 POI 목록. 없으면 빈 배열 */
    private List<PoiItemDTO> items;

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
