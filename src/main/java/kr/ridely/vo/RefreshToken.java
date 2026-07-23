package kr.ridely.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * refresh_token 테이블 VO (스키마 v1.1 컬럼 1:1).
 *
 * 민감 컬럼(token_hash)을 보유한 테이블이므로 vo로 분리
 * 토큰 원문이 아닌 해시만 저장. 이 객체는 dao·service 내부 전용.
 *
 * ※ 1주차(골격 2)에서는 클래스만 선작성. 실제 INSERT/SELECT는
 *   2주차 로그인·재발급 API(POST /auth/login, /auth/refresh)에서 사용.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    /** refresh_token.refresh_token_id (BIGSERIAL PK) */
    private Long refreshTokenId;

    /** refresh_token.user_id (FK -> app_user, ON DELETE CASCADE) */
    private Long userId;

    /** refresh_token.token_hash (VARCHAR(255) NOT NULL) — 리프레시 토큰 해시. 민감 컬럼 */
    private String tokenHash;

    /** refresh_token.expires_at (NOT NULL) — 만료 시각 (발급 시점 + 14d) */
    private OffsetDateTime expiresAt;

    /** refresh_token.revoked_at (nullable) — 폐기 시각. null이면 유효 */
    private OffsetDateTime revokedAt;

    /** refresh_token.created_at (DEFAULT NOW()) */
    private OffsetDateTime createdAt;
}
