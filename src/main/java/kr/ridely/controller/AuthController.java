package kr.ridely.controller;

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
 * W1 범위: POST /api/v1/auth/signup 만.
 * login·refresh·logout은 W2 (SPRINT_W2.md A1·A2).
 *
 * 검증 책임 분리 (핸드오프 07-20 §3):
 *   - 형식 검증: @Valid → 실패 시 GlobalExceptionHandler가 COMMON-001
 *   - 비밀번호 정책: AuthServiceImpl → AUTH-102
 *   - loginId 중복: AuthServiceImpl → AUTH-101
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 회원가입. 성공 시 201 Created + 생성된 회원 정보(UserResponseDTO).
     * app_user + user_settings(기본값) 1행씩이 한 트랜잭션으로 생성된다.
     */
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponseDTO> signup(@Valid @RequestBody SignupRequestDTO request) {
        return ApiResponse.ok(authService.signup(request));
    }

    /**
     * 로그인. 성공 시 access·refresh 토큰을 발급한다.
     *
     * 아이디가 없는 경우와 비밀번호가 틀린 경우 모두 AUTH-201로 응답한다
     * (가입 여부가 노출되지 않도록).
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        return ApiResponse.ok(authService.login(request));
    }

    /**
     * 토큰 재발급.
     *
     * 액세스 토큰이 만료됐을 때 리프레시 토큰으로 새 토큰 쌍을 받는다.
     * 사용한 리프레시 토큰은 폐기되므로, 응답으로 받은 새 토큰으로 교체해야 한다.
     */
    @PostMapping("/refresh")
    public ApiResponse<LoginResponseDTO> refresh(@Valid @RequestBody RefreshTokenRequestDTO request) {
        return ApiResponse.ok(authService.refresh(request.getRefreshToken()));
    }

    /**
     * 로그아웃. 전달한 리프레시 토큰을 폐기한다.
     *
     * 여러 기기에서 로그인한 경우 해당 토큰만 무효화된다.
     * 응답 본문은 없다(204).
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshTokenRequestDTO request) {
        authService.logout(request.getRefreshToken());
    }
}