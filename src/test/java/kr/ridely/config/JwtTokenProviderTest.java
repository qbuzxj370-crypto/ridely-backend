package kr.ridely.config;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JwtTokenProvider 단위 테스트.
 *
 * Spring 컨텍스트를 띄우지 않고 JwtProperties를 직접 만들어 주입한다.
 * 만료 검증처럼 시간이 필요한 경우 만료 시간이 짧은 provider를 따로 만든다.
 */
class JwtTokenProviderTest {

    /** 테스트용 시크릿. 32바이트(256bit) 이상이어야 한다 */
    private static final String SECRET = "ridely-test-secret-key-for-unit-test-32bytes-over";
    private static final long USER_ID = 42L;

    /** 만료 시간을 지정해 provider 생성 (단위: 초) */
    private JwtTokenProvider provider(long accessSeconds, long refreshSeconds) {
        return new JwtTokenProvider(
                new JwtProperties(SECRET, accessSeconds, refreshSeconds, "ridely"));
    }

    /** 기본 provider — access 1시간, refresh 14일 (운영 설정과 동일) */
    private JwtTokenProvider provider() {
        return provider(3600, 1209600);
    }

    @Test
    @DisplayName("access 토큰을 발급하면 검증에 통과하고 userId를 되돌려준다")
    void accessToken_왕복() {
        JwtTokenProvider sut = provider();

        String token = sut.generateAccessToken(USER_ID);

        assertThat(sut.validate(token)).isTrue();
        assertThat(sut.getUserId(token)).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("refresh 토큰도 동일하게 발급·검증된다")
    void refreshToken_왕복() {
        JwtTokenProvider sut = provider();

        String token = sut.generateRefreshToken(USER_ID);

        assertThat(sut.validate(token)).isTrue();
        assertThat(sut.getUserId(token)).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("access와 refresh는 type 클레임으로 구분된다")
    void 토큰_종류_구분() {
        JwtTokenProvider sut = provider();

        assertThat(sut.getTokenType(sut.generateAccessToken(USER_ID)))
                .isEqualTo(JwtTokenProvider.TYPE_ACCESS);
        assertThat(sut.getTokenType(sut.generateRefreshToken(USER_ID)))
                .isEqualTo(JwtTokenProvider.TYPE_REFRESH);
    }

    @Test
    @DisplayName("만료된 토큰은 검증에 실패한다")
    void 만료_토큰_거부() throws InterruptedException {
        // 유효 기간 1초짜리 provider로 발급 후 만료를 기다린다
        JwtTokenProvider sut = provider(1, 1);
        String token = sut.generateAccessToken(USER_ID);

        Thread.sleep(1_100);

        assertThat(sut.validate(token)).isFalse();
        assertThatThrownBy(() -> sut.getUserId(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("다른 시크릿으로 발급된 토큰은 서명 검증에 실패한다")
    void 위조_서명_거부() {
        JwtTokenProvider attacker = new JwtTokenProvider(
                new JwtProperties("attacker-secret-key-32bytes-over-1234567890", 3600, 1209600, "ridely"));
        String forged = attacker.generateAccessToken(USER_ID);

        JwtTokenProvider sut = provider();

        assertThat(sut.validate(forged)).isFalse();
        assertThatThrownBy(() -> sut.getUserId(forged))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    @DisplayName("형식이 잘못된 문자열은 검증에 실패한다")
    void 잘못된_형식_거부() {
        JwtTokenProvider sut = provider();

        assertThat(sut.validate("not-a-jwt")).isFalse();
        assertThat(sut.validate("")).isFalse();
        assertThat(sut.validate(null)).isFalse();
    }

    @Test
    @DisplayName("시크릿이 32바이트 미만이면 생성 시점에 예외가 발생한다")
    void 짧은_시크릿_거부() {
        JwtProperties weak = new JwtProperties("too-short", 3600, 1209600, "ridely");

        assertThatThrownBy(() -> new JwtTokenProvider(weak))
                .isInstanceOf(WeakKeyException.class);
    }

    @Test
    @DisplayName("access와 refresh의 만료 시각이 설정대로 다르게 적용된다")
    void 만료시간_설정_반영() {
        // access 1초 / refresh 넉넉하게 → access만 만료되는지로 확인
        JwtTokenProvider sut = provider(1, 3600);
        String access = sut.generateAccessToken(USER_ID);
        String refresh = sut.generateRefreshToken(USER_ID);

        try {
            Thread.sleep(1_100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertThat(sut.validate(access)).isFalse();
        assertThat(sut.validate(refresh)).isTrue();
    }
}
