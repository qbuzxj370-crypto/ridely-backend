package kr.ridely.infra.seoul;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 서울 열린데이터광장 OpenAPI 설정 바인딩.
 * application.yml의 ridely.external.public-data-portal.seoul.* 값을 주입받는다.
 *
 * 공공데이터포털(data.go.kr)과는 다른 기관·다른 인증키다.
 * 키는 열린데이터광장(data.seoul.go.kr)에서 별도 발급하며 SEOUL_OPEN_API_KEY로 주입한다.
 *
 * @param baseUrl        http://openapi.seoul.go.kr:8088 (https 미지원, 포트 필수)
 * @param apiKey         열린데이터광장 발급 인증키
 * @param timeoutSeconds 호출 타임아웃
 * @param maxRowsPerCall 1회 요청 최대 건수. 초과 시 ERROR-336
 */
@ConfigurationProperties(prefix = "ridely.external.public-data-portal.seoul")
public record SeoulOpenApiProperties(
        String baseUrl,
        String apiKey,
        int timeoutSeconds,
        int maxRowsPerCall
) {
}
