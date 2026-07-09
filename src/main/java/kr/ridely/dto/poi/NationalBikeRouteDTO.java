package kr.ridely.dto.poi;

import lombok.*;

import java.math.BigDecimal;

/**
 * 국토종주 자전거길 응답 (한강종주길, 4대강 종주길 등 13개 노선).
 * 지도에 종주길 레이어를 그리거나 코스 설계의 뼈대로 사용한다.
 *
 * 대응 테이블: national_bike_route
 * 원본 출처  : 행정안전부 자전거길 DB (공공데이터포털)
 *
 * ※ 이 데이터도 '선'이라서 PoiItemDTO에 합치지 않았다.
 *
 * ※ 종주길 위의 인증센터·화장실·급수대는 route_facility 테이블에 따로 있고,
 *   그건 '점'이라서 PoiItemDTO(type="ROUTE_FACILITY")로 나간다.
 *
 * ※ 전국 단위 노선이라 특정 지역(region)에 속하지 않는다.
 *
 * 응답 예시:
 * {
 *   "nationalBikeRouteId": 1,
 *   "routeName": "한강종주자전거길",
 *   "startDesc": "아라한강갑문",
 *   "endDesc": "충주댐",
 *   "totalLengthKm": 192.0,
 *   "lineGeoJson": "{\"type\":\"LineString\",\"coordinates\":[...]}"
 * }
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NationalBikeRouteDTO {

    /** 고유 번호 (national_bike_route.national_bike_route_id) */
    private Long nationalBikeRouteId;

    /** 노선명. 예: "한강종주자전거길" */
    private String routeName;

    /** 출발 지점 설명. 예: "아라한강갑문" */
    private String startDesc;

    /** 도착 지점 설명. 예: "충주댐" */
    private String endDesc;

    /** 노선 총 길이(km) */
    private BigDecimal totalLengthKm;

    /**
     * 노선 형상 (GeoJSON LineString 문자열).
     * 원본 CSV의 좌표 시퀀스를 선으로 이어 붙인 것이다.
     */
    private String lineGeoJson;
}
