package kr.ridely.infra.seoul;

import kr.ridely.common.ApiResponse;
import kr.ridely.dto.poi.BikeStationIngestResultDTO;
import kr.ridely.service.BikeStationIngestService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ★ 임시 PoC — 따릉이 대여소 수동 적재 트리거.
 *
 * POST /api/v1/poc/seoul/stations/ingest  대여소 약 3,237건 적재
 *
 * <p>다른 시드와 달리 <b>주기 실행이 필요한 마스터</b>다. 대여소는 이전·개명·폐쇄가
 * 실제로 일어나므로 나중에 스케줄러로 옮긴다. 지금은 수동 실행이다.
 *
 * <p>실시간 잔여 대수는 이 테이블에 넣지 않는다 — 변동이 잦아 조회 시점에
 * 서울시 실시간 API를 직접 호출한다.
 */
@RestController
@RequestMapping("/api/v1/poc/seoul/stations")
public class BikeStationSeedPocController {

    private final BikeStationIngestService bikeStationIngestService;

    public BikeStationSeedPocController(BikeStationIngestService bikeStationIngestService) {
        this.bikeStationIngestService = bikeStationIngestService;
    }

    /**
     * 대여소를 적재한다.
     * RENT_ID 기준 UPSERT이고, 응답에서 사라진 대여소는 비활성으로 돌린다.
     */
    @PostMapping("/ingest")
    public ApiResponse<BikeStationIngestResultDTO> ingest() {
        return ApiResponse.ok(bikeStationIngestService.ingestBikeStations());
    }
}
