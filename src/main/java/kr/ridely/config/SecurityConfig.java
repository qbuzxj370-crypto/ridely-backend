package kr.ridely.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 설정.
 *
 * 정책:
 *   - 토큰 기반 인증이라 세션을 만들지 않는다 (STATELESS)
 *   - 아래 목록은 인증 없이 접근, 나머지는 액세스 토큰 필요
 *   - 인증 실패 응답은 우리 형식({success, data, error})으로 통일
 *
 * ⚠️ 새 API를 추가할 때 공개 경로면 반드시 아래 목록에 넣어야 한다.
 *   빠뜨리면 401이 나는데 원인을 찾기 어렵다 (실제로 /tours/** 누락으로 겪었다).
 */
@Configuration
public class SecurityConfig {

    /** 인증 없이 접근 가능한 경로 */
    private static final String[] PUBLIC_PATHS = {
            "/api/v1/health",
            "/api/v1/auth/signup",
            "/api/v1/auth/login",
            // 재발급·로그아웃은 요청 본문의 리프레시 토큰 자체가 자격 증명이다.
            // 액세스 토큰이 만료된 상태에서 호출되는 것이 정상 흐름이라
            // 액세스 토큰 인증을 요구할 수 없다.
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/pois/**",
            "/api/v1/tours/**",         // 관광지 조회 (비회원도 코스를 짜볼 수 있어야 한다)
            // 출발지를 이름으로 찾는 검색이다. 좌표를 정하는 것이 추천 요청의 첫 단계라
            // 여기서 로그인을 요구하면 비회원에게 코스 추천을 여는 의미가 없어진다.
            "/api/v1/geo/**",
            // 코스 추천도 비회원에게 연다. 가입 전에 서비스를 써보게 하는 것이 이 앱의 진입점이다.
            // 회원이면 토큰에서 번호를 꺼내 결과에 붙이고, 비회원이면 user_id를 NULL로 저장한다.
            "/api/v1/routes/**",
            "/api/v1/poc/**",           // 임시 PoC
            // API 문서. 경로는 열려 있지만 문서 기능 자체가 개발 프로파일에서만 켜지므로
            // (application.yml의 springdoc.*.enabled=false가 기본값)
            // 운영 환경에서는 이 경로로 접근해도 404가 된다.
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CorsConfig의 CorsConfigurationSource 빈을 필터 체인 맨 앞단에 물린다.
                // WebMvcConfigurer 방식(DispatcherServlet 단계)이 아니라 이렇게 해야,
                // JwtAuthenticationFilter가 유효하지 않은 토큰을 만나 응답을 직접 쓰고
                // 체인을 끊어도(ApiErrorWriter) CORS 헤더가 이미 붙어 있다. 아래 OPTIONS
                // permitAll 규칙과 별개로, 이게 진짜 CORS 헤더 부착을 보장하는 부분이다.
                .cors(Customizer.withDefaults())
                // REST API라 CSRF 비활성 (토큰 기반이라 쿠키를 쓰지 않는다)
                .csrf(AbstractHttpConfigurer::disable)
                // 세션을 만들지 않는다. 인증 상태는 매 요청의 토큰으로만 판단한다
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // CORS 프리플라이트(OPTIONS)는 Authorization 헤더 없이 온다.
                        // 이걸 permitAll에 안 넣으면 보호 경로의 프리플라이트가 401로 막혀
                        // 실제 요청(GET/POST 등)이 나가기도 전에 브라우저가 CORS 에러로 처리한다.
                        // (Cordova WebView에서 인증 필요한 API 호출 시 실제로 이렇게 막혔다.)
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated()
                )
                // 인증 없이 보호 경로 접근 시 우리 응답 형식으로 COMMON-002
                .exceptionHandling(handler ->
                        handler.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                // 토큰 검사는 아이디·비밀번호 인증 단계보다 먼저 수행한다
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
