package kr.ridely.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 설정 바인딩.
 * application.yml의 ridely.jwt.* 값을 주입.
 *
 * @param secret                      서명 시크릿 (Base64, 최소 256bit 권장)
 * @param accessTokenValiditySeconds  access 토큰 만료 (초, 기본 3600)
 * @param refreshTokenValiditySeconds refresh 토큰 만료 (초, 기본 1209600)
 * @param issuer                      토큰 발급자
 */
@ConfigurationProperties(prefix = "ridely.jwt")
public record JwtProperties(
        String secret,
        long accessTokenValiditySeconds,
        long refreshTokenValiditySeconds,
        String issuer
) {
}
