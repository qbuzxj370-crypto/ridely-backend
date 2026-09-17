package kr.ridely.dto.user;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 회원 탈퇴 요청 (DELETE /api/v1/users/me).
 *
 * 되돌릴 수 없는 작업이라 비밀번호를 다시 받는다. 토큰만으로 탈퇴가 되면 화면을 잠깐 넘긴 사이에 계정이 사라질 수 있다.
 *
 * DELETE에 본문을 싣는다. DELETE 본문은 정해진 의미가 없어 버리거나 거부하는 구현이 있다. 그럼에도 이렇게 둔 것은 대안인 쿼리 문자열이 비밀번호를 접근 로그에 평문으로 남기기 때문이다. 앱이 직접 부르는 API라 중간에서 본문을 버릴 경로가 없다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawRequestDTO {

    @NotBlank(message = "비밀번호를 입력해 주세요")
    private String password;
}
