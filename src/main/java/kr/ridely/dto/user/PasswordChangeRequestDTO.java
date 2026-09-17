package kr.ridely.dto.user;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 비밀번호 변경 요청 (PATCH /api/v1/users/me/password).
 *
 * 현재 비밀번호를 함께 받는다. 토큰이 유효해도 그 기기를 다른 사람이 쥐고 있을 수 있다. 비밀번호를 바꾸는 순간이 계정을 통째로 넘기는 순간이라 한 번 더 확인한다.
 *
 * 새 비밀번호의 정책(8~30자, 영문·숫자·특수문자)은 여기서 검사하지 않는다. DTO에서 검사하면 {@code COMMON-001}로 나가 {@code AUTH-102}가 나갈 수 없다. 회원가입과 같은 이유다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PasswordChangeRequestDTO {

    @NotBlank(message = "현재 비밀번호를 입력해 주세요")
    private String currentPassword;

    @NotBlank(message = "새 비밀번호를 입력해 주세요")
    private String newPassword;
}
