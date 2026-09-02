package kr.ridely.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kr.ridely.common.ApiResponse;
import kr.ridely.common.PageResponse;
import kr.ridely.dto.rideHistory.RidingSessionEndRequestDTO;
import kr.ridely.dto.rideHistory.RidingSessionResponseDTO;
import kr.ridely.dto.rideHistory.RidingSessionStartRequestDTO;
import kr.ridely.service.RidingSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 라이딩 세션 컨트롤러.
 *
 * 회원 번호는 토큰에서 꺼낸다. 세션 번호는 경로 변수로 받을 수밖에 없으므로 서비스가 소유권을 확인한다.
 */
@Tag(name = "라이딩 세션", description = "라이딩 시작 · 종료 · 기록 조회 (인증 필요)")
@RestController
@RequestMapping("/api/v1/riding-sessions")
@RequiredArgsConstructor
@Validated
public class RidingSessionController {

    private final RidingSessionService ridingSessionService;

    @Operation(summary = "라이딩 시작",
            description = """
                    진행 중인 세션을 만든다. 시작 시각은 서버가 찍는다 - 기기 시계가 틀어져 있으면
                    시작보다 이른 종료 시각이 들어가 종료가 막힌다.

                    `recommendedRouteId`를 비우면 추천 없이 달리는 자유 주행으로 기록된다.
                    **본문을 통째로 생략해도 된다.** 자유 주행에서 보낼 값이 없기 때문이다.

                    진행 중인 세션이 이미 있어도 막지 않는다. 앱이 죽었다 살아나면 이전 세션이
                    진행 중으로 남는데, 새 라이딩을 시작하지 못하면 앱을 쓸 수 없게 된다.
                    남은 세션은 목록에서 `endedAt`이 null인 항목으로 찾는다.

                    - 없는 추천 코스 번호: COMMON-004
                    """)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RidingSessionResponseDTO> start(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            // required = false다. 이 DTO는 필수 필드가 없어 자유 주행이면 보낼 값이 아예 없는데,
            // 기본값(필수)이면 본문 없는 요청이 HttpMessageNotReadableException으로 떨어진다
            @Valid @RequestBody(required = false) RidingSessionStartRequestDTO request) {
        return ApiResponse.ok(ridingSessionService.start(userId, request));
    }

    @Operation(summary = "라이딩 종료",
            description = """
                    측정값과 GPS 트랙을 한 번에 받아 세션을 마무리한다. 종료 시각은 서버가 찍는다.

                    `trackGeoJson`은 GeoJSON LineString 문자열이다. 매 좌표를 다 보내지 말고
                    앱에서 솎아 보낸다(예: 10초 또는 50m 간격). 고도가 섞여 있어도 서버가 떨군다.

                    **이미 끝난 세션에 다시 보내면 에러가 아니라 기존 기록이 그대로 돌아온다.**
                    모바일에서 종료 요청이 타임아웃돼 재시도하는 경우가 흔한데, 그때 에러를 내면
                    실제로는 저장된 기록을 두고 실패 화면이 뜬다. 값이 달라도 덮어쓰지 않는다.

                    - 없는 번호: COMMON-004
                    - 남의 기록: COMMON-003
                    - GPS 트랙이 GeoJSON LineString이 아님: COMMON-001
                    """)
    @PatchMapping("/{ridingSessionId}")
    public ApiResponse<RidingSessionResponseDTO> end(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable long ridingSessionId,
            @Valid @RequestBody RidingSessionEndRequestDTO request) {
        return ApiResponse.ok(ridingSessionService.end(userId, ridingSessionId, request));
    }

    @Operation(summary = "라이딩 기록 단건",
            description = """
                    기록 하나를 반환한다. **`trackGeoJson`이 채워지는 유일한 응답이다.**
                    기록 상세 화면에서 지난 주행 궤적을 지도에 그리는 데 쓴다.

                    트랙을 보내지 않고 종료한 세션이면 `trackGeoJson`이 null이다.
                    좌표는 2차원이다. 저장할 때 고도를 떨궜다.

                    - 없는 번호: COMMON-004
                    - 남의 기록: COMMON-003
                    """)
    @GetMapping("/{ridingSessionId}")
    public ApiResponse<RidingSessionResponseDTO> findOne(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable long ridingSessionId) {
        return ApiResponse.ok(ridingSessionService.findById(userId, ridingSessionId));
    }

    @Operation(summary = "내 라이딩 기록 목록",
            description = """
                    최근 시작한 순으로 반환한다. 진행 중인 세션(`endedAt`이 null)도 함께 나온다.

                    **GPS 트랙은 담기지 않는다.** 트랙 하나가 좌표 수백~수천 개라 한 페이지 응답이
                    수 MB가 된다. 궤적이 필요하면 단건 조회를 쓴다.
                    """)
    @GetMapping
    public ApiResponse<PageResponse<RidingSessionResponseDTO>> list(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,

            @Parameter(description = "0부터 시작")
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다") int page,

            @Parameter(description = "페이지당 항목 수. 최대 100")
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다")
            @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다") int size) {
        return ApiResponse.ok(ridingSessionService.findByUserId(userId, page, size));
    }
}
