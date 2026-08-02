package kr.ridely.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 리프레시 토큰 해시 유틸.
 *
 * 리프레시 토큰 원문은 클라이언트에만 주고 DB에는 해시만 저장한다.
 * DB가 유출돼도 저장된 값으로는 인증할 수 없게 하기 위함이다.
 *
 * 비밀번호와 달리 BCrypt를 쓰지 않는 이유:
 *   비밀번호는 사람이 만든 짧고 추측 가능한 문자열이라 무차별 대입을 늦추는
 *   느린 해시가 필요하다. 리프레시 토큰은 서버가 서명해 발급한 긴 문자열(JWT)이라
 *   추측 자체가 불가능하므로 빠른 SHA-256으로 충분하다.
 *   오히려 토큰 검증은 요청마다 일어나므로 느린 해시가 부담이 된다.
 */
public final class TokenHasher {

    private TokenHasher() {
    }

    /**
     * 토큰을 SHA-256 16진 문자열로 변환한다.
     * 같은 입력이면 항상 같은 결과라 DB 조회 조건으로 쓸 수 있다.
     */
    public static String sha256(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 모든 JVM이 제공하도록 표준에 정해져 있어 실제로는 발생하지 않는다
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다", e);
        }
    }
}
