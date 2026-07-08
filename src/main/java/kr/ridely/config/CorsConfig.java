package kr.ridely.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * CORS 설정.
 * 허용 origin은 application.yml의 ridely.cors.allowed-origins에서 주입.
 *
 * Cordova WebView는 file://, http://localhost 등에서 요청하므로
 * 개발/운영 프로파일별로 허용 목록을 다르게 둔다.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${ridely.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
