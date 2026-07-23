package kr.ridely.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * app_user 테이블 VO (스키마 v1.1 컬럼 1:1).
 *
 * 민감 컬럼(password_hash)을 보유한 테이블이므로 vo로 분리
 * 이 객체는 dao·service 내부 전용이며, 절대 컨트롤러 응답으로 직접 반환하지 않는다.
 * 외부 응답은 반드시 UserResponseDTO로 변환한다 (password_hash 필드 자체가 없음).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AppUser {

    /** app_user.user_id (BIGSERIAL PK, INSERT 후 useGeneratedKeys로 채워짐) */
    private Long userId;

    /** app_user.login_id (VARCHAR(50) NOT NULL UNIQUE) */
    private String loginId;

    /** app_user.password_hash (VARCHAR(255) NOT NULL) — BCrypt 해시. 민감 컬럼 */
    private String passwordHash;

    /** app_user.nickname (VARCHAR(50) NOT NULL) */
    private String nickname;

    /** app_user.email (VARCHAR(100), nullable) */
    private String email;

    /** app_user.status — ACTIVE / SUSPENDED / WITHDRAWN (DEFAULT 'ACTIVE') */
    private String status;

    /** app_user.last_login_at (nullable, 가입 직후 null) */
    private OffsetDateTime lastLoginAt;

    /** app_user.created_at (DEFAULT NOW()) */
    private OffsetDateTime createdAt;

    /** app_user.updated_at (DEFAULT NOW()) */
    private OffsetDateTime updatedAt;
}
