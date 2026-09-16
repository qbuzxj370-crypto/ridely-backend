package kr.ridely.infra.kakao;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kakao Local API 설정 바인딩. application.yml의 ridely.external.kakao.* 값을 주입받는다.
 *
 * 주소·장소명을 좌표로 바꾸는 데만 쓴다. 지도 렌더링은 프론트가 별도 키(JS)로 직접 한다.
 *
 * @param localBaseUrl   https://dapi.kakao.com
 * @param restApiKey     백엔드 전용 REST 키 (KAKAO_REST_API_KEY). Authorization 헤더에
 *                       {@code KakaoAK } 접두어를 붙여 보낸다
 * @param jsApiKey       프론트 전용 키. 백엔드는 쓰지 않지만 한곳에서 관리하려고 여기 둔다
 * @param timeoutSeconds 호출 타임아웃
 */
@ConfigurationProperties(prefix = "ridely.external.kakao")
public record KakaoProperties(
        String localBaseUrl,
        String restApiKey,
        String jsApiKey,
        int timeoutSeconds
) {
}
