package kr.ridely.config;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.ridely.common.ApiErrorWriter;
import kr.ridely.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 요청 헤더의 액세스 토큰을 확인해 인증 정보를 설정하는 필터.
 *
 * 동작:
 *   토큰 없음        → 아무것도 하지 않고 통과 (공개 경로면 그대로 처리되고,
 *                     보호 경로면 이후 단계에서 JwtAuthenticationEntryPoint가 COMMON-002 응답)
 *   토큰 유효        → SecurityContext에 회원 번호를 담아 통과
 *   토큰 만료        → AUTH-301 (클라이언트는 재발급을 시도해야 한다)
 *   토큰 위조·형식오류 → COMMON-002 (재발급으로도 해결되지 않으므로 재로그인)
 *
 * ※ 필터에서 던진 예외는 컨트롤러에 도달하지 않아 GlobalExceptionHandler가 잡지 못한다.
 *   그래서 여기서 응답을 직접 쓴다.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final ApiErrorWriter apiErrorWriter;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, ApiErrorWriter apiErrorWriter) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.apiErrorWriter = apiErrorWriter;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String token = resolveToken(request);

        // 토큰이 없으면 인증하지 않은 상태로 넘긴다.
        // 공개 경로일 수 있으므로 여기서 막지 않는다.
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            /*
             * 리프레시 토큰으로 API를 호출하는 것을 막는다.
             * 두 토큰은 같은 키로 서명되므로 type 클레임을 확인하지 않으면
             * 수명이 긴 리프레시 토큰이 액세스 토큰처럼 쓰이게 된다.
             */
            if (!JwtTokenProvider.TYPE_ACCESS.equals(jwtTokenProvider.getTokenType(token))) {
                log.warn("액세스 토큰이 아닌 토큰으로 접근 시도");
                apiErrorWriter.write(response, ErrorCode.COMMON_002);
                return;
            }

            long userId = jwtTokenProvider.getUserId(token);
            setAuthentication(request, userId);

        } catch (ExpiredJwtException e) {
            // 정상적인 만료. 클라이언트가 재발급을 시도하도록 구분된 코드를 준다
            apiErrorWriter.write(response, ErrorCode.AUTH_301);
            return;
        } catch (JwtException | IllegalArgumentException e) {
            // 서명 불일치·형식 오류 등. 재발급으로 해결되지 않으므로 재로그인이 필요하다
            log.warn("유효하지 않은 토큰: {}", e.getMessage());
            apiErrorWriter.write(response, ErrorCode.COMMON_002);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * SecurityContext에 인증 정보를 등록한다.
     *
     * principal에 회원 번호(Long)를 넣으므로 컨트롤러에서
     * {@code @AuthenticationPrincipal Long userId}로 바로 받을 수 있다.
     * 권한(역할) 체계는 아직 없어 빈 목록을 넘긴다.
     */
    private void setAuthentication(HttpServletRequest request, long userId) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId, null, List.of());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /** "Authorization: Bearer {토큰}" 헤더에서 토큰만 꺼낸다 */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(AUTH_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
