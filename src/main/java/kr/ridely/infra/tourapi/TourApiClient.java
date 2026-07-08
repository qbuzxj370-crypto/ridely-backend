package kr.ridely.infra.tourapi;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 한국관광공사 TourAPI 클라이언트.
 * MVP는 locationBasedList1(위치 기반 관광정보)만 사용.
 *
 * 호출 형태 (GET):
 *   {baseUrl}/locationBasedList1
 *     ?serviceKey={키}&MobileOS=ETC&MobileApp=Ridely&_type=json
 *     &mapX={경도}&mapY={위도}&radius={미터}&contentTypeId={12|14|39}
 *
 * ⚠️ 주의:
 *   - serviceKey는 URL 인코딩된 상태로 발급됨 → WebClient가 이중 인코딩하지 않도록
 *     UriComponentsBuilder.build(true) 또는 인코딩 모드 조정 필요 (자주 겪는 트러블)
 *   - mapX=경도(lng), mapY=위도(lat) — 순서 헷갈리기 쉬움
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
     * 위치 기반 관광 콘텐츠 조회 (locationBasedList1).
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
