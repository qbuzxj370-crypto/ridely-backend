package kr.ridely.infra.durunubi;

import kr.ridely.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ★ 임시 스파이크 — 두루누비 채택 판정용. 판정이 끝나면 이 패키지를 통째로 삭제한다.
 *
 * <pre>
 *  GET /api/v1/poc/durunubi/raw?op=routeList    응답 원문 (키·응답 형태 확인)
 *  GET /api/v1/poc/durunubi/coverage            S1 — 국토종주 13길 존재 여부  ← 중단 게이트
 *  GET /api/v1/poc/durunubi/geometry            S2 — GPX trkseg vs 우리 휴리스틱 대조
 * </pre>
 *
 * <p>실행 순서를 지킬 것. raw로 키가 통하는지 먼저 보고, coverage가 0이면
 * geometry는 돌리지 않는다 (durunubi.kr에서 GPX를 받는 호출이라 헛되이 부하만 준다).
 *
 * <p>/api/v1/poc/** 는 SecurityConfig에서 이미 permitAll이라 토큰 없이 호출된다.
 */
@RestController
@RequestMapping("/api/v1/poc/durunubi")
public class DurunubiPocController {

    private final DurunubiClient client;
    private final DurunubiSpikeService spikeService;

    public DurunubiPocController(DurunubiClient client, DurunubiSpikeService spikeService) {
        this.client = client;
        this.spikeService = spikeService;
    }

    /**
     * 응답 원문 확인 — 가장 먼저 실행할 것.
     *
     * <p>여기서 COMMON-500이 나면 로그에 포털 오류 원문이 찍힌다.
     * SERVICE_KEY_IS_NOT_REGISTERED_ERROR면 두루누비 활용신청이 아직 승인되지 않은 것이다
     * (자동승인이지만 포털↔관광공사 동기화에 30분 걸린다).
     *
     * <p>진단 순서: brdDiv 없이 → DNBW → DNWW 순으로 비교하면
     * "데이터가 없는 것"인지 "필터가 안 맞는 것"인지 갈린다.
     *
     * @param op       routeList | courseList
     * @param routeIdx courseList일 때 지정
     * @param brdDiv   DNBW(자전거)|DNWW(걷기). 생략하면 파라미터를 보내지 않는다(=전체)
     */
    @GetMapping("/raw")
    public ApiResponse<String> raw(@RequestParam(defaultValue = "routeList") String op,
                                   @RequestParam(required = false) String routeIdx,
                                   @RequestParam(required = false) String brdDiv,
                                   @RequestParam(defaultValue = "10") int numOfRows) {
        return ApiResponse.ok(client.fetchRaw(op, routeIdx, brdDiv, numOfRows));
    }

    /**
     * S1 — 커버리지. <b>스파이크의 중단 게이트다.</b>
     * matchedCount가 0이면 여기서 멈추고 기존안으로 복귀한다.
     */
    @GetMapping("/coverage")
    public ApiResponse<DurunubiCoverageReport> coverage() {
        return ApiResponse.ok(spikeService.checkCoverage());
    }

    /**
     * S2 — GPX 형상 분석.
     *
     * @param routeCode 특정 노선만 볼 때 지정(1~13). MVP 구간만 빠르게 보려면 2(한강종주).
     *                  생략하면 매칭된 전체를 돌지만 GPX 다운로드 상한에 걸릴 수 있다
     */
    @GetMapping("/geometry")
    public ApiResponse<DurunubiGeometryReport> geometry(
            @RequestParam(required = false) Integer routeCode) {
        return ApiResponse.ok(spikeService.analyzeGeometry(routeCode));
    }
}
