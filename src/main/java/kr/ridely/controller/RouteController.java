package kr.ridely.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.ridely.common.ApiResponse;
import kr.ridely.dto.route.RouteRecommendRequestDTO;
import kr.ridely.dto.route.RouteRecommendResponseDTO;
import kr.ridely.service.RouteRecommendService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 코스 추천 컨트롤러.
 *
 * 로그인하지 않아도 추천을 받을 수 있다. 회원이면 결과에 회원 번호가 붙어 나중에 이력으로 쓰인다. 비회원은 user_id가 NULL로 저장되고, 저장 자체는 하므로 번호로 다시 열어볼 수 있다.
 *
 * ⚠️ 응답까지 수 초에서 십수 초가 걸린다. LLM을 두 번, 라우팅 엔진을 한 번 부르기 때문이다. 클라이언트는 그동안 진행 상태를 보여줘야 한다.
 */
@Tag(name = "코스 추천", description = "AI 코스 설계 · 재조회")
@RestController
@RequestMapping("/api/v1/routes")
@RequiredArgsConstructor
public class RouteController {

    private final RouteRecommendService routeRecommendService;

    @Operation(summary = "AI 코스 추천",
            description = """
                    출발지와 목표 거리로 자전거 코스를 설계한다. 도착지를 생략하면 출발지로 되돌아오는 순환 코스가 된다.

                    경유지는 AI가 고르고 실제 경로는 라우팅 엔진이 그린다. AI가 좌표를 만들지 않으므로 강 위를 지나는 경로는 나오지 않는다.

                    사고다발지역을 지나면 응답의 passingDangerZones에 담기고 코치 코멘트가 짚어 준다.

                    회피를 켜면 위험도가 높은 구역을 지나지 않는 경로를 그린다. 회원은 설정값을, 비회원은 X-Ridely-Avoid-Danger-Zones 헤더를 따른다.

                    ⚠️ **avoidDangerZonesApplied가 true여도 passingDangerZones는 비지 않는다.** 회피는 등급을 가려서 하고 대상에서 뺀 등급은 그대로 지나간다. 주의 등급까지 전부 피하면 우회가 커져 목표 거리를 크게 넘기기 때문이다. 화면에 "안전 경로"라고 단정하지 말고 지나는 구역을 함께 보여 줘야 한다.

                    회피가 켜져 있어도 피해 가는 경로를 찾지 못하면 회피 없이 그리고 avoidDangerZonesApplied를 false로 둔다.

                    우선순위 셋의 합은 정확히 1이어야 한다. 오차를 허용하지 않으므로 슬라이더 셋을 쓰는 화면은 마지막 값을 `1 - a - b`로 계산해 보낸다. 3등분이면 0.33/0.33/0.34다. 같은 규칙이 `/users/me/settings`의 기본 가중치에도 적용된다.

                    - 우선순위 합이 1이 아님: ROUTE-001
                    - 목표 거리가 직선거리보다 짧음: ROUTE-002
                    - 서비스 지역(한강 서울 구간) 밖: ROUTE-003
                    """)
    @PostMapping("/recommend")
    public ApiResponse<RouteRecommendResponseDTO> recommend(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Parameter(description = "비회원 사고다발지 회피 요청. 회원은 저장된 설정을 따르므로 무시된다")
            @RequestHeader(value = "X-Ridely-Avoid-Danger-Zones", required = false) Boolean avoidHeader,
            @Valid @RequestBody RouteRecommendRequestDTO request) {
        return ApiResponse.ok(routeRecommendService.recommend(request, userId, avoidHeader));
    }

    @Operation(summary = "추천 코스 다시 보기",
            description = """
                    이전에 받은 추천을 번호로 다시 읽는다. AI를 다시 부르지 않으므로 즉시 응답한다.

                    - 없는 번호: COMMON-004
                    """)
    @GetMapping("/{recommendedRouteId}")
    public ApiResponse<RouteRecommendResponseDTO> findById(
            @PathVariable long recommendedRouteId) {
        return ApiResponse.ok(routeRecommendService.findById(recommendedRouteId));
    }
}
