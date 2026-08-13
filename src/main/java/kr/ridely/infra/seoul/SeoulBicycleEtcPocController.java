package kr.ridely.infra.seoul;

import kr.ridely.common.ApiResponse;
import kr.ridely.dto.poi.SeoulFacilityIngestResultDTO;
import kr.ridely.service.SeoulFacilityIngestService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * ★ 임시 PoC — 서울시 자전거 편의시설 데이터 분류 규칙 조사.
 *
 * GET /api/v1/poc/seoul/bicycle-etc/profile  전량 수집 후 값 분포 리포트
 *
 * 이 API는 시설 종류(거치대·공기주입기·수리센터)를 구분하는 전용 필드를 제공하지 않는다.
 * 적재 코드를 짜기 전에 어떤 필드로 종류를 판별할 수 있는지 실데이터로 확인해야 해서
 * 분포를 집계하는 엔드포인트를 먼저 만들었다.
 *
 * 분류 규칙이 확정되면(적재 서비스 구현 시) 이 컨트롤러는 삭제한다.
 */
@RestController
@RequestMapping("/api/v1/poc/seoul/bicycle-etc")
public class SeoulBicycleEtcPocController {

    /** 상세정보 항목 번호 (DTL_INFO_NM01~NM10 / VL01~VL10) */
    private static final List<String> DETAIL_NUMBERS =
            List.of("01", "02", "03", "04", "05", "06", "07", "08", "09", "10");

    /**
     * 수리센터 전용 상세정보 번호.
     * 명세상 항목명이 "(*수리센터만 선택하세요)"로 되어 있어, 값이 채워져 있으면 수리센터일 가능성이 높다.
     */
    private static final List<String> REPAIR_ONLY_NUMBERS = List.of("06", "07", "08");

    /** 시설명·시설ID에서 찾아볼 종류 키워드 */
    private static final List<String> TYPE_KEYWORDS =
            List.of("수리", "공기주입", "주입기", "거치대", "보관", "주차", "대여");

    /** 리포트에 남길 상위 개수 */
    private static final int TOP_N = 40;

    /** 분류 후보별로 붙일 예시 건수 */
    private static final int SAMPLE_SIZE = 5;

    private final SeoulOpenApiClient seoulOpenApiClient;
    private final SeoulFacilityIngestService seoulFacilityIngestService;

    public SeoulBicycleEtcPocController(SeoulOpenApiClient seoulOpenApiClient,
                                        SeoulFacilityIngestService seoulFacilityIngestService) {
        this.seoulOpenApiClient = seoulOpenApiClient;
        this.seoulFacilityIngestService = seoulFacilityIngestService;
    }

    /**
     * 공기주입기·수리센터를 적재한다.
     *
     * 공기주입기는 route_facility(AIR_PUMP), 수리센터는 repair_shop으로 나눠 들어간다.
     * 거치대·보관대는 넣지 않는다 — bike_parking 소스는 행안부 API로 확정돼 있다.
     *
     * 두 테이블 모두 자연키 UNIQUE가 있어 여러 번 실행해도 중복이 쌓이지 않는다.
     * 응답의 repairShops 목록은 23건뿐이니 적재 후 눈으로 한 번 훑어볼 것.
     */
    @PostMapping("/ingest")
    public ApiResponse<SeoulFacilityIngestResultDTO> ingest() {
        return ApiResponse.ok(seoulFacilityIngestService.ingestSeoulFacilities());
    }

    /**
     * 전량 수집 후 분류 판단에 필요한 분포를 집계한다.
     *
     * 반환 타입이 Map인 이유: 어떤 필드가 분류에 쓸모 있는지 모르는 상태의 일회성 조사라
     * 응답 구조를 고정할 수 없다. 규칙이 정해지면 이 코드는 사라진다.
     */
    @GetMapping("/profile")
    public ApiResponse<Map<String, Object>> profile() {
        List<SeoulBicycleEtcResponse.Row> rows = seoulOpenApiClient.fetchAllBicycleEtc();

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("수집건수", rows.size());
        report.put("사용여부", countBy(rows, SeoulBicycleEtcResponse.Row::getUseYn));
        report.put("테마타입", countBy(rows, SeoulBicycleEtcResponse.Row::getThemeType));
        report.put("테마명", countBy(rows, SeoulBicycleEtcResponse.Row::getThemeName));
        report.put("좌표결측", rows.stream().filter(r -> !hasCoordinate(r)).count());
        report.put("시설명분포", top(countBy(rows, r -> trimmed(r.getContentName()))));
        report.put("시설ID키워드", keywordCounts(rows, r -> r.getFacilityId()));
        report.put("시설명키워드", keywordCounts(rows, r -> r.getContentName()));
        report.put("상세정보항목", detailProfile(rows));
        report.put("수리센터후보", repairCandidates(rows));
        return ApiResponse.ok(report);
    }

    /** 값별 건수. 빈 값은 "(없음)"으로 모은다 */
    private Map<String, Long> countBy(List<SeoulBicycleEtcResponse.Row> rows,
                                      java.util.function.Function<SeoulBicycleEtcResponse.Row, String> extractor) {
        Map<String, Long> counts = new TreeMap<>();
        for (SeoulBicycleEtcResponse.Row row : rows) {
            String key = trimmed(extractor.apply(row));
            counts.merge(key.isEmpty() ? "(없음)" : key, 1L, Long::sum);
        }
        return counts;
    }

    /** 건수 내림차순 상위 N개만 남긴다 */
    private Map<String, Long> top(Map<String, Long> counts) {
        Map<String, Long> result = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(TOP_N)
                .forEach(e -> result.put(e.getKey(), e.getValue()));
        if (counts.size() > TOP_N) {
            result.put("(그 외 " + (counts.size() - TOP_N) + "종)", null);
        }
        return result;
    }

    /** 키워드를 포함하는 행이 몇 건인지 — 시설 종류를 문자열로 판별할 수 있는지 확인용 */
    private Map<String, Long> keywordCounts(List<SeoulBicycleEtcResponse.Row> rows,
                                            java.util.function.Function<SeoulBicycleEtcResponse.Row, String> extractor) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String keyword : TYPE_KEYWORDS) {
            counts.put(keyword, rows.stream()
                    .filter(r -> trimmed(extractor.apply(r)).contains(keyword))
                    .count());
        }
        counts.put("(키워드 없음)", rows.stream()
                .filter(r -> TYPE_KEYWORDS.stream().noneMatch(k -> trimmed(extractor.apply(r)).contains(k)))
                .count());
        return counts;
    }

    /**
     * 상세정보 번호별 프로파일.
     * 항목명이 전 건 동일한지(= 번호로 의미를 특정할 수 있는지)와
     * 값이 채워진 비율을 함께 본다.
     */
    private Map<String, Object> detailProfile(List<SeoulBicycleEtcResponse.Row> rows) {
        Map<String, Object> profile = new LinkedHashMap<>();
        for (String no : DETAIL_NUMBERS) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("항목명종류", countBy(rows, r -> r.detailName(no)).size());
            entry.put("대표항목명", top(countBy(rows, r -> r.detailName(no))).keySet().stream()
                    .findFirst().orElse("(없음)"));
            entry.put("값채워진건수", rows.stream().filter(r -> isFilled(r.detailValue(no))).count());
            entry.put("값분포", top(countBy(rows, r -> r.detailValue(no))));
            profile.put(no, entry);
        }
        return profile;
    }

    /**
     * 수리센터 전용 항목(06·07·08)이 하나라도 채워진 행.
     * 이 조건으로 수리센터를 골라낼 수 있는지 판단하기 위해 시설명 분포와 예시를 함께 낸다.
     */
    private Map<String, Object> repairCandidates(List<SeoulBicycleEtcResponse.Row> rows) {
        List<SeoulBicycleEtcResponse.Row> candidates = rows.stream()
                .filter(r -> REPAIR_ONLY_NUMBERS.stream().anyMatch(no -> isFilled(r.detailValue(no))))
                .sorted(Comparator.comparing(r -> trimmed(r.getContentName())))
                .toList();

        List<Map<String, String>> samples = new ArrayList<>();
        for (SeoulBicycleEtcResponse.Row row : candidates.stream().limit(SAMPLE_SIZE).toList()) {
            Map<String, String> sample = new LinkedHashMap<>();
            sample.put("시설ID", row.getFacilityId());
            sample.put("시설명", trimmed(row.getContentName()));
            sample.put("주소", row.getNewAddr());
            sample.put("좌표", row.getLng() + ", " + row.getLat());
            sample.put("운영시간(01)", row.detailValue("01"));
            sample.put("운영형태(06)", row.detailValue("06"));
            sample.put("수리범위(07)", row.detailValue("07"));
            sample.put("순회일정(08)", row.detailValue("08"));
            samples.add(sample);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("건수", candidates.size());
        result.put("시설명분포", top(countBy(candidates, r -> trimmed(r.getContentName()))));
        result.put("예시", samples);
        return result;
    }

    private boolean hasCoordinate(SeoulBicycleEtcResponse.Row row) {
        return isFilled(row.getLng()) && isFilled(row.getLat());
    }

    private boolean isFilled(String value) {
        return value != null && !value.isBlank();
    }

    private String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}
