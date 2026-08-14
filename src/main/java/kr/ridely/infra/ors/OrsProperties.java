package kr.ridely.infra.ors;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * openrouteservice 설정 바인딩. application.yml의 ridely.external.ors.* 값을 주입받는다.
 *
 * 공공데이터가 아니라 외부 상용 서비스다. 출처표시 대상은 아니지만 무료 한도가 따로 걸린다 — docs/shared/DATA_SOURCES.md 4장
 *
 * @param baseUrl        https://api.openrouteservice.org
 * @param apiKey         발급 키 (ORS_API_KEY). Authorization 헤더에 그대로 넣는다
 * @param profile        라우팅 프로파일. 자전거는 cycling-regular
 * @param timeoutSeconds 호출 타임아웃. W3 기준 10초를 넘기면 그냥 실패시킨다
 */
@ConfigurationProperties(prefix = "ridely.external.ors")
public record OrsProperties(
        String baseUrl,
        String apiKey,
        String profile,
        int timeoutSeconds
) {
}
