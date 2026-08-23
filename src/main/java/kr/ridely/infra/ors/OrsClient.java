package kr.ridely.infra.ors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import kr.ridely.common.BusinessException;
import kr.ridely.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * openrouteservice 자전거 경로 탐색 클라이언트.
 *
 * 호출 형태 (POST):
 *   {baseUrl}/v2/directions/{profile}/geojson
 *   Authorization: {키}          ← Bearer가 아니라 키를 그대로 넣는다
 *   Content-Type: application/json
 *   { "coordinates": [[경도,위도], ...], "elevation": true, "instructions": false }
 *
 * 이 클래스가 존재하는 이유는 LLM이 좌표를 직접 만들지 않게 하기 위해서다.
 * AI는 "어디를 경유할지"만 정하고 실제 길찾기는 라우팅 엔진이 수행한다.
 * 환각으로 강 위를 지나는 좌표가 출력되는 상황을 방지하기 위한 구조적 장치
 *
 * ⚠️ 주의
 *   - 좌표는 [경도, 위도] 순이다. 뒤집으면 조용히 엉뚱한 경로가 나온다
 *   - 경유지를 포함해 순서대로 넘긴다 (출발 → 경유 → 도착). ORS가 그 순서를 지킨다. 최적 순서 재배열은 하지 않는다 — 경유지 순서는 LLM이 정한 설계 의도다
 *   - 무료 한도가 있다(directions 기준 분당·일일 제한). 같은 요청을 반복하지 말 것. 개발 중에는 응답 JSON을 로컬에 저장해 재사용한다
 *   - avoid_polygons를 넘기면 그 영역을 지나지 않는 경로를 그린다. 넘기는 도형이 길을 완전히 막으면 경로를 못 찾아 실패하므로, 호출부가 회피 없는 재시도를 준비해야 한다
 */
@Component
public class OrsClient {

    private static final Logger log = LoggerFactory.getLogger(OrsClient.class);

    private static final String PATH_TEMPLATE = "/v2/directions/%s/geojson";

    /** 경로 하나를 그리려면 최소 출발·도착 두 점이 필요하다 */
    private static final int MIN_COORDINATES = 2;

    private final WebClient webClient;
    private final OrsProperties properties;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public OrsClient(WebClient.Builder webClientBuilder, OrsProperties properties) {
        this.properties = properties;
        this.webClient = webClientBuilder.baseUrl(properties.baseUrl()).build();
    }

    /**
     * 좌표 순서대로 자전거 경로를 그린다.
     *
     * @param coordinates [경도, 위도] 쌍의 목록. 출발 → 경유 → 도착 순서 그대로 전달된다
     * @return 형상·거리·시간·고도를 추린 결과
     */
    public OrsRouteResult route(List<double[]> coordinates) {
        return route(coordinates, null);
    }

    /**
     * 특정 영역을 지나지 않는 자전거 경로를 그린다.
     *
     * ⚠️ 넘긴 도형이 길을 완전히 막으면 경로를 못 찾아 실패한다. 사고다발지는 교차로에 생기고 그 교차로가 유일한 통로일 수 있다. <b>호출부는 회피 없는 재시도를 준비해야 한다.</b>
     *
     * @param avoidPolygonsGeoJson 회피할 영역. GeoJSON Polygon 또는 MultiPolygon 문자열. null이면 회피하지 않는다
     */
    public OrsRouteResult route(List<double[]> coordinates, String avoidPolygonsGeoJson) {
        String body = fetchRaw(coordinates, avoidPolygonsGeoJson);
        OrsDirectionsResponse parsed = parse(body);
        return toResult(parsed);
    }

    /**
     * 응답 원문을 그대로 돌려준다. PoC·디버깅용이다.
     *
     * 파싱에서 무엇이 빠지는지 눈으로 확인할 때 쓴다. 정식 경로는 {@link #route}다.
     */
    public String fetchRaw(List<double[]> coordinates) {
        return fetchRaw(coordinates, null);
    }

    private String fetchRaw(List<double[]> coordinates, String avoidPolygonsGeoJson) {
        verifyRequest(coordinates);

        // 순서를 유지해야 요청 본문이 예측 가능해진다. 로그 대조와 재현에 필요하다
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("coordinates", coordinates.stream().map(c -> List.of(c[0], c[1])).toList());
        // 고도는 강도 판정에 쓰지 않지만 응답에는 담아야 해서 켠다 — SCHEMA_CHANGE_POI 6.5
        payload.put("elevation", true);
        // 턴바이턴 안내는 쓰지 않는다. 응답 크기를 크게 줄인다
        payload.put("instructions", false);
        if (avoidPolygonsGeoJson != null) {
            payload.put("options", Map.of("avoid_polygons", toGeometry(avoidPolygonsGeoJson)));
        }

        String path = PATH_TEMPLATE.formatted(properties.profile());

        String body = webClient.post()
                .uri(path)
                // ⚠️ Bearer 접두어를 붙이지 않는다. ORS는 키를 헤더 값으로 그대로 받는다
                .header("Authorization", properties.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .block();

        verifyNotEmpty(body);
        return body;
    }

    /**
     * GeoJSON 문자열을 요청 본문에 실을 수 있는 형태로 바꾼다.
     *
     * 문자열을 그대로 넣으면 JSON 안에 따옴표로 감싼 문자열이 되어 라우팅 엔진이 형식 오류로 거부한다. 객체로 풀어야 중첩 JSON이 된다.
     *
     * ⚠️ PostGIS가 돌려주는 도형은 MultiPolygon이다(accident_zone.polygon_geom을 그 타입으로 통일했다). 좌표 배열을 한 번 더 감싸면 중첩이 깊어져 같은 형식 오류가 난다 — 여기서 파싱만 하고 구조는 손대지 않는 이유다.
     */
    private Map<String, Object> toGeometry(String geoJson) {
        try {
            return objectMapper.readValue(geoJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            log.error("회피 도형 GeoJSON을 읽지 못했다: {}", e.getMessage());
            throw new BusinessException(ErrorCode.COMMON_500);
        }
    }

    private void verifyRequest(List<double[]> coordinates) {
        if (coordinates == null || coordinates.size() < MIN_COORDINATES) {
            log.error("ORS 좌표가 부족하다: {}개 (최소 {}개)",
                    coordinates == null ? 0 : coordinates.size(), MIN_COORDINATES);
            throw new BusinessException(ErrorCode.COMMON_500);
        }
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            log.error("ORS_API_KEY가 비어 있다. .env를 확인한다");
            throw new BusinessException(ErrorCode.COMMON_500);
        }
    }

    private void verifyNotEmpty(String body) {
        if (body == null || body.isBlank()) {
            log.error("ORS 응답이 비어 있음");
            throw new BusinessException(ErrorCode.COMMON_500);
        }
    }

    private OrsDirectionsResponse parse(String body) {
        try {
            return objectMapper.readValue(body, OrsDirectionsResponse.class);
        } catch (Exception e) {
            log.error("ORS 응답 파싱 실패: {}", abbreviate(body), e);
            throw new BusinessException(ErrorCode.COMMON_500);
        }
    }

    /**
     * 응답에서 필요한 값만 뽑는다.
     *
     * 형상은 다시 JSON 문자열로 직렬화해 넘긴다. ST_GeomFromGeoJSON에 그대로 넣기 위해서다.
     */
    private OrsRouteResult toResult(OrsDirectionsResponse parsed) {
        OrsDirectionsResponse.Feature feature = parsed.firstFeature();
        if (feature == null || feature.getGeometry() == null) {
            log.error("ORS 응답에 경로 형상이 없다");
            throw new BusinessException(ErrorCode.COMMON_500);
        }

        OrsDirectionsResponse.Geometry geometry = feature.getGeometry();
        List<List<Double>> coords = geometry.coordinatesOrEmpty();
        if (coords.size() < MIN_COORDINATES) {
            log.error("ORS 경로 좌표가 {}개뿐이다", coords.size());
            throw new BusinessException(ErrorCode.COMMON_500);
        }

        String geometryJson;
        try {
            geometryJson = objectMapper.writeValueAsString(geometry);
        } catch (Exception e) {
            log.error("ORS 형상 직렬화 실패", e);
            throw new BusinessException(ErrorCode.COMMON_500);
        }

        OrsRouteResult result = new OrsRouteResult();
        result.setGeometryGeoJson(geometryJson);
        result.setPointCount(coords.size());

        OrsDirectionsResponse.Properties props = feature.getProperties();
        if (props != null && props.getSummary() != null) {
            result.setDistanceM(orZero(props.getSummary().getDistance()));
            result.setDurationSec(orZero(props.getSummary().getDuration()));
        }
        if (props != null) {
            result.setAscentM(orZero(props.getAscent()));
            result.setDescentM(orZero(props.getDescent()));
        }

        if (result.getDistanceM() <= 0) {
            log.error("ORS 거리가 0이다. 출발지와 도착지가 같거나 경로를 못 찾았다");
            throw new BusinessException(ErrorCode.COMMON_500);
        }

        log.debug("ORS 경로: {}점, {}m, {}초, 상승 {}m",
                result.getPointCount(), result.getDistanceM(),
                result.getDurationSec(), result.getAscentM());
        return result;
    }

    private double orZero(Double value) {
        return value == null ? 0.0 : value;
    }

    private String abbreviate(String body) {
        return body.length() <= 500 ? body : body.substring(0, 500) + "...(생략)";
    }
}
