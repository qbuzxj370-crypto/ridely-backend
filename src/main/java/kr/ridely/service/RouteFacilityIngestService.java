package kr.ridely.service;

import kr.ridely.dto.poi.RouteFacilityIngestResultDTO;

/**
 * 자전거길 주변시설 적재 (인증센터·화장실·급수대·공기주입기).
 *
 * 원본이 API가 아니라 CSV 파일이라 배치 스케줄이 아니라 수동 실행을 전제로 한다.
 * 소스가 "수시(1회성 데이터)"라 갱신이 거의 없기 때문이기도 하다.
 */
public interface RouteFacilityIngestService {

    /**
     * db/seed/의 주변시설 CSV를 읽어 route_facility에 적재하고,
     * 각 시설을 가장 가까운 국토종주 노선에 연결한다.
     *
     * <p>자연키(종류·이름·좌표) 기준으로 중복을 건너뛰므로 여러 번 실행해도 쌓이지 않는다.
     * <p>연결을 채우려면 national_bike_route가 <b>먼저</b> 적재돼 있어야 한다.
     */
    RouteFacilityIngestResultDTO ingestRouteFacilities();
}
