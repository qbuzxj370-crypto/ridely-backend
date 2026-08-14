package kr.ridely.controller;

import kr.ridely.common.ApiResponse;
import kr.ridely.dto.route.CoachCommentDTO;
import kr.ridely.dto.route.CourseDesignDTO;
import kr.ridely.dto.route.RouteCandidatesDTO;
import kr.ridely.infra.llm.CoachCommentClient;
import kr.ridely.infra.llm.CourseDesignClient;
import kr.ridely.service.InfraCandidateCollector;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * ★ 임시 PoC — LLM 구조화 출력 검증용.
 *
 *  GET /api/v1/poc/course/design?endLng=126.9339&endLat=37.5265
 *  GET /api/v1/poc/course/comment?endLng=126.9339&endLat=37.5265
 *
 * ORS는 부르지 않는다. 파이프라인에서 가장 불안정한 것이 구조화 출력 파싱이라 그것만 떼어 확인한다. 경로 좌표가 없어도 설계와 코멘트는 만들어진다.
 *
 * 확인할 것
 *   1. CourseDesignDTO가 예외 없이 파싱되는가. 여기서 깨지면 스키마를 더 얕게 줄여야 한다
 *   2. selectedWaypoints의 id가 실재하는가. 후보 목록에 없는 번호가 나오면 재호출 루프가 필요하다
 *   3. 경유지가 진행 거리 순으로 나오는가. 뒤섞이면 프롬프트의 순서 지시가 약한 것이다
 *   4. coachComment가 트레이너 톤인가. "드립니다"·"하시기 바랍니다"가 나오면 톤 검증기가 필요하다는 뜻이다
 *
 * ⚠️ 호출마다 LLM을 부른다. Gemini 무료 티어 한도가 있으니 반복 호출을 자제한다.
 *
 * ※ PoC 컨트롤러 위치 — 외부 API를 검증하면 infra/{대상}에, 우리 계층(dao·service)을 검증하면 controller/에 둔다. 여기서 검증하는 것은 우리 프롬프트와 DTO 스키마다.
 *
 * 오케스트레이터(RouteRecommendService)가 붙으면 이 컨트롤러는 삭제한다.
 */
@RestController
@RequestMapping("/api/v1/poc/course")
public class CourseDesignPocController {

    /** 검증 기본값 — 선유도공원. 앞선 PoC들과 같은 출발지를 쓴다 */
    private static final double DEFAULT_START_LNG = 126.8997;
    private static final double DEFAULT_START_LAT = 37.5434;
    private static final double DEFAULT_TARGET_DISTANCE_KM = 12.0;

    /** 우선순위 기본값. 합이 1이어야 한다 */
    private static final String DEFAULT_CONVENIENCE = "0.50";
    private static final String DEFAULT_EXERCISE = "0.30";
    private static final String DEFAULT_SCENERY = "0.20";

    /** 코멘트 검증용 가짜 경로 결과. ORS를 부르지 않으므로 그럴듯한 값을 넣는다 */
    private static final double STUB_TOTAL_DISTANCE_KM = 12.4;
    private static final int STUB_DURATION_MIN = 48;
    private static final String STUB_INTENSITY_LEVEL = "MODERATE";

    private final InfraCandidateCollector infraCandidateCollector;
    private final CourseDesignClient courseDesignClient;
    private final CoachCommentClient coachCommentClient;

    public CourseDesignPocController(InfraCandidateCollector infraCandidateCollector,
                                     CourseDesignClient courseDesignClient,
                                     CoachCommentClient coachCommentClient) {
        this.infraCandidateCollector = infraCandidateCollector;
        this.courseDesignClient = courseDesignClient;
        this.coachCommentClient = coachCommentClient;
    }

    /** 후보 수집 후 설계 호출까지. 프롬프트 원문은 서버 로그(DEBUG)에 찍힌다 */
    @GetMapping("/design")
    public ApiResponse<CourseDesignDTO> design(
            @RequestParam(defaultValue = "" + DEFAULT_START_LNG) double startLng,
            @RequestParam(defaultValue = "" + DEFAULT_START_LAT) double startLat,
            @RequestParam(required = false) Double endLng,
            @RequestParam(required = false) Double endLat,
            @RequestParam(defaultValue = "" + DEFAULT_TARGET_DISTANCE_KM) double targetDistanceKm) {

        return ApiResponse.ok(runDesign(startLng, startLat, endLng, endLat, targetDistanceKm).design());
    }

    /**
     * 설계에 이어 코멘트까지. 경로 수치는 가짜 값을 쓴다.
     *
     * 코멘트 프롬프트가 실제 거리와 목표 거리의 차이를 짚게 돼 있어, 가짜 값(12.4km vs 목표 12km)으로도 그 동작을 볼 수 있다.
     */
    @GetMapping("/comment")
    public ApiResponse<CoachCommentDTO> comment(
            @RequestParam(defaultValue = "" + DEFAULT_START_LNG) double startLng,
            @RequestParam(defaultValue = "" + DEFAULT_START_LAT) double startLat,
            @RequestParam(required = false) Double endLng,
            @RequestParam(required = false) Double endLat,
            @RequestParam(defaultValue = "" + DEFAULT_TARGET_DISTANCE_KM) double targetDistanceKm) {

        DesignResult result = runDesign(startLng, startLat, endLng, endLat, targetDistanceKm);
        return ApiResponse.ok(coachCommentClient.generate(
                result.design(), result.candidates(), targetDistanceKm,
                STUB_TOTAL_DISTANCE_KM, STUB_DURATION_MIN, STUB_INTENSITY_LEVEL));
    }

    private DesignResult runDesign(double startLng, double startLat,
                                   Double endLng, Double endLat, double targetDistanceKm) {
        RouteCandidatesDTO candidates = infraCandidateCollector.collect(
                startLng, startLat, endLng, endLat, targetDistanceKm);

        boolean circular = endLng == null || endLat == null;
        CourseDesignDTO design = courseDesignClient.design(
                candidates, targetDistanceKm, circular,
                new BigDecimal(DEFAULT_CONVENIENCE),
                new BigDecimal(DEFAULT_EXERCISE),
                new BigDecimal(DEFAULT_SCENERY));

        return new DesignResult(candidates, design);
    }

    /** 두 값을 함께 넘기기 위한 임시 묶음. 이 컨트롤러와 함께 삭제된다 */
    private record DesignResult(RouteCandidatesDTO candidates, CourseDesignDTO design) {
    }
}
