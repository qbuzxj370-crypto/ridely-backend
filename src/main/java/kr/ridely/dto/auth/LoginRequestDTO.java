package kr.ridely.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.*;

/**
 * 로그인 요청 데이터.
 * POST /api/v1/auth/login 의 요청 본문.
 *
 * 대응 테이블: app_user (조회만)
 *
 * ※ 여기서는 형식 검증만 한다 (빈 값 여부).
 *   비밀번호 정책 검증은 회원가입 때만 하면 된다.
 *   아이디나 비밀번호가 틀리면 AUTH-201 에러를 돌려준다.
 *   이때 "아이디가 없습니다" / "비밀번호가 틀립니다"를 구분해서 알려주면
 *   공격자가 가입된 아이디를 알아낼 수 있으므로 같은 메시지로 통일한다.
 *
 * 요청 예시:
 * {
 *   "loginId": "ridely_user",
 *   "password": "Pa55word!"
 * }
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequestDTO {

    /** 로그인 아이디 */
    @NotBlank(message = "아이디를 입력해 주세요")
    private String loginId;

    /** 비밀번호 (평문). 서버에서 해시값과 비교 */
    @NotBlank(message = "비밀번호를 입력해 주세요")
    private String password;
}
