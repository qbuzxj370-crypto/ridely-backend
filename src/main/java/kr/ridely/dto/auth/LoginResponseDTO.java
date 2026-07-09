package kr.ridely.dto.auth;

import lombok.*;

/**
 * 로그인 성공 시 돌려주는 토큰 묶음.
 * POST /api/v1/auth/login 응답.
 * POST /api/v1/auth/refresh (토큰 재발급) 응답에도 그대로 재사용한다.
 *
 * 대응 테이블: app_user(조회), refresh_token(저장)
 *
 * 앱은 이 두 토큰을 저장해 두고:
 *   - 모든 API 요청 헤더에 "Authorization: Bearer {accessToken}" 를 붙인다.
 *   - accessToken이 만료되면 refreshToken으로 /auth/refresh를 호출해 새로 받는다.
 *
 * ※ refreshToken 원본은 클라이언트에만 주고, 서버 DB(refresh_token 테이블)에는
 *   해시(token_hash)만 저장한다. DB가 유출돼도 토큰을 그대로 쓸 수 없게 하기 위함.
 *
 * 응답 예시 (ApiResponse로 감싸진 상태):
 * {
 *   "success": true,
 *   "data": {
 *     "accessToken": "eyJhbGciOi...",
 *     "refreshToken": "eyJhbGciOi...",
 *     "tokenType": "Bearer",
 *     "accessTokenExpiresIn": 3600
 *   },
 *   "error": null
 * }
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponseDTO {

    /** API 호출용 짧은 수명 토큰 (기본 1시간) */
    private String accessToken;

    /** accessToken 재발급용 긴 수명 토큰 (기본 14일) */
    private String refreshToken;

    /** 토큰 종류. 항상 "Bearer" */
    private String tokenType;

    /** accessToken 만료까지 남은 시간(초). 앱이 갱신 시점을 계산하는 데 쓴다 */
    private long accessTokenExpiresIn;

    /** tokenType을 "Bearer"로 자동 채워 주는 편의 생성자 */
    public LoginResponseDTO(String accessToken, String refreshToken, long accessTokenExpiresIn) {
        this(accessToken, refreshToken, "Bearer", accessTokenExpiresIn);
    }
}
