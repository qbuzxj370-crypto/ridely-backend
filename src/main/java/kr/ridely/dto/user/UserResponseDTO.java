package kr.ridely.dto.user;

import lombok.*;

import java.time.OffsetDateTime;

/**
 * 회원 정보 응답.
 *
 * 아래 두 API의 응답으로 함께 사용한다 (담기는 데이터 성격이 같기 때문):
 *   - POST /api/v1/auth/signup  (회원가입 직후 생성된 회원 정보)
 *   - GET  /api/v1/users/me     (내 정보 조회)
 *
 * 대응 테이블: app_user
 *
 * ※ password_hash는 절대 포함하지 않는다.
 *   이 DTO에 password 필드 자체가 없으므로 실수로 유출될 수 없다.
 *
 * 응답 예시 (ApiResponse로 감싸진 상태):
 * {
 *   "success": true,
 *   "data": {
 *     "userId": 1,
 *     "loginId": "ridely_user",
 *     "nickname": "한강라이더",
 *     "email": "rider@example.com",
 *     "status": "ACTIVE",
 *     "lastLoginAt": null,
 *     "createdAt": "2026-07-09T14:20:00+09:00"
 *   },
 *   "error": null
 * }
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDTO {

    /** 회원 고유 번호 (app_user.user_id) */
    private Long userId;

    /** 로그인 아이디 */
    private String loginId;

    /** 닉네임 */
    private String nickname;

    /** 이메일. 입력하지 않았으면 null */
    private String email;

    /**
     * 계정 상태.
     * ACTIVE(정상) / SUSPENDED(정지) / WITHDRAWN(탈퇴)
     */
    private String status;

    /**
     * 마지막 로그인 시각.
     * 회원가입 직후에는 아직 로그인한 적이 없으므로 null.
     */
    private OffsetDateTime lastLoginAt;

    /** 가입 시각 */
    private OffsetDateTime createdAt;
}
