package kr.ridely.infra.seoul;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import kr.ridely.common.BusinessException;
import kr.ridely.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 서울 열린데이터광장 OpenAPI 클라이언트.
 *
 * 호출 형태 (GET) — 인증키가 쿼리 파라미터가 아니라 <b>경로</b>에 들어간다:
 *   {baseUrl}/{인증키}/{json|xml}/{서비스명}/{시작위치}/{종료위치}/
 *
 * ⚠️ 주의:
 *   - 위치는 1부터 시작하고 끝 번호를 포함한다. 1/1000 → 1~1000번째 (1000건)
 *   - 한 번에 1,000건을 넘기면 ERROR-336. 전량 수집은 구간을 나눠 반복 호출한다
 *   - 인증키가 틀리면 json을 요청해도 XML로 응답한다(TourAPI와 같은 함정).
 *     본문이 "<"로 시작하면 오류로 분기해야 한다
 *   - https를 지원하지 않는다. http + 8088 포트 고정
 *   - 응답이 수 MB에 달해 WebClient 기본 버퍼(256KB)로는 받을 수 없다
 */
@Component
public class SeoulOpenApiClient {

    private static final Logger log = LoggerFactory.getLogger(SeoulOpenApiClient.class);

    /** 자전거 편의시설 서비스명 (거치대·공기주입기·수리센터 통합 제공) */
    private static final String SERVICE_BICYCLE_ETC = "tvBicycleEtc";

    /** 정상 처리 코드 */
    private static final String RESULT_CODE_OK = "INFO-000";

    /** 조건에 맞는 데이터 없음. 오류가 아니라 정상 상황으로 취급한다 */
    private static final String RESULT_CODE_NO_DATA = "INFO-200";

    /** 응답 버퍼 상한. 1,000건 응답이 수 MB라 기본값(256KB)으로는 부족하다 */
    private static final int MAX_RESPONSE_BYTES = 16 * 1024 * 1024;

    /**
     * 전량 수집 시 반복 호출 상한.
     * 총 건수를 잘못 읽어 종료 조건이 깨져도 무한 호출로 번지지 않게 막는다.
     */
    private static final int MAX_PAGES = 50;

    private final WebClient webClient;
    private final SeoulOpenApiProperties properties;

    /**
     * 전용 ObjectMapper를 쓰는 이유:
     * 선언하지 않은 응답 필드(이미지 URL, 평가 점수 등)를 무시해야 하는데
     * 전역 ObjectMapper 설정을 바꾸면 우리 API 전체의 역직렬화 동작이 달라진다.
     */
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public SeoulOpenApiClient(WebClient.Builder webClientBuilder, SeoulOpenApiProperties properties) {
        this.properties = properties;
        this.webClient = webClientBuilder
                .baseUrl(properties.baseUrl())
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(MAX_RESPONSE_BYTES))
                .build();
    }

    /**
     * 자전거 편의시설 전량 수집.
     *
     * 1회 호출 상한(1,000건)을 넘는 데이터라 구간을 나눠 반복 호출한다.
     * 첫 응답의 총 건수를 보고 필요한 만큼만 더 요청한다.
     *
     * @return 수집된 전체 행. 순서는 API 응답 순서를 유지한다
     */
    public List<SeoulBicycleEtcResponse.Row> fetchAllBicycleEtc() {
        int pageSize = properties.maxRowsPerCall();
        List<SeoulBicycleEtcResponse.Row> collected = new ArrayList<>();

        int totalCount = Integer.MAX_VALUE;
        for (int page = 0; page < MAX_PAGES; page++) {
            int startIndex = page * pageSize + 1;
            if (startIndex > totalCount) {
                break;
            }

            SeoulBicycleEtcResponse response = fetchBicycleEtc(startIndex, startIndex + pageSize - 1);
            totalCount = response.totalCount();

            List<SeoulBicycleEtcResponse.Row> rows = response.rows();
            if (rows.isEmpty()) {
                break;
            }
            collected.addAll(rows);

            log.debug("서울 자전거 편의시설 수집: {}~{} → {}건 (누적 {}/{})",
                    startIndex, startIndex + rows.size() - 1, rows.size(), collected.size(), totalCount);
        }

        log.info("서울 자전거 편의시설 수집 완료: {}건", collected.size());
        return collected;
    }

    /**
     * 자전거 편의시설 구간 조회.
     *
     * @param startIndex 시작 위치 (1부터)
     * @param endIndex   종료 위치 (포함). startIndex와의 차이가 1,000을 넘으면 ERROR-336
     */
    public SeoulBicycleEtcResponse fetchBicycleEtc(int startIndex, int endIndex) {
        String body = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(properties.apiKey(), "json", SERVICE_BICYCLE_ETC,
                                String.valueOf(startIndex), String.valueOf(endIndex))
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .block();

        return parse(body);
    }

    /**
     * 응답 파싱 + 결과 코드 검사.
     *
     * 인증 실패·파라미터 오류는 HTTP 200에 XML 본문으로 내려오기 때문에
     * 상태 코드 검사로는 걸러지지 않는다. 본문을 직접 확인해야 한다.
     */
    private SeoulBicycleEtcResponse parse(String body) {
        if (body == null || body.isBlank()) {
            log.error("서울 열린데이터광장 응답이 비어 있음");
            throw new BusinessException(ErrorCode.COMMON_500);
        }
        if (body.stripLeading().startsWith("<")) {
            // 인증키 오류(INFO-100) 등. 원인이 본문에 들어 있으므로 그대로 남긴다
            log.error("서울 열린데이터광장 오류 응답(XML): {}", abbreviate(body));
            throw new BusinessException(ErrorCode.COMMON_500);
        }

        SeoulBicycleEtcResponse parsed;
        try {
            parsed = objectMapper.readValue(body, SeoulBicycleEtcResponse.class);
        } catch (Exception e) {
            log.error("서울 열린데이터광장 응답 파싱 실패: {}", abbreviate(body), e);
            throw new BusinessException(ErrorCode.COMMON_500);
        }

        String code = parsed.resultCode();
        if (code != null && !RESULT_CODE_OK.equals(code) && !RESULT_CODE_NO_DATA.equals(code)) {
            log.error("서울 열린데이터광장 오류 응답: code={}, msg={}", code, parsed.resultMessage());
            throw new BusinessException(ErrorCode.COMMON_500);
        }
        return parsed;
    }

    /** 로그에 응답 전문이 쏟아지지 않도록 앞부분만 남긴다 */
    private String abbreviate(String body) {
        return body.length() <= 500 ? body : body.substring(0, 500) + "...(생략)";
    }
}
