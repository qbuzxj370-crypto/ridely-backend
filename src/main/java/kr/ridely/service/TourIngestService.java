package kr.ridely.service;

import kr.ridely.dto.tour.TourIngestResultDTO;

/**
 * TourAPI 관광 콘텐츠 적재 서비스.
 *
 * 개발계정 트래픽이 오퍼레이션당 일 1,000건이라 실시간 호출은 불가능하다.
 * 배치로 DB에 적재하고, 조회는 PostGIS 공간 쿼리로 처리한다.
 */
public interface TourIngestService {

    /**
     * MVP 대상 구간(한강 서울 구간)의 관광 콘텐츠를 적재한다.
     * 이미 있는 콘텐츠는 최신 값으로 갱신되므로 여러 번 실행해도 안전하다.
     */
    TourIngestResultDTO ingestHangangArea();
}
