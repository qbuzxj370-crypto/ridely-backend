package kr.ridely.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS 설정.
 * 허용 origin·method는 CorsProperties(ridely.cors.*)에서 주입.
 *
 * Cordova WebView는 file:// 등에서 요청하므로 이 설정이 없으면
 * 앱에서의 API 호출이 전부 차단된다. 삭제 금지.
 *
 * ⚠️ WebMvcConfigurer.addCorsMappings가 아니라 CorsConfigurationSource 빈으로 노출한다.
 * SecurityConfig가 이 빈을 http.cors(...)에 물려서 Spring Security의 필터 체인
 * 맨 앞단에서 CORS를 처리하게 한다. WebMvcConfigurer 방식은 DispatcherServlet의
 * HandlerMapping 단계(모든 Security 필터보다 뒤)에서만 동작해서, JwtAuthenticationFilter가
 * 토큰 오류로 응답을 직접 쓰고 체인을 끊어버리면(ApiErrorWriter) CORS 헤더가 아예 안 붙는다.
 * 실기기에서 만료된 토큰으로 호출할 때마다 "CORS 정책에 의해 차단됨"으로 보였던 게 이 문제였다
 * (2026-09-17) — 실제로는 401인데 브라우저가 CORS 헤더 없는 응답을 network error로 표시했다.
 */
@Configuration
@RequiredArgsConstructor
public class CorsConfig {

    private final CorsProperties corsProperties;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.getAllowedOrigins());
        configuration.setAllowedMethods(corsProperties.getAllowedMethods());
        configuration.setAllowedHeaders(java.util.List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
