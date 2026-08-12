package kr.ridely.infra.seed;

import kr.ridely.common.ApiResponse;
import kr.ridely.dto.poi.BikeRouteIngestResultDTO;
import kr.ridely.service.BikeRouteIngestService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ★ 임시 PoC — 파일 기반 공공데이터 수동 적재 트리거.
 *
 * POST /api/v1/poc/seed/bike-routes  국토종주 자전거길 노선 적재
 *
 * 원본이 API가 아니라 CSV라 배치 스케줄을 걸 대상이 아니다.
 * 소스도 "수시(1회성 데이터)"라 갱신이 거의 없어 필요할 때만 수동 실행한다.
 *
 * 파일이 db/seed/에 없으면 실패한다. 배치 방법은 docs/shared/DATA_SOURCES.md 3장 참조.
 */
@RestController
@RequestMapping("/api/v1/poc/seed")
public class BikeRouteSeedPocController {

    private final BikeRouteIngestService bikeRouteIngestService;

    public BikeRouteSeedPocController(BikeRouteIngestService bikeRouteIngestService) {
        this.bikeRouteIngestService = bikeRouteIngestService;
    }

    /**
     * 국토종주 13개 노선을 적재한다.
     * 노선명 기준으로 갱신되므로 여러 번 실행해도 중복이 쌓이지 않는다.
     */
    @PostMapping("/bike-routes")
    public ApiResponse<BikeRouteIngestResultDTO> ingestBikeRoutes() {
        return ApiResponse.ok(bikeRouteIngestService.ingestNationalRoutes());
    }
}
