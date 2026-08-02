package kr.ridely.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 설정.
 *
 * 1주차 정책:
 *   - 회원가입(/auth/signup), 헬스체크(/health), POI 조회(/pois/**), PoC(/poc/**) → permitAll
 *   - 나머지 → 인증 필요 (단, JWT 필터는 2주차에 추가)
 *   - PasswordEncoder(BCrypt) Bean 등록
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // REST API라 CSRF·세션 비활성 (JWT 기반 예정)
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/api/v1/health",
                        "/api/v1/auth/signup",
                        "/api/v1/auth/login",
                        // 재발급·로그아웃은 요청 본문의 리프레시 토큰 자체가 자격 증명이므로
                        // 액세스 토큰 인증을 요구하지 않는다.
                        // (액세스 토큰이 만료된 상태에서 호출되는 것이 정상 흐름이다)
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/logout",
                        "/api/v1/pois/**",
                        "/api/v1/tours/**",         // 관광지 조회 (비회원도 코스를 짜볼 수 있어야 한다)
                        "/api/v1/poc/**"            // 임시 PoC
                ).permitAll()
                .anyRequest().authenticated()
            );
        // TODO(2주차): .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
