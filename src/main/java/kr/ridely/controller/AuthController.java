package kr.ridely.controller;

import jakarta.validation.Valid;
import kr.ridely.common.ApiResponse;
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
}