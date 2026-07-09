package kr.ridely.dto.route;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 추천된 코스가 통과하는 사고다발지역 하나.
 *
 * 대응 컬럼: recommended_route.passing_danger_zones_json (JSONB 배열의 요소)
 * 원본 테이블: accident_zone
 *
 * ※ 코스의 선(route_geom)과 사고다발구역의 폴리곤(polygon_geom)이 겹치는지를
 *   PostGIS ST_Intersects로 검사해서 찾아낸 목록이다.
 *
 * ※ 회피 설정(avoidDangerZones)을 켠 사용자는 애초에 우회 경로를 받으므로
 *   이 목록이 비어 있게 된다.
 *   끈 사용자는 이 목록을 받아서 지도에 표시하고, 라이딩 중 근처에 가면
 *   진동으로 알려 준다.
 *
 * JSON 예시:
 * {
 *   "accidentZoneId": 1,
 *   "spotName": "마포대교 북단",
 *   "dangerLevel": "WARNING",
 *   "occurrenceCount": 8,
 *   "deathCount": 0,
 *   "lat": 37.5400, "lng": 126.9355,
 *   "polygonGeoJson": "{\"type\":\"Polygon\",\"coordinates\":[...]}"
 * }
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PassingDangerZoneDTO {

    /** 사고다발지역 고유 번호 (accident_zone.accident_zone_id) */
    private Long accidentZoneId;

    /** 지점명. 예: "마포대교 북단" */
    private String spotName;

    /**
     * 위험 등급.
     * CAUTION(사고 4~5건)  → 진동 1회 + "이 부근 사고 좀 있으니 살피면서"
     * WARNING(사고 6~9건)  → 진동 2회 + "여기 사고 잦은 편이에요. 페이스 늦춰가요"
     * DANGER (10건 이상 또는 사망사고 포함) → 진동 3회 + 빨간 배너
     */
    private String dangerLevel;

    /** 사고 발생 건수 */
    private Integer occurrenceCount;

    /** 사망자 수. 0보다 크면 등급이 DANGER가 된다 */
    private Integer deathCount;

    /** 구역 중심점 위도 */
    private double lat;

    /** 구역 중심점 경도 */
    private double lng;

    /**
     * 위험 구역 영역 (GeoJSON Polygon 문자열).
     * 지도에 반투명 색으로 칠할 때 사용한다.
     * 원본 데이터에 폴리곤이 없으면 null (이 경우 중심점만 표시).
     */
    private String polygonGeoJson;
}
