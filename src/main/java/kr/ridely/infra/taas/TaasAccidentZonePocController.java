package kr.ridely.infra.taas;

import kr.ridely.common.ApiResponse;
import kr.ridely.dto.poi.AccidentZoneIngestResultDTO;
import kr.ridely.service.AccidentZoneIngestService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ★ 임시 PoC — TAAS 사고다발지역 적재.
 *
 *  POST /api/v1/poc/taas/ingest?year=2024
 *  GET  /api/v1/poc/taas/raw?siDo=11&guGun=680&year=2024
 *
 * 확인이 끝난 항목 — docs/shared/DATA_SOURCES.md 2.5
 *   1. {@code guGun}은 필수다. 서울 25개 구를 각각 호출한다 ({@code SeoulDistrict})
 *   2. {@code searchYearCd}는 동작한다. 4자리 연도를 넣으면 그 해 데이터가 온다
 *
 * {@code /raw}는 응답 원문 확인용으로 남겨 뒀다. 정식 API를 붙일 때 이 컨트롤러는 삭제한다.
 *
 * ⚠️ 개발계정은 일 1,000건 한도다. {@code /ingest} 한 번이 25회 이상을 쓴다.
 */
@RestController
@RequestMapping("/api/v1/poc/taas")
public class TaasAccidentZonePocController {

    private final TaasClient taasClient;
    private final AccidentZoneIngestService accidentZoneIngestService;

    public TaasAccidentZonePocController(TaasClient taasClient,
                                         AccidentZoneIngestService accidentZoneIngestService) {
        this.taasClient = taasClient;
        this.accidentZoneIngestService = accidentZoneIngestService;
    }

    /**
     * 서울 사고다발지역을 적재한다.
     *
     * {@code guGun}이 필수라 25개 자치구를 각각 호출한다.
     * (afos_fid, data_year) 기준 UPSERT이므로 여러 번 실행해도 쌓이지 않는다.
     *
     * ⚠️ 응답의 {@code mvpAreaCount}는 경계 상자 안의 건수다. 코스에 실제로 걸리는
     * 건수가 아니다 — 상자가 서울 도심 대부분을 덮는다. 적재 검증용으로만 본다.
     *
     * @param year 사고년도. 생략하면 설정값(활용가이드 3.1 기준 최신)
     */
    @PostMapping("/ingest")
    public ApiResponse<AccidentZoneIngestResultDTO> ingest(
            @RequestParam(required = false) Integer year) {
        return ApiResponse.ok(accidentZoneIngestService.ingestAccidentZones(year));
    }

    /**
     * 응답 원문을 그대로 돌려준다.
     *
     * 실패하면 서버 로그에 포털 오류 원문이 찍힌다.
     * {@code SERVICE_KEY_IS_NOT_REGISTERED_ERROR}면 활용신청이 아직 반영되지 않은 것이다.
     *
     * @param siDo  시도코드. 서울=11. 생략하면 파라미터를 보내지 않는다
     * @param guGun 시군구코드. 강남구=680. 생략하면 결과가 비어 온다
     * @param year  사고년도. 생략하면 설정값(2024)
     */
    @GetMapping("/raw")
    public ApiResponse<String> raw(@RequestParam(required = false) String siDo,
                                   @RequestParam(required = false) String guGun,
                                   @RequestParam(required = false) Integer year,
                                   @RequestParam(defaultValue = "10") int numOfRows,
                                   @RequestParam(defaultValue = "1") int pageNo) {
        return ApiResponse.ok(taasClient.fetchRaw(siDo, guGun, year, numOfRows, pageNo));
    }
}
