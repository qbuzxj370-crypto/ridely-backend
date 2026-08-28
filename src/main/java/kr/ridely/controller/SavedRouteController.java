package kr.ridely.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import kr.ridely.common.ApiResponse;
import kr.ridely.common.PageResponse;
import kr.ridely.dto.savedRoute.SavedRouteCreateRequestDTO;
import kr.ridely.dto.savedRoute.SavedRouteResponseDTO;
import kr.ridely.dto.savedRoute.SavedRouteUpdateRequestDTO;
import kr.ridely.service.SavedRouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * 저장 경로 컨트롤러.
 *
 * 회원 번호는 경로나 파라미터로 받지 않고 토큰에서 꺼낸다. UserController와 같은 이유다 -
 * 클라이언트가 보낸 값을 쓰면 번호만 바꿔 남의 목록에 접근할 수 있다.
 *
 * 저장 경로 번호(savedRouteId)는 경로 변수로 받을 수밖에 없으므로 서비스가 소유권을 확인한다.
 */
@Tag(name = "저장 경로", description = "추천받은 코스를 내 목록에 담기 (인증 필요)")
@RestController
@RequestMapping("/api/v1/saved-routes")
@RequiredArgsConstructor
@Validated
public class SavedRouteController {

    private final SavedRouteService savedRouteService;

    @Operation(summary = "코스 저장",
            description = """
                    추천받은 코스를 내 목록에 담는다. 코스 내용은 이미 저장돼 있으므로
                    여기서는 추천 코스 번호와 내 메모만 보낸다.

                    즐겨찾기는 저장할 때 켤 수 없다. 저장 후 PATCH로 켠다.

                    - 같은 코스를 두 번 저장: SAVED-001 (409)
                    - 없는 추천 코스 번호: COMMON-004
                    - 이름 100자 초과: COMMON-001
                    """)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SavedRouteResponseDTO> save(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Valid @RequestBody SavedRouteCreateRequestDTO request) {
        return ApiResponse.ok(savedRouteService.save(userId, request));
    }

    @Operation(summary = "내 저장 목록",
            description = """
                    내가 저장한 코스를 페이지로 반환한다. 각 항목에 코스 요약(제목·거리·시간·강도)이
                    함께 담기므로 목록을 그리려고 코스를 하나씩 다시 조회하지 않아도 된다.

                    - `sort=latest` (기본): 저장한 순서의 역순
                    - `sort=name`: 화면에 보이는 이름 오름차순. 이름을 안 붙인 항목은 AI 제목으로 정렬한다
                    - `favoriteOnly=true`: 즐겨찾기만
                    """)
    @GetMapping
    public ApiResponse<PageResponse<SavedRouteResponseDTO>> list(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,

            @Parameter(description = "0부터 시작")
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다") int page,

            @Parameter(description = "페이지당 항목 수. 최대 100")
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다")
            @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다") int size,

            @Parameter(description = "true면 즐겨찾기만")
            @RequestParam(required = false) Boolean favoriteOnly,

            @Parameter(description = "latest 또는 name")
            @RequestParam(defaultValue = "latest")
            @Pattern(regexp = "latest|name", message = "정렬은 latest 또는 name만 가능합니다")
            String sort) {
        return ApiResponse.ok(savedRouteService.findByUserId(userId, favoriteOnly, sort, page, size));
    }

    @Operation(summary = "저장 코스 단건 조회",
            description = """
                    저장 정보와 코스 요약을 반환한다.

                    **경로 좌표는 담기지 않는다.** 지도를 그리려면 응답의 `recommendedRouteId`로
                    `GET /routes/{id}`를 따로 부른다. 목록 응답과 형태를 같게 두려는 것이고,
                    좌표를 여기 넣으면 목록 한 페이지가 수천 좌표를 실어 나르게 된다.

                    - 없는 번호: COMMON-004
                    - 남의 저장 기록: COMMON-003
                    """)
    @GetMapping("/{savedRouteId}")
    public ApiResponse<SavedRouteResponseDTO> findOne(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable long savedRouteId) {
        return ApiResponse.ok(savedRouteService.findById(userId, savedRouteId));
    }

    @Operation(summary = "저장 코스 수정",
            description = """
                    보낸 항목만 변경된다. 응답은 갱신된 전체라 목록을 다시 조회하지 않아도 된다.

                    메모를 지우려면 빈 문자열을 보낸다. null은 "안 보냈다"라는 뜻이라 지우기와 구분되지 않는다.

                    어떤 추천 코스를 가리키는지는 바꿀 수 없다. 다른 코스를 담으려면 새로 저장한다.

                    - 없는 번호: COMMON-004
                    - 남의 저장 기록: COMMON-003
                    """)
    @PatchMapping("/{savedRouteId}")
    public ApiResponse<SavedRouteResponseDTO> update(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable long savedRouteId,
            @Valid @RequestBody SavedRouteUpdateRequestDTO request) {
        return ApiResponse.ok(savedRouteService.update(userId, savedRouteId, request));
    }

    @Operation(summary = "저장 취소",
            description = """
                    내 목록에서 뺀다. 추천 코스 원본은 지워지지 않으므로 같은 코스를 다시 저장할 수 있다.

                    - 없는 번호: COMMON-004
                    - 남의 저장 기록: COMMON-003
                    """)
    @DeleteMapping("/{savedRouteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable long savedRouteId) {
        savedRouteService.delete(userId, savedRouteId);
    }
}
