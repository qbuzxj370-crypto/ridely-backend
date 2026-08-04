package kr.ridely.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.ridely.common.ApiResponse;
import kr.ridely.dto.auth.LoginRequestDTO;
import kr.ridely.dto.auth.LoginResponseDTO;
import kr.ridely.dto.auth.RefreshTokenRequestDTO;
import kr.ridely.dto.auth.SignupRequestDTO;
import kr.ridely.dto.user.UserResponseDTO;
import kr.ridely.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 컨트롤러.
 *
 * 검증 책임 분리:
 *   - 형식 검증: @Valid → 실패 시 GlobalExceptionHandler가 COMMON-001
 *   - 비밀번호 정책: AuthServiceImpl → AUTH-102
 *   - loginId 중복: AuthServiceImpl → AUTH-101
 *
 * 네 엔드포인트 모두 토큰 없이 호출한다(@SecurityRequirements로 전역 인증 해제).
 * 재발급·로그아웃은 요청 본문의 리프레시 토큰 자체가 자격 증명이며,
 * 액세스 토큰이 만료된 상태에서 호출되는 것이 정상 흐름이다.
 */
@Tag(name = "인증", description = "회원가입 · 로그인 · 토큰 재발급 · 로그아웃")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@SecurityRequirements   // 전역 Bearer 인증 요구를 이 컨트롤러에서 해제
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "회원가입",
            description = """
                    성공 시 201과 생성된 회원 정보를 반환한다. 비밀번호는 응답에 포함되지 않는다.

                    - 비밀번호 정책: 8~30자, 영문·숫자·특수문자 각 1자 이상 (위반 시 AUTH-102)
                    - 이미 있는 아이디: AUTH-101
                    - 형식 오류(빈 값·길이·이메일): COMMON-001, 어느 항목이 왜 틀렸는지 error.details에 담긴다
                    """)
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponseDTO> signup(@Valid @RequestBody SignupRequestDTO request) {
        return ApiResponse.ok(authService.signup(request));
    }

    @Operation(summary = "로그인",
            description = """
                    액세스 토큰(1시간)과 리프레시 토큰(14일)을 발급한다.
                    두 토큰을 저장해 두고, 이후 요청 헤더에 `Authorization: Bearer {accessToken}`을 넣는다.

                    - 아이디가 없거나 비밀번호가 틀림: AUTH-201 (두 경우를 구분하지 않는다)
                    - 탈퇴·정지 계정: AUTH-202
                    """)
    @PostMapping("/login")
    public ApiResponse<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        return ApiResponse.ok(authService.login(request));
    }

    @Operation(summary = "토큰 재발급",
            description = """
                    액세스 토큰이 만료됐을 때(AUTH-301) 호출한다. 응답 형식은 로그인과 같다.

                    ⚠️ 사용한 리프레시 토큰은 즉시 폐기된다. 응답으로 받은 새 토큰으로 반드시 교체해야 하며,
                    이전 토큰을 다시 보내면 AUTH-302가 반환된다.

                    - 만료·위조·이미 폐기된 토큰: AUTH-302 (재로그인 필요)
                    - 액세스 토큰을 넣은 경우도 AUTH-302
                    """)
    @PostMapping("/refresh")
    public ApiResponse<LoginResponseDTO> refresh(@Valid @RequestBody RefreshTokenRequestDTO request) {
        return ApiResponse.ok(authService.refresh(request.getRefreshToken()));
    }

    @Operation(summary = "로그아웃",
            description = """
                    전달한 리프레시 토큰을 폐기한다. 응답 본문은 없다(204).

                    여러 기기에서 로그인한 경우 해당 기기의 토큰만 무효화된다.
                    이미 폐기됐거나 존재하지 않는 토큰이어도 204를 반환하므로,
                    클라이언트는 응답과 무관하게 저장된 토큰을 지우면 된다.
                    """)
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshTokenRequestDTO request) {
        authService.logout(request.getRefreshToken());
    }
}