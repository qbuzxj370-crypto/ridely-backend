package kr.ridely.dto.poi;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 서비스 지역 인프라 POI 전체 조회 응답 (GET /api/v1/pois/all).
 *
 * <h3>왜 「주변」이 아니라 「전부」인가</h3>
 *
 * 내 주변 시설을 서버에 물으면 요청에 내 위치가 실린다. 위치정보를 서버로 보내지 않는다는 원칙
 * (LOCATION_PRIVACY_ARCHITECTURE.md) 때문에, 앱이 <b>적재된 전부를 한 번 받아 폰에서 GPS로 거른다.</b>
 * 그래서 이 응답에는 조회 중심(center)도 반경(radiusM)도 없고, 요청에도 그런 값이 없다. 모든
 * 사용자가 같은 요청을 보내 같은 응답을 받는다.
 *
 * <b>파라미터를 추가하지 말 것.</b> 좌표·반경·격자·「내 근처 우선」 같은 위치 단서가 요청에
 * 들어가는 순간 이 계약이 깨진다.
 *
 * <h3>항목</h3>
 *
 * {@link PoiItemDTO}를 그대로 쓰되 지도에 찍고 이름을 보여줄 필드만 채운다. {@code distanceM}은
 * 기준점이 없어 항상 없고(전역 non_null 설정으로 응답에서 빠진다), 사고다발지 폴리곤 같은 무거운
 * 필드는 담지 않는다. 종류마다 채워지는 필드는 {@code PoiSpatialDao.findAll*}·
 * {@code AccidentZoneSpatialDao.findAll}에 있다.
 *
 * 항목 순서는 종류별(시설 → 수리소 → 따릉이 → 사고다발지)로 번호순이며 실행마다 같다. 순서가
 * 흔들리면 본문이 바뀌어 ETag(304) 캐시가 무의미해진다.
 *
 * 응답 예시 (ApiResponse로 감싸진 상태, items는 일부):
 * <pre>
 * {
 *   "success": true,
 *   "data": {
 *     "items": [
 *       {"type": "ROUTE_FACILITY", "id": 5, "name": "여의도 급수대", "lat": 37.526, "lng": 126.933, "facilityType": "WATER"},
 *       {"type": "BIKE_STATION", "id": 2, "name": "여의나루역 대여소", "lat": 37.527, "lng": 126.934}
 *     ],
 *     "totalCount": 5498
 *   },
 *   "error": null
 * }
 * </pre>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PoiAllResponseDTO {

    /** 적재된 전체 POI. 없으면 빈 배열 */
    private List<PoiItemDTO> items;

    /** items의 개수 */
    private int totalCount;
}
