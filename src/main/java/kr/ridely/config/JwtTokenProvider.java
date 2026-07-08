package kr.ridely.config;

import org.springframework.stereotype.Component;

/**
 * JWT 발급·검증.
 * JJWT 0.12.x 기준.
 *
 * ⚠️ 1주차는 메서드 골격만. 실제 로그인 API·인증 필터는 2주차.
 * ⚠️ JJWT 0.12.x는 0.11 이하와 API가 다름 (Jwts.builder().subject(...), verifyWith(...) 등)
 */
@Component
public class JwtTokenProvider {

    private final JwtProperties properties;
    // TODO: SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
    }

    /** access 토큰 발급 */
    public String generateAccessToken(long userId) {
        // TODO(JJWT 0.12.x):
        //   Jwts.builder()
        //       .subject(String.valueOf(userId))
        //       .issuedAt(now)
        //       .expiration(now + accessTokenValiditySeconds*1000)
        //       .signWith(key)
        //       .compact();
        throw new UnsupportedOperationException("TODO: 2주차 구현");
    }

    /** refresh 토큰 발급 */
    public String generateRefreshToken(long userId) {
        // TODO: accessTokenValiditySeconds 대신 refreshTokenValiditySeconds 적용
        throw new UnsupportedOperationException("TODO: 2주차 구현");
    }

    /** 토큰 유효성 검증 */
    public boolean validate(String token) {
        // TODO: Jwts.parser().verifyWith(key).build().parseSignedClaims(token) → 예외 시 false
        throw new UnsupportedOperationException("TODO: 2주차 구현");
    }

    /** 토큰에서 userId 추출 */
    public long getUserId(String token) {
        // TODO: parseSignedClaims(token).getPayload().getSubject() → Long 파싱
        throw new UnsupportedOperationException("TODO: 2주차 구현");
    }
}
