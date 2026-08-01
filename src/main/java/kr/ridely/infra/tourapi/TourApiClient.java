package kr.ridely.infra.tourapi;

import kr.ridely.common.BusinessException;
import kr.ridely.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * 한국관광공사 TourAPI 클라이언트 (활용매뉴얼 v4.4 / KorService2 기준).
 * MVP는 locationBasedList2(위치 기반 관광정보)만 사용.
 *
 * 호출 형태 (GET):
 *   {baseUrl}/locationBasedList2
 *     ?serviceKey={키}&MobileOS=ETC&MobileApp=Ridely&_type=json
 *     &mapX={경도}&mapY={위도}&radius={미터}&contentTypeId={12|14|39}
 *
 * ⚠️ 주의:
 *   - mapX=경도(lng), mapY=위도(lat) — 순서 헷갈리기 쉬움
 *   - radius 최대 20000(m)
 *   - MobileApp은 활용 통계 산출용 필수 항목 (매뉴얼 명시)
 *   - 포털 레벨 오류(키 미등록·한도 초과)는 _type=json이어도 XML로 응답한다.
 *     본문이 "<OpenAPI_ServiceResponse"로 시작하면 오류로 분기해야 한다.
 *   - 현재 발급되는 서비스 키는 영숫자 조합이라 이중 인코딩 문제가 없다.
 *     WebClient 기본 동작(queryParam 자동 인코딩)을 그대로 쓰면 된다.
 */
@Component
public class TourApiClient {

    private static final Logger log = LoggerFactory.getLogger(TourApiClient.class);

    /** 매뉴얼상 radius 상한 (m) */
    private static final int MAX_RADIUS_M = 20000;

    /** 포털 레벨 오류 응답(XML)의 시작 태그 */
    private static final String PORTAL_ERROR_PREFIX = "<OpenAPI_ServiceResponse";

    private final WebClient webClient;
    private final TourApiProperties properties;

    public TourApiClient(WebClient.Builder webClientBuilder, TourApiProperties properties) {
        this.properties = properties;
        this.webClient = webClientBuilder.baseUrl(properties.baseUrl()).build();
    }

    /**
     * 위치 기반 관광 콘텐츠 조회 (첫 페이지 20건).
     * 단건 확인·PoC용 단축 메서드.
     */
    public String fetchNearbySpots(double lng, double lat, int radiusM, int contentTypeId) {
        return fetchNearbySpots(lng, lat, radiusM, contentTypeId, 1, 20);
    }

    /**
     * 위치 기반 관광 콘텐츠 조회 (locationBasedList2).
     *
     * @param lng           경도 (mapX)
     * @param lat           위도 (mapY)
     * @param radiusM       반경 (미터, 최대 20000)
     * @param contentTypeId 12=관광지, 14=문화시설, 39=음식점
     * @param pageNo        페이지 번호 (1부터)
     * @param numOfRows     한 페이지 결과 수
     * @return 응답 JSON 원문
     */
    public String fetchNearbySpots(double lng, double lat, int radiusM, int contentTypeId,
                                   int pageNo, int numOfRows) {
        int radius = Math.min(radiusM, MAX_RADIUS_M);

        String body = webClient.get()
                .uri(uri -> uri.path("/locationBasedList2")
                        .queryParam("serviceKey", properties.serviceKey())
                        .queryParam("MobileOS", "ETC")
                        .queryParam("MobileApp", "Ridely")
                        .queryParam("_type", "json")
                        .queryParam("mapX", lng)
                        .queryParam("mapY", lat)
                        .queryParam("radius", radius)
                        .queryParam("contentTypeId", contentTypeId)
                        .queryParam("arrange", "E")       // 거리순
                        .queryParam("numOfRows", numOfRows)
                        .queryParam("pageNo", pageNo)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .block();

        verifyNotPortalError(body);
        return body;
    }

    /**
     * 포털 레벨 오류 검사.
     *
     * 키 미등록·한도 초과 등은 HTTP 200에 XML 본문으로 내려오기 때문에 WebClient의 상태 코드 검사로는 걸러지지 않는다.
     * 본문을 직접 확인해야 한다.
     */
    private void verifyNotPortalError(String body) {
        if (body == null || body.isBlank()) {
            log.error("TourAPI 응답이 비어 있음");
            throw new BusinessException(ErrorCode.COMMON_500);
        }
        if (body.stripLeading().startsWith(PORTAL_ERROR_PREFIX)) {
            // 원인(등록되지 않은 서비스키, 한도 초과 등)이 본문에 들어 있으므로 그대로 남긴다
            log.error("TourAPI 포털 오류 응답: {}", body);
            throw new BusinessException(ErrorCode.COMMON_500);
        }
    }
}
