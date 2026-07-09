package kr.ridely.dto.poi;

import lombok.*;

import java.math.BigDecimal;

/**
 * 자전거도로 응답.
 * 지도에 자전거도로 레이어를 그릴 때 사용한다.
 *
 * 대응 테이블: bike_road
 * 원본 출처  : 전국자전거도로표준데이터 (공공데이터포털)
 *
 * ※ 이 데이터는 '점'이 아니라 '선'이라서 PoiItemDTO에 합치지 않았다.
 *
 * ※ 주의: 원본 표준데이터는 노선 형상(선)을 제공하지 않고
 *   기점·종점 주소와 좌표만 준다. 게다가 좌표 결측이 많다.
 *   선 형상(lineGeoJson)은 V-World WFS 자전거길 레이어로 따로 채워 넣어야 하며, 아직 못 채운 노선은 null이다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BikeRoadDTO {

    /** 고유 번호 (bike_road.bike_road_id) */
    private Long bikeRoadId;

    /** 노선명. 예: "한강공원 자전거도로" */
    private String routeName;

    /**
     * 자전거도로 종류.
     * 자전거전용도로 / 자전거보행자겸용도로 / 자전거전용차로 / 자전거우선도로
     * (원본 데이터 표기를 그대로 쓴다. 값이 비어 있는 행도 있다)
     */
    private String roadType;

    /** 시도명. 예: "서울특별시" */
    private String sidoName;

    /** 시군구명. 예: "영등포구" */
    private String sigunguName;

    /** 총 길이(km) */
    private BigDecimal totalLengthKm;

    /** 자전거도로 폭(m). 없으면 null */
    private BigDecimal bikeWidthM;

    /** 기점 위도. 원본 좌표 결측이 잦아 null일 수 있다 */
    private Double startLat;

    /** 기점 경도. 원본 좌표 결측이 잦아 null일 수 있다 */
    private Double startLng;

    /** 종점 위도. null일 수 있다 */
    private Double endLat;

    /** 종점 경도. null일 수 있다 */
    private Double endLng;

    /**
     * 노선 전체 형상 (GeoJSON LineString 문자열).
     * 지도에 선을 그릴 때 사용한다.
     * 아직 보강하지 못한 노선은 null이며, 이 경우 지도에 그릴 수 없다.
     */
    private String lineGeoJson;
}
