package kr.ridely.infra.tourapi;

import kr.ridely.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ★ 임시 PoC — 공모전 필수 요건(TourAPI 활용) 검증용.
 * GET /api/v1/poc/tour
 *
 * 검증 기준 (SPRINT_W1 C6):
 *   선유도공원 좌표(126.8997, 37.5434) 반경 2km, contentTypeId=12
 *   → items 1건 이상 수신하면 성공
 *
 * 검증 완료 후: 이 컨트롤러는 삭제하고 TourApiClient를
 * 정식 배치(tour_spot 적재)·서비스에서 사용하는 형태로 발전.
 */
@RestController
@RequestMapping("/api/v1/poc/tour")
public class TourApiPocController {

    private final TourApiClient tourApiClient;

    public TourApiPocController(TourApiClient tourApiClient) {
        this.tourApiClient = tourApiClient;
    }

    @GetMapping
    public ApiResponse<String> tourPoc() {
        // 선유도공원 좌표, 반경 2km, 관광지(12)
        String rawJson = tourApiClient.fetchNearbySpots(126.8997, 37.5434, 2000, 12);
        return ApiResponse.ok(rawJson);
    }
}
