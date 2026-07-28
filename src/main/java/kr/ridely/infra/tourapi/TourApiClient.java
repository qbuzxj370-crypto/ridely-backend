package kr.ridely.infra.tourapi;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

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

    private final WebClient webClient;
    private final TourApiProperties properties;

    public TourApiClient(WebClient.Builder webClientBuilder, TourApiProperties properties) {
        this.properties = properties;
        // TODO: baseUrl 설정 + 타임아웃 적용
        this.webClient = webClientBuilder.baseUrl(properties.baseUrl()).build();
    }

    /**
     * 위치 기반 관광 콘텐츠 조회 (locationBasedList2).
     *
     * @param lng           경도 (mapX)
     * @param lat           위도 (mapY)
     * @param radiusM       반경 (미터, 최대 20000)
     * @param contentTypeId 12=관광지, 14=문화시설, 39=음식점
     * @return 응답 JSON 원문 (PoC 단계 — 이후 DTO 파싱으로 발전)
     */
    public String fetchNearbySpots(double lng, double lat, int radiusM, int contentTypeId) {
        // TODO(C6 PoC):
        //   1. queryParam 조립 (serviceKey, MobileOS=ETC, MobileApp=Ridely, _type=json,
        //      mapX, mapY, radius, contentTypeId)
        //   2. webClient.get().uri(...).retrieve().bodyToMono(String.class).block()
        //   3. 서비스 키 이중 인코딩 문제 발생 시 위 클래스 주석 참고
        // TODO(2주차 이후): String → TourApiResponse DTO 파싱, tour_spot 테이블 배치 적재
        throw new UnsupportedOperationException("TODO: C6 PoC에서 구현");
    }
}
