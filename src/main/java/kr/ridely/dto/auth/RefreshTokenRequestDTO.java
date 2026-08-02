package kr.ridely.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 리프레시 토큰을 전달하는 요청 본문.
 *
 * 두 곳에서 함께 쓴다:
 *   - POST /api/v1/auth/refresh  (토큰 재발급)
 *   - POST /api/v1/auth/logout   (토큰 폐기)
 *
 * 로그아웃도 리프레시 토큰을 받는 이유는, 폐기 대상을 특정해야 하기 때문이다.
 * 여러 기기에서 로그인한 경우 지금 로그아웃하는 기기의 토큰만 무효화한다.
 *
 * 요청 예시:
 * {
 *   "refreshToken": "eyJhbGciOi..."
 * }
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenRequestDTO {

    /** 로그인 시 발급받은 리프레시 토큰 원문 */
    @NotBlank(message = "리프레시 토큰이 필요합니다")
    private String refreshToken;
}
