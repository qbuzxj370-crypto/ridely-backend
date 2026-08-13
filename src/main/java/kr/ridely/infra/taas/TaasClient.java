package kr.ridely.infra.taas;

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
 * 도로교통공단 TAAS 자전거 교통사고 다발지역 클라이언트.
 *
 * 호출 형태 (GET):
 *   {baseUrl}/frequentzoneBicycle/getRestFrequentzoneBicycle
 *     ?serviceKey={키}&searchYearCd={연도}&siDo={시도코드}&guGun={시군구코드}
 *     &type=json&numOfRows={n}&pageNo={p}
 *
 * 선정 기준: 반경 200m 내 자전거사고 4건 이상 (사망사고 포함 시 3건 이상).
 *
 * ⚠️ 주의
 *   - 응답 구조가 TourAPI와 다르다. {@code response.header/body} 래핑이 없고
 *     정상 코드가 {@code "00"}이다
 *   - {@code siDo}·{@code guGun}은 법정동 코드를 쪼갠 값이다
 *     (서울 강남구 11680 → siDo=11, guGun=680)
 *   - {@code geom_json}이 GeoJSON 문자열로 온다. {@code ST_GeomFromGeoJSON()}에 그대로
 *     넣을 수 있다. 명세는 {@code Polygon}만 예시로 싣지만 {@code MultiPolygon}으로도 온다
 *   - {@code sido_sgg_nm}에 일련번호가 붙고 표기가 연도별로 흔들린다
 *     (2023 "서울 강남구1" vs 2022 "서울특별시 강남구1").
 *     표시용으로는 {@code spot_nm}, 지역 매핑은 {@code bjd_cd}를 쓴다
 *   - 포털 레벨 오류(키 미등록·한도 초과)는 {@code type=json}이어도 XML로 온다
 *
 * 명세와 실제가 어긋난 항목은 docs/shared/DATA_SOURCES.md 6.7에 정리했다.
 */
@Component
public class TaasClient {

    private static final Logger log = LoggerFactory.getLogger(TaasClient.class);

    private static final String PATH = "/frequentzoneBicycle/getRestFrequentzoneBicycle";

    /** 포털 레벨 오류 응답(XML)의 시작 태그 */
    private static final String PORTAL_ERROR_PREFIX = "<OpenAPI_ServiceResponse";

    /** 정상 결과 코드. ⚠️ 명세(XML)의 "0000"이 아니라 JSON은 "00"이다 */
    private static final String RESULT_CODE_OK = "00";

    /**
     * 데이터 없음. 오류가 아니다.
     *
     * ⚠️ 활용가이드 v1.1의 에러 코드표(1·10·12·20·22·30·31·32·99)에 03이 없다.
     * 공공데이터포털 공통 코드의 NODATA_ERROR인데 이 문서에는 누락돼 있다.
     * 사고다발지가 없는 자치구를 조회하면 이 코드가 돌아온다 — 실제로 흔하다.
     */
    private static final String RESULT_CODE_NO_DATA = "03";

    /** 한 자치구에서 도는 최대 페이지. 종료 조건이 깨져도 무한 호출로 번지지 않게 막는다 */
    private static final int MAX_PAGES_PER_DISTRICT = 20;

    private final WebClient webClient;
    private final TaasProperties properties;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public TaasClient(WebClient.Builder webClientBuilder, TaasProperties properties) {
        this.properties = properties;
        this.webClient = webClientBuilder.baseUrl(properties.baseUrl()).build();
    }

    /**
     * 서울 25개 자치구의 사고다발지역을 전량 수집한다.
     *
     * {@code guGun}이 필수라 시도 단위 조회가 불가능하다. 구별로 나눠 호출한다.
     * 호출 수는 25개 구 × 페이지 수이며, 데이터가 희소해 대개 구당 1페이지로 끝난다.
     *
     * @param year 사고년도. null이면 설정값
     */
    public List<TaasFrequentZoneResponse.Item> fetchSeoulZones(Integer year) {
        int searchYear = year != null ? year : properties.searchYear();
        List<TaasFrequentZoneResponse.Item> collected = new ArrayList<>();

        for (SeoulDistrict district : SeoulDistrict.all()) {
            List<TaasFrequentZoneResponse.Item> items = fetchDistrict(district, searchYear);
            collected.addAll(items);
            if (!items.isEmpty()) {
                log.debug("TAAS {} {}년: {}건", district.getKoreanName(), searchYear, items.size());
            }
        }

        log.info("TAAS 서울 사고다발지역 수집 완료: {}년 {}건 (자치구 {}개 조회)",
                searchYear, collected.size(), SeoulDistrict.all().size());
        return collected;
    }

    /** 자치구 하나를 페이지 끝까지 수집한다 */
    private List<TaasFrequentZoneResponse.Item> fetchDistrict(SeoulDistrict district, int year) {
        List<TaasFrequentZoneResponse.Item> collected = new ArrayList<>();
        int pageSize = properties.maxRowsPerCall();
        int totalCount = Integer.MAX_VALUE;

        for (int pageNo = 1; pageNo <= MAX_PAGES_PER_DISTRICT; pageNo++) {
            if (collected.size() >= totalCount) {
                break;
            }
            String body = fetchRaw(SeoulDistrict.SIDO_CODE_SEOUL, district.getCode(),
                    year, pageSize, pageNo);
            TaasFrequentZoneResponse parsed = parse(body, district);

            totalCount = parsed.totalCountOrZero();
            List<TaasFrequentZoneResponse.Item> items = parsed.itemList();
            if (items.isEmpty()) {
                break;
            }
            collected.addAll(items);
        }
        return collected;
    }

    /**
     * 응답 파싱 + 결과 코드 검사.
     *
     * 결과 코드가 "00"이 아니면 실패로 본다. 단 "03"(데이터 없음)은 예외다 —
     * 서울 25개 구 중 6개가 여기 해당하므로 정상 경로로 다룬다.
     */
    private TaasFrequentZoneResponse parse(String body, SeoulDistrict district) {
        TaasFrequentZoneResponse parsed;
        try {
            parsed = objectMapper.readValue(body, TaasFrequentZoneResponse.class);
        } catch (Exception e) {
            log.error("TAAS 응답 파싱 실패 ({}): {}", district.getKoreanName(), abbreviate(body), e);
            throw new BusinessException(ErrorCode.COMMON_500);
        }

        String code = parsed.getResultCode();
        if (RESULT_CODE_NO_DATA.equals(code)) {
            // 사고다발지가 없는 자치구. itemList()가 빈 목록을 돌려주므로 호출부가 알아서 멈춘다
            log.debug("TAAS {}: 데이터 없음", district.getKoreanName());
            return parsed;
        }
        if (!RESULT_CODE_OK.equals(code)) {
            log.error("TAAS 오류 응답 ({}): code={}, msg={}",
                    district.getKoreanName(), code, parsed.getResultMsg());
            throw new BusinessException(ErrorCode.COMMON_500);
        }
        return parsed;
    }

    /**
     * 사고다발지역 조회 (응답 원문).
     *
     * @param siDo      시도코드 (서울=11). null이면 파라미터를 보내지 않는다
     * @param guGun     시군구코드 (강남구=680). 필수다 — 빼면 결과가 비어 온다
     * @param year      사고년도. null이면 설정값 사용
     * @param numOfRows 한 페이지 결과 수
     * @param pageNo    페이지 번호 (1부터)
     */
    public String fetchRaw(String siDo, String guGun, Integer year, int numOfRows, int pageNo) {
        int searchYear = year != null ? year : properties.searchYear();

        String body = webClient.get()
                .uri(uri -> {
                    var builder = uri.path(PATH)
                            .queryParam("serviceKey", properties.serviceKey())
                            .queryParam("searchYearCd", searchYear)
                            .queryParam("type", "json")
                            .queryParam("numOfRows", numOfRows)
                            .queryParam("pageNo", pageNo);
                    // 값이 없으면 파라미터 자체를 보내지 않는다.
                    // 빈 문자열로 보내면 "전체"가 아니라 "빈 값과 일치"로 해석될 수 있다.
                    if (siDo != null && !siDo.isBlank()) {
                        builder.queryParam("siDo", siDo);
                    }
                    if (guGun != null && !guGun.isBlank()) {
                        builder.queryParam("guGun", guGun);
                    }
                    return builder.build();
                })
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
     * 키 미등록·활용기간 만료 등은 HTTP 200에 XML 본문으로 내려와 상태 코드로는 안 걸린다.
     * TourApiClient와 같은 함정이라 같은 방식으로 거른다.
     */
    private void verifyNotPortalError(String body) {
        if (body == null || body.isBlank()) {
            log.error("TAAS 응답이 비어 있음");
            throw new BusinessException(ErrorCode.COMMON_500);
        }
        String head = body.stripLeading();
        if (head.startsWith(PORTAL_ERROR_PREFIX) || head.startsWith("<")) {
            // 원인(SERVICE_KEY_IS_NOT_REGISTERED_ERROR 등)이 본문에 있으므로 그대로 남긴다
            log.error("TAAS 포털 오류 응답: {}", abbreviate(body));
            throw new BusinessException(ErrorCode.COMMON_500);
        }
    }

    private String abbreviate(String body) {
        return body.length() <= 500 ? body : body.substring(0, 500) + "...(생략)";
    }
}
