package kr.ridely.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient 설정.
 * 외부 API(ORS, Kakao, 공공데이터) 호출용 공통 WebClient Bean.
 *
 * 각 infra 클라이언트(infra/ors, infra/kakao, infra/publicdata)가 주입받아 사용.
 * 도메인별 baseUrl은 각 클라이언트에서 설정.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        // TODO: 타임아웃, 커넥션 풀, 로깅 필터 등 공통 설정 추가
        return WebClient.builder();
    }
}
