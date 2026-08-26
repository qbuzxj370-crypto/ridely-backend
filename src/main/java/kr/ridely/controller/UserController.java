package kr.ridely.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.ridely.common.ApiResponse;
import kr.ridely.dto.user.UserResponseDTO;
import kr.ridely.dto.user.UserSettingsDTO;
import kr.ridely.dto.user.UserUpdateRequestDTO;
import kr.ridely.service.UserService;
import kr.ridely.service.UserSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 정보 컨트롤러.
 *
 * 회원 번호는 경로나 파라미터로 받지 않고 토큰에서 꺼낸다.
 * 클라이언트가 보낸 값을 쓰면 번호만 바꿔 남의 정보에 접근할 수 있다.
 * JwtAuthenticationFilter가 인증 정보에 회원 번호를 넣어 두므로
 * @AuthenticationPrincipal 로 바로 받는다.
 */
@Tag(name = "회원", description = "내 정보 조회 · 수정 (인증 필요)")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserSettingsService userSettingsService;

    @Operation(summary = "내 정보 조회",
            description = """
                    토큰의 회원 정보를 반환한다. 회원 번호를 파라미터로 받지 않으므로
                    다른 회원의 정보는 조회할 수 없다.

                    - 토큰 없음·위조: COMMON-002
                    - 토큰 만료: AUTH-301 (재발급 후 재시도)
                    """)
    @GetMapping("/me")
    public ApiResponse<UserResponseDTO> me(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(userService.findById(userId));
    }

    @Operation(summary = "내 정보 수정",
            description = """
                    보낸 항목만 변경된다. 예를 들어 `{"nickname": "새닉네임"}`만 보내면
                    이메일은 기존 값이 유지된다.

                    아이디는 변경할 수 없고, 비밀번호는 현재 비밀번호 확인이 필요해 별도 API로 분리 예정이다.

                    - 형식 오류(닉네임 길이·이메일 형식): COMMON-001
                    """)
    @PatchMapping("/me")
    public ApiResponse<UserResponseDTO> updateMe(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UserUpdateRequestDTO request) {
        return ApiResponse.ok(userService.updateProfile(userId, request));
    }

    @Operation(summary = "내 설정 조회",
            description = """
                    회피 여부, 코스 추천 기본 가중치, 표시 단위, 알림·진동 설정을 반환한다.

                    설정 행은 가입할 때 기본값으로 만들어진다. 없다면 데이터가 어긋난 것이므로 COMMON-004다.
                    """)
    @GetMapping("/me/settings")
    public ApiResponse<UserSettingsDTO> mySettings(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(userSettingsService.findByUserId(userId));
    }

    @Operation(summary = "내 설정 수정",
            description = """
                    보낸 항목만 변경된다. 예를 들어 `{"avoidDangerZones": true}`만 보내면 나머지 설정은 그대로다.
                    응답은 갱신된 전체 설정이라 다른 항목을 다시 조회하지 않아도 된다.

                    **가중치 셋은 묶음이다.** 하나만 바꾸면 나머지는 기존 값이 남아 합이 1을 벗어난다.
                    셋 중 하나라도 보내면 셋 다 보내야 하고 합이 정확히 1이어야 한다. 아니면 ROUTE-001이다.
                    오차를 허용하지 않으므로 슬라이더 셋을 쓰는 화면은 마지막 값을 `1 - a - b`로 계산해 보낸다.
                    셋을 3등분하면 0.33/0.33/0.33(합 0.99)이 아니라 0.33/0.33/0.34가 된다.
                    같은 규칙이 코스 추천 요청에도 적용된다.

                    `avoidDangerZones`를 켜면 이후 코스 추천이 사고다발지를 피해 그린다.
                    ⚠️ **모든 사고다발지를 피하는 것은 아니다.** 대상은 위험·경고 등급이고 주의 등급은 그대로 지나간다.
                    주의 등급까지 피하면 우회가 커져 목표 거리를 크게 넘긴다. 대상 등급은 서버 설정이라 사용자가 고를 수 없다.
                    화면에 "안전 경로"라고 단정하지 말고 추천 응답의 passingDangerZones를 함께 보여 줘야 한다.

                    `units`는 저장만 하고 응답 거리는 항상 km다. 표시 변환은 화면에서 한다.

                    - 형식 오류(가중치 범위·단위 값): COMMON-001
                    - 가중치가 묶음이 아니거나 합이 1이 아님: ROUTE-001
                    """)
    @PatchMapping("/me/settings")
    public ApiResponse<UserSettingsDTO> updateMySettings(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UserSettingsDTO request) {
        return ApiResponse.ok(userSettingsService.update(userId, request));
    }
}
