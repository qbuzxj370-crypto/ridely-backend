package kr.ridely.dto.poi;

import lombok.*;

/**
 * 자전거 인프라 POI 통합 응답.
 * GET /api/v1/pois/nearby 의 응답 목록 요소.
 *
 * 서로 다른 5개 테이블을 하나의 DTO로 합쳐서 "거리순 통합 목록"으로 내려준다.
 * 어느 테이블에서 왔는지는 type 필드로 구분한다.
 *
 * 대응 테이블 (모두 GEOMETRY(Point) 를 가진 것들):
 *   - accident_zone   → type = "ACCIDENT_ZONE"
 *   - bike_station    → type = "BIKE_STATION"
 *   - repair_shop     → type = "REPAIR_SHOP"
 *   - route_facility  → type = "ROUTE_FACILITY"
 *   - bike_parking    → type = "BIKE_PARKING"
 *
 * ※ bike_road·national_bike_route는 선(LineString) 데이터라 위경도 한 점으로
 *   표현할 수 없다. 각각 BikeRoadDTO / NationalBikeRouteDTO를 따로 쓴다.
 *
 * ※ 공통 필드(id, name, lat, lng, distanceM)는 항상 채워지고,
 *   그 아래 "타입별 필드"는 해당 type일 때만 채워지고 나머지는 null이다.
 *   예) type이 "BIKE_STATION"이면 rackCount만 값이 있고 dangerLevel 등은 null.
 *
 * 응답 예시:
 * {
 *   "type": "REPAIR_SHOP",
 *   "id": 7,
 *   "name": "영등포구 무료 자전거 수리센터",
 *   "lat": 37.5265, "lng": 126.8962,
 *   "distanceM": 850,
 *   "isFree": true,
 *   "operatingHours": "평일 09:00~18:00"
 * }
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PoiItemDTO {

    // ===== 공통 필드 (모든 타입에서 항상 채워짐) =====

    /**
     * POI 종류.
     * ACCIDENT_ZONE(사고다발지역) / BIKE_STATION(따릉이 대여소)
     * / REPAIR_SHOP(수리센터) / ROUTE_FACILITY(자전거길 시설) / BIKE_PARKING(보관소)
     *
     * 프론트에서 이 값으로 마커 아이콘·색상을 결정한다.
     */
    private String type;

    /** 해당 테이블에서의 고유 번호 (테이블이 다르면 id가 겹칠 수 있으니 항상 type과 함께 본다) */
    private Long id;

    /** 표시할 이름 (수리센터명, 대여소명, 사고다발지점명 등) */
    private String name;

    /** 위도 */
    private double lat;

    /** 경도 */
    private double lng;

    /** 요청한 위치로부터 떨어진 거리(미터). 이 값 기준으로 목록이 정렬된다 */
    private Integer distanceM;

    /** 주소. 데이터에 없으면 null */
    private String addr;

    /** 전화번호. 데이터에 없으면 null */
    private String tel;


    // ===== ACCIDENT_ZONE 전용 (사고다발지역) =====

    /**
     * 위험 등급.
     * CAUTION(사고 4~5건) / WARNING(6~9건) / DANGER(10건 이상 또는 사망사고 포함)
     * 등급에 따라 지도 색상과 진동 횟수가 달라진다.
     */
    private String dangerLevel;

    /** 해당 지점의 사고 발생 건수 */
    private Integer occurrenceCount;

    /** 사망자 수 (0보다 크면 DANGER 등급) */
    private Integer deathCount;

    /**
     * 사고다발구역의 폴리곤 영역 (GeoJSON 문자열).
     * 지도에 히트맵 형태로 칠할 때 사용한다.
     * 원본 데이터에 폴리곤이 없으면 null.
     */
    private String polygonGeoJson;


    // ===== BIKE_STATION 전용 (따릉이 대여소) =====

    /** 거치대 수. 실시간 잔여 대수가 아니라 총 거치대 개수 */
    private Integer rackCount;

    /**
     * 대여소 운영 여부.
     * ※ "지금 자전거가 몇 대 남았는지"는 변동이 잦아 DB에 저장하지 않는다.
     *   필요하면 서울시 실시간 API를 따로 호출하는게 나을듯
     */
    private Boolean isActive;


    // ===== REPAIR_SHOP 전용 (자전거 수리센터) =====

    /** 자치구가 운영하는 무료 수리소인지 여부 */
    private Boolean isFree;

    /** 운영 시간 안내 문구. 예: "평일 09:00~18:00" */
    private String operatingHours;


    // ===== ROUTE_FACILITY 전용 (자전거길 주변 시설) =====

    /**
     * 시설 종류.
     * CERT_CENTER(국토종주 인증센터) / TOILET(화장실) / WATER(급수대) / ETC(기타)
     *
     * ※ 음수대는 별도 테이블이 아니라 이 중에서 WATER 타입이다.
     */
    private String facilityType;


    // ===== BIKE_PARKING 전용 (자전거 보관소) =====

    /**
     * 관리 기관명.
     * ※ 보관소는 '거치대'이지 따릉이 대여소가 아니다. 둘을 혼동하지 말 것.
     */
    private String mgmtAgency;


    /** 공통 필드만 채우는 편의 생성자 (타입별 필드는 이후 setter로 채운다) */
    public PoiItemDTO(String type, Long id, String name, double lat, double lng, Integer distanceM) {
        this.type = type;
        this.id = id;
        this.name = name;
        this.lat = lat;
        this.lng = lng;
        this.distanceM = distanceM;
    }
}
