package kr.ridely.infra.tourapi;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 한국관광공사 TourAPI 설정 바인딩.
 * application.yml의 ridely.external.tourapi.* 값을 주입받는다.
 *
 * 사용하려면 @ConfigurationPropertiesScan 또는
 * @EnableConfigurationProperties(TourApiProperties.class)가 필요
 * (Spring Boot 3.x는 메인 클래스에 @ConfigurationPropertiesScan 권장).
 *
 * @param baseUrl        https://apis.data.go.kr/B551011/KorService2
 * @param serviceKey     data.go.kr 발급 서비스 키 (TOUR_API_KEY)
 * @param timeoutSeconds 호출 타임아웃 (기본 10초)
 * @param contentTypeIds MVP 적재 대상 (12=관광지, 14=문화시설, 39=음식점)
 */
@ConfigurationProperties(prefix = "ridely.external.tourapi")
public record TourApiProperties(
        String baseUrl,
        String serviceKey,
        int timeoutSeconds,
        String contentTypeIds
) {
}
