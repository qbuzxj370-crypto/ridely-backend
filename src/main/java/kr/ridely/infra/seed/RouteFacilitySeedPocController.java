package kr.ridely.infra.seed;

import kr.ridely.common.ApiResponse;
import kr.ridely.dto.poi.RouteFacilityIngestResultDTO;
import kr.ridely.service.RouteFacilityIngestService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ★ 임시 PoC — 자전거길 주변시설 수동 적재 트리거.
 *
 * POST /api/v1/poc/seed/route-facilities  인증센터·화장실·급수대·공기주입기 적재
 *
 * <p>⚠️ <b>노선 적재를 먼저 실행해야 한다</b>(POST /api/v1/poc/seed/bike-routes).
 * 원본 CSV에 노선 코드가 없어 좌표로 노선을 찾는데, 노선이 비어 있으면
 * 시설은 들어가되 national_bike_route_id가 전부 NULL로 남는다.
 * 순서를 놓쳤으면 노선 적재 후 이 엔드포인트를 다시 부르면 연결만 다시 계산된다.
 *
 * <p>파일이 db/seed/에 없으면 실패한다. 배치 방법은 docs/shared/DATA_SOURCES.md 참조.
 */
@RestController
@RequestMapping("/api/v1/poc/seed")
public class RouteFacilitySeedPocController {

    private final RouteFacilityIngestService routeFacilityIngestService;

    public RouteFacilitySeedPocController(RouteFacilityIngestService routeFacilityIngestService) {
        this.routeFacilityIngestService = routeFacilityIngestService;
    }

    /**
     * 주변시설을 적재하고 가장 가까운 노선에 연결한다.
     * 자연키(종류·이름·좌표) 기준으로 중복을 건너뛰므로 여러 번 실행해도 쌓이지 않는다.
     */
    @PostMapping("/route-facilities")
    public ApiResponse<RouteFacilityIngestResultDTO> ingestRouteFacilities() {
        return ApiResponse.ok(routeFacilityIngestService.ingestRouteFacilities());
    }
}
