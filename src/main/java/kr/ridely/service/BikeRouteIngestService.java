package kr.ridely.service;

import kr.ridely.dto.poi.BikeRouteIngestResultDTO;

/**
 * 국토종주 자전거길 노선 적재.
 *
 * 원본이 API가 아니라 CSV 파일이라 배치 스케줄이 아니라 수동 실행을 전제로 한다.
 * 소스가 "수시(1회성 데이터)"라 갱신이 거의 없기 때문이기도 하다.
 */
public interface BikeRouteIngestService {

    /**
     * db/seed/의 노선 좌표 CSV를 읽어 national_bike_route에 적재한다.
     * 노선명이 이미 있으면 갱신하므로 여러 번 실행해도 중복이 쌓이지 않는다.
     */
    BikeRouteIngestResultDTO ingestNationalRoutes();
}
