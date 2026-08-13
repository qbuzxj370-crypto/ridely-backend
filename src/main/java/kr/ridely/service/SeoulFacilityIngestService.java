package kr.ridely.service;

import kr.ridely.dto.poi.SeoulFacilityIngestResultDTO;

/**
 * 서울시 자전거 편의시설 적재 (공기주입기 · 수리센터).
 *
 * 소스: 서울 열린데이터광장 tvBicycleEtc, 3,374건.
 * 한 응답에 거치대·공기주입기·수리센터가 섞여 있고 종류 필드가 없어
 * {@code SeoulFacilityClassifier}로 판정해 두 테이블로 나눠 넣는다.
 *
 * 거치대·보관대(약 1,910건)는 적재하지 않는다 —
 * bike_parking 소스는 행안부 자전거보관소 API로 확정돼 있다.
 */
public interface SeoulFacilityIngestService {

    /**
     * 편의시설을 수집해 공기주입기는 route_facility에, 수리센터는 repair_shop에 적재한다.
     * 두 테이블 모두 자연키 UNIQUE가 있어 여러 번 실행해도 쌓이지 않는다.
     */
    SeoulFacilityIngestResultDTO ingestSeoulFacilities();
}
