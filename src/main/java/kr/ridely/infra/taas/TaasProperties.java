package kr.ridely.infra.taas;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 도로교통공단 TAAS(교통사고분석시스템) 설정 바인딩.
 * application.yml의 ridely.external.public-data-portal.taas.* 값을 주입받는다.
 *
 * 공공데이터포털(data.go.kr) 경유로 호출한다. 도로교통공단 자체 포털
 * (opendata.koroad.or.kr)과 경로·인증 체계가 다르므로 섞지 않는다.
 *
 * @param baseUrl        https://apis.data.go.kr/B552061
 * @param serviceKey     공공데이터포털 발급 인증키 (TAAS_SERVICE_KEY)
 * @param timeoutSeconds 호출 타임아웃
 * @param searchYear     사고년도. 갱신주기가 연 1회이며 활용가이드 3.1 기준 최신은 2024
 * @param maxRowsPerCall 1회 요청 건수. 응답 상한 16,000 byte에 geom_json이 최대 4,000 byte라
 *                       크게 잡으면 응답이 잘린다
 */
@ConfigurationProperties(prefix = "ridely.external.public-data-portal.taas")
public record TaasProperties(
        String baseUrl,
        String serviceKey,
        int timeoutSeconds,
        int searchYear,
        int maxRowsPerCall
) {
}
