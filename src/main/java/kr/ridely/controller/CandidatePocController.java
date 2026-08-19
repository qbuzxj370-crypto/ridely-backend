package kr.ridely.controller;

import kr.ridely.common.ApiResponse;
import kr.ridely.dto.route.RouteCandidatesDTO;
import kr.ridely.service.InfraCandidateCollector;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ★ 임시 PoC — 후보 수집 검증용.
 *
 *  GET /api/v1/poc/candidates?startLng=126.8997&startLat=37.5434&endLng=126.9339&endLat=37.5265
 *  GET /api/v1/poc/candidates?startLng=126.8997&startLat=37.5434&targetDistanceKm=15   (순환)
 *
 * 확인할 것
 *   1. corridor 쿼리가 도는가. PoiSpatialDao·TourSpatialDao의 CTE가 처음 실행되는 지점이다
 *   2. 타입별로 후보가 고르게 잡히는가. 한 종류가 0건이면 데이터나 반경을 의심한다
 *   3. distanceM이 축 기준으로 나오는가. 출발점 기준이면 도착지 쪽 후보가 크게 나온다
 *   4. 순환 코스에서 반경이 목표 거리에 따라 커지는가
 *
 * 오케스트레이터(RouteRecommendService)가 붙으면 이 컨트롤러는 삭제한다.
 */
@RestController
@RequestMapping("/api/v1/poc/candidates")
public class CandidatePocController {

    /** 검증 기본값 — 선유도공원. ORS PoC와 같은 출발지를 쓴다 */
    private static final double DEFAULT_START_LNG = 126.8997;
    private static final double DEFAULT_START_LAT = 37.5434;

    /** 순환 코스일 때만 쓰이는 값이라 기본값을 둔다 */
    private static final double DEFAULT_TARGET_DISTANCE_KM = 12.0;

    private final InfraCandidateCollector infraCandidateCollector;

    public CandidatePocController(InfraCandidateCollector infraCandidateCollector) {
        this.infraCandidateCollector = infraCandidateCollector;
    }

    /**
     * 후보를 모아 그대로 돌려준다.
     *
     * @param endLng           생략하면 순환 코스로 판정해 반경 기반으로 수집한다
     * @param endLat           생략하면 위와 같다
     * @param targetDistanceKm 순환 코스의 검색 반경 유도에만 쓴다
     */
    @GetMapping
    public ApiResponse<RouteCandidatesDTO> collect(
            @RequestParam(defaultValue = "" + DEFAULT_START_LNG) double startLng,
            @RequestParam(defaultValue = "" + DEFAULT_START_LAT) double startLat,
            @RequestParam(required = false) Double endLng,
            @RequestParam(required = false) Double endLat,
            @RequestParam(defaultValue = "" + DEFAULT_TARGET_DISTANCE_KM) double targetDistanceKm) {

        return ApiResponse.ok(infraCandidateCollector.collect(
                startLng, startLat, endLng, endLat, targetDistanceKm));
    }
}
