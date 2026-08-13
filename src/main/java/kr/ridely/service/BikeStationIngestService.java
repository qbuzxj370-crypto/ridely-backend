package kr.ridely.service;

import kr.ridely.dto.poi.BikeStationIngestResultDTO;

/**
 * 따릉이 대여소 적재.
 *
 * 소스: 서울 열린데이터광장 tbCycleStationInfo, 약 3,237건.
 *
 * <p>다른 POI와 달리 <b>운영 중 바뀌는 마스터</b>다. 대여소는 이전·개명·폐쇄가 실제로
 * 일어나므로 재실행 시 갱신하고, 응답에서 사라진 대여소는 비활성으로 돌린다.
 *
 * <p>실시간 잔여 대수는 이 테이블에 넣지 않는다 — 변동이 잦아 서울시 실시간 API를
 * 그때그때 호출한다(schema 주석·DATA_SOURCES 2.7).
 */
public interface BikeStationIngestService {

    /**
     * 대여소를 수집해 bike_station에 반영한다.
     * RENT_ID 기준 UPSERT라 여러 번 실행해도 중복이 쌓이지 않는다.
     */
    BikeStationIngestResultDTO ingestBikeStations();
}
