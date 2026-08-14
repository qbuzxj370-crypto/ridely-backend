package kr.ridely.infra.ors;

import kr.ridely.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * ★ 임시 PoC — ORS 실호출 검증용.
 *
 *  GET /api/v1/poc/ors/route?coords=126.8997,37.5434;126.9339,37.5265
 *  GET /api/v1/poc/ors/raw?coords=...
 *
 * 확인할 것
 *   1. 키가 통하는가. 401이면 ORS_API_KEY 미설정이거나 오타다
 *   2. 한강 자전거길을 실제로 타는가. 직선거리 대비 거리가 지나치게 짧으면 의심한다
 *   3. ascent 값이 얼마나 부풀려지는가. 강도 판정에서 뺀 근거를 재확인한다
 *
 * ⚠️ 무료 한도가 있다. 같은 좌표로 반복 호출하지 말 것.
 *
 * 정식 API(POST /routes/recommend)가 붙으면 이 컨트롤러는 삭제한다.
 */
@RestController
@RequestMapping("/api/v1/poc/ors")
public class OrsPocController {

    /** 좌표 쌍 구분자. `경도,위도;경도,위도` 형태로 받는다 */
    private static final String PAIR_DELIMITER = ";";
    private static final String VALUE_DELIMITER = ",";
    private static final int VALUES_PER_PAIR = 2;

    /** 검증 기본값 — 선유도공원 → 여의도한강공원. DATA_SOURCES 4장의 실측과 같은 구간이다 */
    private static final String DEFAULT_COORDS = "126.8997,37.5434;126.9339,37.5265";

    private final OrsClient orsClient;

    public OrsPocController(OrsClient orsClient) {
        this.orsClient = orsClient;
    }

    /**
     * 파싱까지 마친 결과를 돌려준다.
     *
     * @param coords `경도,위도;경도,위도` 형태. 생략하면 선유도 → 여의도
     */
    @GetMapping("/route")
    public ApiResponse<OrsRouteResult> route(
            @RequestParam(defaultValue = DEFAULT_COORDS) String coords) {
        return ApiResponse.ok(orsClient.route(parseCoordinates(coords)));
    }

    /** 응답 원문. 파싱에서 무엇이 빠지는지 볼 때 쓴다 */
    @GetMapping("/raw")
    public ApiResponse<String> raw(
            @RequestParam(defaultValue = DEFAULT_COORDS) String coords) {
        return ApiResponse.ok(orsClient.fetchRaw(parseCoordinates(coords)));
    }

    /**
     * `경도,위도;경도,위도` 문자열을 좌표 배열로 바꾼다.
     *
     * 경도를 먼저 쓰는 이유는 ORS 요청 본문의 순서와 맞추기 위해서다. 사람이 읽기엔 위도가 먼저인 게 익숙하지만, 여기서 순서를 바꾸면 실제 요청과 대조할 때 헷갈린다.
     */
    private List<double[]> parseCoordinates(String coords) {
        List<double[]> parsed = new ArrayList<>();
        for (String pair : coords.split(PAIR_DELIMITER)) {
            String[] values = pair.trim().split(VALUE_DELIMITER);
            if (values.length != VALUES_PER_PAIR) {
                throw new IllegalArgumentException(
                        "좌표 형식이 잘못됐다: '%s' (경도,위도 형태여야 한다)".formatted(pair));
            }
            parsed.add(new double[]{
                    Double.parseDouble(values[0].trim()),
                    Double.parseDouble(values[1].trim())
            });
        }
        return parsed;
    }
}
