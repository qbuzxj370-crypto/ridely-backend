package kr.ridely.service;

import kr.ridely.dto.poi.AccidentZoneIngestResultDTO;

/**
 * 자전거 사고다발지역 적재.
 *
 * 소스: 도로교통공단 TAAS (data.go.kr/data/15056681), 갱신주기 연 1회.
 * 선정 기준은 반경 200m 내 자전거사고 4건 이상(사망사고 포함 시 3건 이상)이다.
 *
 * {@code guGun}이 필수 파라미터라 서울 25개 자치구를 각각 호출한다.
 *
 * ⚠️ 데이터가 희소하다. 2024년 서울 전역 111건이고 25개 구 중 6개는 사고다발지가
 * 아예 없다. 도심 교차로에 몰려 있어 한강 자전거도로에는 거의 없다 —
 * 적재 건수가 적은 것은 실패가 아니라 데이터 특성이다.
 */
public interface AccidentZoneIngestService {

    /**
     * 서울 사고다발지역을 수집해 accident_zone에 적재한다.
     * (afos_fid, data_year) 기준 UPSERT라 여러 번 실행해도 쌓이지 않는다.
     *
     * @param year 사고년도. null이면 설정값(활용가이드 3.1 기준 최신)
     */
    AccidentZoneIngestResultDTO ingestAccidentZones(Integer year);
}
