package kr.ridely.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * 회원가입 요청 데이터.
 * POST /api/v1/auth/signup 의 요청 본문(body)으로 들어온다.
 *
 * 대응 테이블: app_user
 *
 * ※ 검증 규칙(@NotBlank 등)은 컨트롤러에서 @Valid를 붙여야 동작한다.
 *   검증에 실패하면 GlobalExceptionHandler가 COMMON-001 에러로 변환해 준다.
 *
 * 요청 예시:
 * {
 *   "loginId": "ridely_user",
 *   "password": "Pa55word!",
 *   "nickname": "한강라이더",
 *   "email": "rider@example.com"
 * }
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SignupRequestDTO {

    /**
     * 로그인 아이디. 중복 불가 (app_user.login_id UNIQUE).
     * 이미 존재하면 AUTH-101 에러.
     */
    @NotBlank(message = "아이디를 입력해 주세요")
    @Size(min = 4, max = 50, message = "아이디는 4~50자여야 합니다")
    private String loginId;

    /**
     * 비밀번호 (평문).
     * 서버에서 해시한 뒤 app_user.password_hash에 저장한다.
     * 절대 평문 그대로 DB에 넣지 않는다.
     *
     * 정책 위반 시 AUTH-102 에러.
     */
    @NotBlank(message = "비밀번호를 입력해 주세요")
    @Size(min = 8, max = 30, message = "비밀번호는 8~30자여야 합니다")
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
            message = "비밀번호는 영문, 숫자, 특수문자를 각각 1자 이상 포함해야 합니다"
    )
    private String password;

    /** 앱에서 표시되는 별명 */
    @NotBlank(message = "닉네임을 입력해 주세요")
    @Size(min = 2, max = 50, message = "닉네임은 2~50자여야 합니다")
    private String nickname;

    /**
     * 이메일. 선택 입력 (app_user.email은 NULL 허용).
     * 값이 들어오면 형식만 검사한다.
     */
    @Email(message = "이메일 형식이 올바르지 않습니다")
    @Size(max = 100)
    private String email;
}