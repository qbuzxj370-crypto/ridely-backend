package kr.ridely.common;

import java.util.regex.Pattern;

/**
 * 비밀번호 정책. 8~30자, 영문·숫자·특수문자 각 1자 이상.
 *
 * 회원가입과 비밀번호 변경이 같은 규칙을 써야 한다. 둘 중 하나만 바뀌면 가입은 되는데 변경은 안 되는 비밀번호가 생긴다.
 *
 * DTO의 {@code @Pattern}으로 두지 않는 이유는 위반이 전부 {@code COMMON-001}로 바뀌어 {@code AUTH-102}가 나갈 수 없기 때문이다. 서비스에서 직접 검사한다.
 */
public final class PasswordPolicy {

    private static final Pattern POLICY =
            Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,30}$");

    private PasswordPolicy() {
    }

    public static boolean isValid(String password) {
        return password != null && POLICY.matcher(password).matches();
    }
}
