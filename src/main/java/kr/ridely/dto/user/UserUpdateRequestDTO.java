package kr.ridely.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * 내 정보 수정 요청.
 * PATCH /api/v1/users/me 의 요청 본문.
 *
 * 대응 테이블: app_user (UPDATE)
 *
 * ※ PATCH는 "보낸 항목만 수정"한다.
// *   따라서 값을 보내지 않은 필드는 null로 들어오고, 서비스에서 null인 필드는 건드리지 않는다.
 *   예) 닉네임만 바꾸고 싶으면 { "nickname": "새닉네임" } 만 보내면 된다.
 *
 * ※ 아이디(loginId)와 비밀번호는 여기서 수정 불가
 *   - loginId: 변경 불가 정책
 *   - password: 별도 API로 분리 (현재 비밀번호 확인이 필요하므로)
 *
 * 요청 예시:
 * {
 *   "nickname": "한강러너",
 *   "email": "new@example.com"
 * }
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateRequestDTO {

    /** 바꿀 닉네임. 보내지 않으면 기존 값 유지 */
    @Size(min = 2, max = 50, message = "닉네임은 2~50자여야 합니다")
    private String nickname;

    /** 바꿀 이메일. 보내지 않으면 기존 값 유지 */
    @Email(message = "이메일 형식이 올바르지 않습니다")
    @Size(max = 100)
    private String email;
}
