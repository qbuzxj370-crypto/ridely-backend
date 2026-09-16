package kr.ridely.infra.kakao;

import kr.ridely.common.BusinessException;
import kr.ridely.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;

/**
 * Kakao Local 장소 검색 클라이언트.
 *
 * 호출 형태 (GET):
 * <pre>
 * {localBaseUrl}/v2/local/search/keyword.json?query={검색어}&amp;size={건수}
 * Authorization: KakaoAK {키}     ← Bearer가 아니다
 * </pre>
 *
 * 쓰임은 출발지 검색 하나다. 사용자가 「여의도한강공원」이라고 치면 좌표를 찾아 추천 요청에 실을 수 있게 한다. 지도 렌더링은 프론트가 JS 키로 직접 하므로 백엔드를 거치지 않는다.
 *
 * <h3>외부 실패를 502로 바꾼다</h3>
 *
 * <b>호출이 실패했다는 사실 자체가 응답에 드러나야 한다.</b> 그냥 던지면 {@code GlobalExceptionHandler}의 마지막 그물에 걸려 {@code COMMON-500}이 나가고, 클라이언트는 우리 서버가 깨진 것으로 읽는다. 재시도해도 되는 상황인지 판단할 근거가 사라진다.
 *
 * ⚠️ <b>결과 0건은 실패가 아니다.</b> 오타나 없는 장소를 찾은 것이라 정상 응답이고, 빈 목록을 그대로 올려보낸다. 이 클래스가 502로 바꾸는 것은 연결·타임아웃·상태 코드 오류뿐이다.
 */
@Component
public class KakaoLocalClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoLocalClient.class);

    private static final String KEYWORD_PATH = "/v2/local/search/keyword.json";

    /** Kakao는 키를 이 접두어와 함께 받는다 */
    private static final String AUTH_PREFIX = "KakaoAK ";

    private final WebClient webClient;
    private final KakaoProperties properties;

    public KakaoLocalClient(WebClient.Builder webClientBuilder, KakaoProperties properties) {
        this.properties = properties;
        this.webClient = webClientBuilder.baseUrl(properties.localBaseUrl()).build();
    }

    /**
     * 검색어로 장소를 찾는다.
     *
     * @param query 장소명 또는 주소
     * @param size  최대 건수
     * @return 검색 결과. 없으면 빈 목록
     * @throws BusinessException 외부 호출이 실패하면 GEO-001(502)
     */
    public List<KakaoKeywordResponse.Document> searchKeyword(String query, int size) {
        KakaoKeywordResponse response;
        try {
            response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(KEYWORD_PATH)
                            .queryParam("query", query)
                            .queryParam("size", size)
                            .build())
                    .header("Authorization", AUTH_PREFIX + properties.restApiKey())
                    .retrieve()
                    .bodyToMono(KakaoKeywordResponse.class)
                    .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                    .block();

        } catch (WebClientResponseException e) {
            /*
             * 상태 코드만으로는 원인을 못 찾는다. Kakao는 키 오류와 호출 IP 미등록을 모두
             * 401로 주고 구분은 본문에만 있다.
             *
             *   {"errorType":"AccessDeniedError","message":"ip mismatched! callerIp=..."}
             *
             * 2026-09-15에 이것 때문에 두 번 헛돌았다. 본문을 남기면 한 번에 잡힌다.
             * 키는 요청 헤더에 있고 응답 본문에는 들어가지 않으므로 그대로 찍어도 된다.
             */
            log.error("장소 검색 호출 실패: query={}, {} {}",
                    query, e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.GEO_001);

        } catch (RuntimeException e) {
            /*
             * ⚠️ 위 catch만 두면 타임아웃을 놓친다.
             *
             * Mono.timeout이 내보내는 것은 java.util.concurrent.TimeoutException이고,
             * 검사 예외라 block()이 Reactor 예외로 감싸서 던진다. 둘 다 WebClientException
             * 계열이 아니라 그물을 빠져나가 COMMON-500이 된다.
             *
             * 여기 try 안에 있는 것은 이 호출 하나뿐이라 넓게 잡아도 우리 코드의 버그를
             * 삼키지 않는다. 응답을 읽는 부분은 일부러 밖에 뒀다.
             *
             * 키가 틀린 401도 여기로 온다. 사용자에게는 연결 실패와 똑같이 "검색을 못 했다"라서
             * 구분할 이유가 없고, 우리가 볼 근거는 로그에 남는다.
             */
            log.error("장소 검색 호출 실패: query={}, {}", query, e.toString());
            throw new BusinessException(ErrorCode.GEO_001);
        }

        if (response == null || response.documents() == null) {
            // 200인데 본문이 비었다. 명세에 없는 상태라 실패로 본다
            log.warn("장소 검색 응답이 비어 있다: query={}", query);
            throw new BusinessException(ErrorCode.GEO_001);
        }
        return response.documents();
    }
}
