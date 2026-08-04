package kr.ridely.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.ridely.common.ApiResponse;
import kr.ridely.dto.user.UserResponseDTO;
import kr.ridely.dto.user.UserUpdateRequestDTO;
import kr.ridely.service.UserService;
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
 * @AuthenticationPrincipal로 바로 받는다.
 */
@Tag(name = "회원", description = "내 정보 조회 · 수정 (인증 필요)")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

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
}
