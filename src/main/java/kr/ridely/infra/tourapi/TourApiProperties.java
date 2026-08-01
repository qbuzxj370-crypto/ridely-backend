package kr.ridely.infra.tourapi;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 한국관광공사 TourAPI 설정 바인딩.
 * application.yml의 ridely.external.tourapi.* 값을 주입받는다.
 *
 * 사용하려면 @ConfigurationPropertiesScan 또는
 * @EnableConfigurationProperties(TourApiProperties.class)가 필요
 * (Spring Boot 3.x는 메인 클래스에 @ConfigurationPropertiesScan 권장).
 *
 * ※ YAML 리스트는 @Value로 주입할 수 없다(값이 [0],[1]로 평탄화됨).
 *   @ConfigurationProperties는 정상 바인딩되므로 수집 지점 목록도 여기서 받는다.
 *
 * @param baseUrl        https://apis.data.go.kr/B551011/KorService2
 * @param serviceKey     data.go.kr 발급 서비스 키 (TOUR_API_KEY)
 * @param timeoutSeconds 호출 타임아웃 (기본 10초)
 * @param contentTypeIds MVP 적재 대상 (12=관광지, 14=문화시설, 39=음식점)
 * @param ingest         적재 수집 범위 설정
 */
@ConfigurationProperties(prefix = "ridely.external.tourapi")
public record TourApiProperties(
        String baseUrl,
        String serviceKey,
        int timeoutSeconds,
        String contentTypeIds,
        Ingest ingest
) {

    /**
     * 적재 수집 범위.
     *
     * 지역을 넓히거나 반경을 조정할 때 코드 수정 없이 설정만 바꾸면 되도록 분리했다
     * (기획 확장성 원칙: MVP는 수도권으로 출발하되 구조는 전국 대응).
     *
     * @param radiusM 지점당 수집 반경 (m, 매뉴얼 상한 20000)
     * @param points  수집 중심점 목록
     */
    public record Ingest(
            int radiusM,
            List<Point> points
    ) {
    }

    /**
     * 수집 중심점.
     *
     * @param name 지점 이름 (적재 로그 식별용)
     * @param lng  경도
     * @param lat  위도
     */
    public record Point(
            String name,
            double lng,
            double lat
    ) {
    }
}
