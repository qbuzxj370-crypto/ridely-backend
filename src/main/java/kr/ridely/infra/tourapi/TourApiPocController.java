package kr.ridely.infra.tourapi;

import kr.ridely.common.ApiResponse;
import kr.ridely.dto.tour.TourIngestResultDTO;
import kr.ridely.service.TourIngestService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ★ 임시 PoC — 공모전 필수 요건(TourAPI 활용) 검증 + 수동 적재 트리거.
 *
 * GET  /api/v1/poc/tour         단건 호출 확인 (응답 원문)
 * POST /api/v1/poc/tour/ingest  한강 구간 적재 실행
 *
 * 적재는 원래 스케줄러가 주기적으로 돌려야 하지만(기획 §배치 스케줄),
 * 1주차에는 스케줄러 대신 이 엔드포인트로 수동 실행한다.
 * 스케줄러 도입 시 이 컨트롤러는 삭제하고 서비스만 남긴다.
 *
 * ⚠️ 적재는 TourAPI를 수십 회 호출한다(지점 수 × 콘텐츠 타입 수 × 페이지).
 *    개발계정 한도는 오퍼레이션당 일 1,000건이므로 반복 실행에 주의한다.
 */
@RestController
@RequestMapping("/api/v1/poc/tour")
public class TourApiPocController {

    private final TourApiClient tourApiClient;
    private final TourIngestService tourIngestService;

    public TourApiPocController(TourApiClient tourApiClient, TourIngestService tourIngestService) {
        this.tourApiClient = tourApiClient;
        this.tourIngestService = tourIngestService;
    }

    /**
     * 단건 호출 확인 (SPRINT_W1 C6 검증 기준).
     * 선유도공원 좌표(126.8997, 37.5434) 반경 2km, 관광지(12) → items 1건 이상이면 성공.
     */
    @GetMapping
    public ApiResponse<String> tourPoc() {
        String rawJson = tourApiClient.fetchNearbySpots(126.8997, 37.5434, 2000, 12);
        return ApiResponse.ok(rawJson);
    }

    /**
     * 한강 구간 관광 콘텐츠 적재.
     * 이미 있는 콘텐츠는 갱신되므로 여러 번 실행해도 중복이 쌓이지 않는다.
     */
    @PostMapping("/ingest")
    public ApiResponse<TourIngestResultDTO> ingest() {
        return ApiResponse.ok(tourIngestService.ingestHangangArea());
    }
}
