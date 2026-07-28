package kr.ridely.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 발급·검증 (JJWT 0.12.x).
 *
 * 토큰 종류를 type 클레임으로 구분한다(access/refresh).
 * refresh 자리에 access 토큰을 넣어도 서명 자체는 유효하기 때문에 클레임으로 구분하지 않으면 이후 재발급 API에서 서로 바꿔치기가 가능해진다.
 *
 * 실제 로그인 API·인증 필터는 2주차. 1주차는 이 클래스 + 단위 테스트까지.
 */
@Component
public class JwtTokenProvider {

    /** 토큰 종류 클레임 이름 */
    private static final String CLAIM_TYPE = "type";

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        /*
          시크릿 문자열의 UTF-8 바이트를 그대로 서명 키로 사용한다.
          32바이트(256bit) 미만이면 Keys.hmacShaKeyFor가 WeakKeyException을 던지므로 잘못된 설정이 애플리케이션 기동 시점에 바로 드러난다.
         */
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    /** access 토큰 발급 (기본 1시간) */
    public String generateAccessToken(long userId) {
        return generate(userId, TYPE_ACCESS, properties.accessTokenValiditySeconds());
    }

    /** refresh 토큰 발급 (기본 14일) */
    public String generateRefreshToken(long userId) {
        return generate(userId, TYPE_REFRESH, properties.refreshTokenValiditySeconds());
    }

    /**
     * 토큰 유효성 검증 (서명·만료·형식).
     *
     * 유효하지 않은 이유는 구분하지 않는다.
     * 만료(AUTH-301)와 위조(AUTH-302)를 나눠 응답해야 하는 시점은 인증 필터 개발 시 거기서 예외 종류를 보고 처리할 예정
     */
    public boolean validate(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 토큰에서 회원 번호(subject) 추출.
     *
     * @throws JwtException 서명·만료·형식이 잘못된 경우
     */
    public long getUserId(String token) {
        return Long.parseLong(parse(token).getSubject());
    }

    /** 토큰 종류(access/refresh) 추출 */
    public String getTokenType(String token) {
        return parse(token).get(CLAIM_TYPE, String.class);
    }

    // JwtTokenProvider에서만 사용하는 내부 메서드(private)

    /**
     * 토큰 생성 공통 로직.
     * access·refresh는 만료 시간과 type 클레임만 다르므로 한 메서드로 묶는다.
     *
     * @param userId          토큰 소유자 (subject에 문자열로 저장. JWT 표준상 subject는 String)
     * @param type            TYPE_ACCESS 또는 TYPE_REFRESH
     * @param validitySeconds 발급 시점부터의 유효 기간(초)
     */
    private String generate(long userId, String type, long validitySeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_TYPE, type)          // 커스텀 클레임 (access/refresh 구분)
                .issuer(properties.issuer())      // 발급자 = "ridely"
                .issuedAt(Date.from(now))         // iat — 발급 시각
                .expiration(Date.from(now.plusSeconds(validitySeconds)))  // exp — 만료 시각
                .signWith(key)                    // HMAC-SHA 서명 (키 길이로 알고리즘 자동 선택)
                .compact();                       // 헤더.페이로드.서명 을 Base64URL 문자열로 직렬화
    }

    /**
     * 토큰 파싱 공통 로직. 서명을 검증한 뒤 페이로드(클레임)를 꺼낸다.
     *
     * parseSignedClaims는 검증에 실패하면 예외를 던진다:
     *   - SignatureException     서명 불일치 (위조·다른 키로 발급)
     *   - ExpiredJwtException    만료됨 (exp 경과)
     *   - MalformedJwtException  JWT 형식이 아님
     *   - IllegalArgumentException  null·빈 문자열
     * 호출부에서 상황에 맞게 처리한다(validate는 false 반환, getUserId는 그대로 전파).
     */
    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)          // 이 키로 서명됐는지 검증
                .build()
                .parseSignedClaims(token) // 서명 검증 + 만료 확인 (실패 시 예외)
                .getPayload();            // 클레임 본문 (subject·type·iat·exp 등)
    }
}
