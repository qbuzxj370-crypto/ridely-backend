package kr.ridely.infra.durunubi;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
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
 * 한국관광공사 두루누비 정보 서비스 클라이언트 (활용매뉴얼 v4.1 기준).
 *
 * ★ 스파이크 전용. 채택이 기각되면 이 패키지를 통째로 삭제한다.
 *
 * <p>오퍼레이션 2종:
 * <pre>
 *   GET {baseUrl}/routeList   길 목록  (brdDiv=DNBW 로 자전거길만)
 *   GET {baseUrl}/courseList  코스 목록 (routeIdx로 길에 속한 코스, gpxpath 포함)
 * </pre>
 *
 * <p>⚠️ 주의:
 * <ul>
 *   <li>MobileOS·MobileApp이 <b>필수</b>다. 빠지면 오류가 난다</li>
 *   <li>포털 레벨 오류(키 미등록·한도 초과)는 {@code _type=json}이어도 XML로 온다.
 *       TourApiClient와 같은 함정이라 같은 방식으로 거른다</li>
 *   <li>데이터가 없으면 {@code items}가 객체가 아니라 <b>빈 문자열</b>로 오는 경우가 있다.
 *       TourAPI 계열의 고질적인 응답 편차라 JsonNode로 방어적으로 읽는다</li>
 *   <li>item이 1건이면 배열이 아니라 <b>객체</b>로 오는 경우가 있다. 둘 다 받아야 한다</li>
 *   <li>gpxpath는 data.go.kr이 아니라 durunubi.kr 도메인이다. 별도 WebClient로 받는다</li>
 * </ul>
 *
 * <p>응답을 POJO로 바인딩하지 않고 JsonNode로 훑는 이유:
 * 스파이크의 목적이 "응답이 어떻게 생겼는지 확인"이라 스키마를 미리 고정하면
 * 예상과 다를 때 파싱부터 깨진다. 채택이 확정되면 그때 POJO로 정리한다.
 */
@Component
public class DurunubiClient {

    private static final Logger log = LoggerFactory.getLogger(DurunubiClient.class);

    /** 자전거길 구분 코드 (DNWW=걷기길) */
    public static final String BRD_DIV_BIKE = "DNBW";

    /** 포털 레벨 오류 응답(XML)의 시작 태그 */
    private static final String PORTAL_ERROR_PREFIX = "<OpenAPI_ServiceResponse";

    /** 한 페이지 요청 건수. 매뉴얼상 상한 명시가 없어 보수적으로 잡는다 */
    private static final int PAGE_SIZE = 100;

    /** 전량 수집 시 반복 호출 상한. 종료 조건이 깨져도 무한 호출로 번지지 않게 막는다 */
    private static final int MAX_PAGES = 30;

    /** GPX 본문이 수 MB에 달할 수 있어 기본 버퍼(256KB)로는 부족하다 */
    private static final int MAX_RESPONSE_BYTES = 32 * 1024 * 1024;

    private final WebClient apiClient;
    private final WebClient fileClient;
    private final DurunubiProperties properties;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public DurunubiClient(WebClient.Builder webClientBuilder, DurunubiProperties properties) {
        this.properties = properties;
        this.apiClient = webClientBuilder
                .baseUrl(properties.baseUrl())
                .codecs(c -> c.defaultCodecs().maxInMemorySize(MAX_RESPONSE_BYTES))
                .build();
        // gpxpath는 절대 URL로 오므로 baseUrl을 두지 않는다
        this.fileClient = webClientBuilder
                .codecs(c -> c.defaultCodecs().maxInMemorySize(MAX_RESPONSE_BYTES))
                .build();
    }

    /**
     * 자전거길(brdDiv=DNBW) 전량 조회.
     *
     * @return item 노드 목록. 응답이 비어 있으면 빈 목록
     */
    public List<JsonNode> fetchAllBikeRoutes() {
        return fetchAllPages("/routeList", null, BRD_DIV_BIKE);
    }

    /**
     * 코스 <b>전량</b> 조회 (필터 없음).
     *
     * <p>brdDiv 필터를 쓰지 않는 이유: 실측에서 동작하지 않는 것을 확인했다.
     * DNBW를 요청해도 {@code brdDiv:"DNWW"}인 걷기 코스가 돌아온다.
     * 필터를 서버에 맡기지 않고 전량을 받아 응답 값으로 직접 거른다.
     */
    public List<JsonNode> fetchAllCourses() {
        return fetchAllPages("/courseList", null, null);
    }

    /**
     * 특정 길에 속한 코스 목록.
     *
     * @param routeIdx 길 고유번호 (routeList 응답의 routeIdx)
     */
    public List<JsonNode> fetchCourses(String routeIdx) {
        return fetchAllPages("/courseList", routeIdx, BRD_DIV_BIKE);
    }

    /**
     * 오퍼레이션 원문 1페이지. 응답 형태 눈으로 확인하는 용도.
     *
     * <p>brdDiv를 호출자가 정하게 열어 뒀다. 필터를 뺀 응답과 비교해야
     * "데이터가 없는 것"과 "필터가 안 맞는 것"을 구분할 수 있기 때문이다.
     *
     * @param operation "routeList" 또는 "courseList"
     * @param brdDiv    null/빈 값이면 파라미터 자체를 보내지 않는다
     */
    public String fetchRaw(String operation, String routeIdx, String brdDiv, int numOfRows) {
        return call("/" + operation, routeIdx, brdDiv, 1, numOfRows);
    }

    /**
     * GPX 파일 다운로드.
     *
     * @param gpxPath courseList 응답의 gpxpath (절대 URL)
     * @return 파일 본문. 실패 시 null (스파이크에서는 실패도 결과이므로 예외를 던지지 않는다)
     */
    public String downloadGpx(String gpxPath) {
        if (gpxPath == null || gpxPath.isBlank()) {
            return null;
        }
        try {
            return fileClient.get()
                    .uri(gpxPath)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(properties.gpxTimeoutSeconds()))
                    .block();
        } catch (Exception e) {
            log.warn("GPX 다운로드 실패: {} ({})", gpxPath, e.getMessage());
            return null;
        }
    }

    /** 페이지를 끝까지 돌며 item을 모은다 */
    private List<JsonNode> fetchAllPages(String path, String routeIdx, String brdDiv) {
        List<JsonNode> collected = new ArrayList<>();
        int pageNo = 1;
        int totalCount = Integer.MAX_VALUE;

        while (pageNo <= MAX_PAGES && collected.size() < totalCount) {
            String body = call(path, routeIdx, brdDiv, pageNo, PAGE_SIZE);
            JsonNode root = readTree(body);
            JsonNode responseBody = root.path("response").path("body");

            int reported = responseBody.path("totalCount").asInt(0);
            if (reported > 0) {
                totalCount = reported;
            }

            List<JsonNode> items = extractItems(responseBody);
            if (items.isEmpty()) {
                break;
            }
            collected.addAll(items);
            pageNo++;
        }
        log.info("두루누비 {} 수집 완료: {}건 (routeIdx={})", path, collected.size(), routeIdx);
        return collected;
    }

    private String call(String path, String routeIdx, String brdDiv, int pageNo, int numOfRows) {
        String body = apiClient.get()
                .uri(uri -> {
                    var b = uri.path(path)
                            .queryParam("serviceKey", properties.serviceKey())
                            .queryParam("MobileOS", "ETC")
                            .queryParam("MobileApp", "Ridely")
                            .queryParam("_type", "json")
                            .queryParam("numOfRows", numOfRows)
                            .queryParam("pageNo", pageNo);
                    // 값이 없으면 파라미터를 아예 보내지 않는다.
                    // 빈 문자열로 보내면 "전체"가 아니라 "빈 값과 일치"로 해석돼 0건이 나올 수 있다.
                    if (brdDiv != null && !brdDiv.isBlank()) {
                        b.queryParam("brdDiv", brdDiv);
                    }
                    if (routeIdx != null && !routeIdx.isBlank()) {
                        b.queryParam("routeIdx", routeIdx);
                    }
                    return b.build();
                })
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .block();

        verifyNotPortalError(body);
        return body;
    }

    /**
     * body.items.item을 목록으로 꺼낸다.
     * items가 빈 문자열이거나 item이 단일 객체인 경우를 모두 흡수한다.
     */
    private List<JsonNode> extractItems(JsonNode responseBody) {
        JsonNode items = responseBody.path("items");
        if (items.isMissingNode() || items.isNull() || items.isTextual()) {
            return List.of();   // 데이터 없음
        }
        JsonNode item = items.path("item");
        if (item.isMissingNode() || item.isNull()) {
            return List.of();
        }
        if (item.isArray()) {
            List<JsonNode> list = new ArrayList<>(item.size());
            item.forEach(list::add);
            return list;
        }
        return List.of(item);   // 1건이면 객체로 온다
    }

    private JsonNode readTree(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            log.error("두루누비 응답 파싱 실패: {}", abbreviate(body), e);
            throw new BusinessException(ErrorCode.COMMON_500);
        }
    }

    /**
     * 포털 레벨 오류 검사.
     * 키 미등록·활용기간 만료 등은 HTTP 200에 XML 본문으로 내려와 상태 코드로는 안 걸린다.
     */
    private void verifyNotPortalError(String body) {
        if (body == null || body.isBlank()) {
            log.error("두루누비 응답이 비어 있음");
            throw new BusinessException(ErrorCode.COMMON_500);
        }
        String head = body.stripLeading();
        if (head.startsWith(PORTAL_ERROR_PREFIX) || head.startsWith("<")) {
            // 원인(SERVICE_KEY_IS_NOT_REGISTERED_ERROR 등)이 본문에 있으므로 그대로 남긴다.
            // 두루누비를 아직 활용신청하지 않았다면 여기서 걸린다.
            log.error("두루누비 포털 오류 응답: {}", abbreviate(body));
            throw new BusinessException(ErrorCode.COMMON_500);
        }
    }

    private String abbreviate(String s) {
        if (s == null) {
            return "null";
        }
        return s.length() <= 500 ? s : s.substring(0, 500) + "…";
    }

    /** JsonNode에서 문자열 필드를 안전하게 꺼낸다 */
    public static String text(JsonNode node, String field) {
        JsonNode v = node == null ? MissingNode.getInstance() : node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }
}
