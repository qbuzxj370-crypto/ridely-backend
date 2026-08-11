package kr.ridely.infra.durunubi;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 두루누비 채택 판정 스파이크 (★ 임시 — 판정 후 삭제).
 *
 * <p>확인하려는 것은 하나다. <b>행안부 CSV + 거리 휴리스틱을 두루누비 GPX로 대체할 수 있는가.</b>
 *
 * <p>CSV에는 구간 경계가 없어서 우리는 "좌표 간격이 3km를 넘으면 끊긴 것"이라는
 * 자체 규칙을 만들어야 했다. GPX는 {@code <trkseg>}로 경계를 원본에 담는다.
 * 즉 채택되면 자체 규칙이 통째로 사라진다.
 *
 * <p>서비스 인터페이스를 두지 않은 이유: 판정용 일회성 코드라 구현이 바뀔 일이 없고,
 * 채택·기각 어느 쪽이든 이 패키지는 삭제된다 (ADR-010의 계층 규칙은 도메인 코드에 적용).
 */
@Service
public class DurunubiSpikeService {

    private static final Logger log = LoggerFactory.getLogger(DurunubiSpikeService.class);

    /** trkseg 수가 CSV 구간 수와 이만큼 이내로 차이나면 "일치"로 본다 */
    private static final int SEGMENT_TOLERANCE = 1;

    /** 전체 코스 이름 표본 상한. 기각 시 두루누비가 뭘 담고 있는지 보기 위한 것이라 전부는 필요 없다 */
    private static final int SAMPLE_NAME_LIMIT = 40;

    private final DurunubiClient client;
    private final GpxParser gpxParser;
    private final DurunubiProperties properties;

    public DurunubiSpikeService(DurunubiClient client, GpxParser gpxParser,
                                DurunubiProperties properties) {
        this.client = client;
        this.gpxParser = gpxParser;
        this.properties = properties;
    }

    // ------------------------------------------------------------------
    // S1 — 커버리지
    // ------------------------------------------------------------------

    /**
     * 두루누비에 국토종주 자전거 코스가 있는지 확인한다.
     *
     * <p>서버 필터를 쓰지 않는다. 실측에서 {@code routeList}는 0건이었고
     * {@code courseList}의 brdDiv 필터는 요청과 다른 값을 돌려줬다.
     * 코스를 전량 받아 응답의 brdDiv를 직접 세는 편이 유일하게 믿을 수 있다.
     */
    public DurunubiCoverageReport checkCoverage() {
        List<JsonNode> courses = client.fetchAllCourses();

        DurunubiCoverageReport report = new DurunubiCoverageReport();
        report.setTotalCourseCount(courses.size());

        Map<String, Integer> distribution = new LinkedHashMap<>();
        Map<Integer, DurunubiCoverageReport.MatchedCourse> matchedByCode = new LinkedHashMap<>();
        List<String> bikeNames = new ArrayList<>();
        List<String> sampleNames = new ArrayList<>();

        for (JsonNode course : courses) {
            String brdDiv = DurunubiClient.text(course, "brdDiv");
            String crsKorNm = DurunubiClient.text(course, "crsKorNm");
            distribution.merge(brdDiv == null ? "(없음)" : brdDiv, 1, Integer::sum);

            if (sampleNames.size() < SAMPLE_NAME_LIMIT) {
                sampleNames.add(crsKorNm);
            }
            // 이름 매칭은 자전거 코스에만 적용한다.
            // 걷기 코스에도 "섬진강"·"금강" 같은 토큰이 들어 있어 함께 훑으면 오탐이 난다.
            if (!DurunubiClient.BRD_DIV_BIKE.equals(brdDiv)) {
                continue;
            }
            bikeNames.add(crsKorNm);

            Optional<NationalRouteReference> ref = NationalRouteReference.find(crsKorNm);
            if (ref.isEmpty()) {
                continue;
            }
            NationalRouteReference r = ref.get();
            // 한 노선이 여러 코스로 쪼개져 있으면 첫 코스만 대표로 남긴다.
            // 전체 코스는 S2(analyzeGeometry)에서 다시 모은다.
            matchedByCode.putIfAbsent(r.getRouteCode(), new DurunubiCoverageReport.MatchedCourse(
                    r.getRouteCode(),
                    r.getOfficialName(),
                    DurunubiClient.text(course, "routeIdx"),
                    DurunubiClient.text(course, "crsIdx"),
                    crsKorNm,
                    DurunubiClient.text(course, "crsDstnc"),
                    brdDiv,
                    DurunubiClient.text(course, "gpxpath")
            ));
        }

        report.setBrdDivDistribution(distribution);
        report.setBikeCourseCount(bikeNames.size());
        report.setBikeCourseNames(bikeNames);
        report.setSampleCourseNames(sampleNames);
        report.setMatched(new ArrayList<>(matchedByCode.values()));
        report.setMatchedCount(matchedByCode.size());

        List<String> missing = new ArrayList<>();
        for (NationalRouteReference r : NationalRouteReference.values()) {
            if (!matchedByCode.containsKey(r.getRouteCode())) {
                missing.add(r.getOfficialName());
            }
        }
        report.setMissing(missing);
        report.setVerdict(coverageVerdict(courses.size(), bikeNames.size(), matchedByCode.size()));

        log.info("두루누비 커버리지: 코스 {}건, brdDiv 분포 {}, 자전거 {}건, 국토종주 매칭 {}개",
                courses.size(), distribution, bikeNames.size(), matchedByCode.size());
        return report;
    }

    private String coverageVerdict(int total, int bikeCount, int matched) {
        if (total == 0) {
            return "❌ 코스 응답이 0건이다. 활용신청 승인 상태를 먼저 확인할 것";
        }
        if (bikeCount == 0) {
            return "❌ 기각 — 코스 " + total + "건 중 자전거(DNBW)가 0건이다. "
                    + "두루누비는 코리아둘레길 계열 걷기 코스만 담고 있다. "
                    + "brdDivDistribution과 sampleCourseNames로 확인한 뒤 "
                    + "기존안(MultiLineString + 3km + 공식 거리)으로 복귀할 것";
        }
        if (matched == 0) {
            return "❌ 기각 — 자전거 코스 " + bikeCount + "건은 있으나 국토종주 13길이 없다. "
                    + "bikeCourseNames를 눈으로 확인할 것(이름이 달라 토큰 매칭이 빗나갔을 수 있다)";
        }
        if (matched < NationalRouteReference.values().length) {
            return "⚠️ 부분 커버리지 — 13개 중 " + matched + "개 매칭. "
                    + "MVP 구간(아라·한강종주)이 포함돼 있으면 S2 진행 가치가 있다";
        }
        return "✅ 13개 전부 매칭 — S2(GPX 형상 분석)로 진행할 것";
    }

    // ------------------------------------------------------------------
    // S2 — GPX 형상
    // ------------------------------------------------------------------

    /**
     * 매칭된 국토종주 노선의 코스와 GPX를 받아 우리 CSV 분석과 대조한다.
     *
     * @param onlyRouteCode 특정 노선만 볼 때 지정 (null이면 매칭된 전체).
     *                      MVP 구간만 빠르게 보려면 2(한강종주)를 넘긴다
     */
    public DurunubiGeometryReport analyzeGeometry(Integer onlyRouteCode) {
        DurunubiGeometryReport report = new DurunubiGeometryReport();

        // 코스를 한 번만 받아 노선별로 묶는다.
        // routeIdx로 다시 조회하지 않는 이유: routeList가 비어 있어 길 계층을 신뢰할 수 없고,
        // 같은 국토종주 노선의 코스들이 서로 다른 routeIdx에 흩어져 있을 수 있다.
        Map<Integer, List<JsonNode>> bikeCoursesByCode = new LinkedHashMap<>();
        for (JsonNode course : client.fetchAllCourses()) {
            if (!DurunubiClient.BRD_DIV_BIKE.equals(DurunubiClient.text(course, "brdDiv"))) {
                continue;
            }
            NationalRouteReference.find(DurunubiClient.text(course, "crsKorNm")).ifPresent(r ->
                    bikeCoursesByCode.computeIfAbsent(r.getRouteCode(), k -> new ArrayList<>()).add(course));
        }

        if (bikeCoursesByCode.isEmpty()) {
            report.setVerdict("❌ 매칭된 자전거 코스가 없다 — S2를 실행할 대상이 없다. "
                    + "/coverage 리포트를 먼저 볼 것");
            return report;
        }

        int downloads = 0;
        int succeeded = 0;
        int agree = 0;
        int evaluated = 0;
        boolean anySegmented = false;

        for (Map.Entry<Integer, List<JsonNode>> entry : bikeCoursesByCode.entrySet()) {
            int routeCode = entry.getKey();
            if (onlyRouteCode != null && routeCode != onlyRouteCode) {
                continue;
            }
            NationalRouteReference ref = NationalRouteReference.findByCode(routeCode)
                    .orElseThrow(() -> new IllegalStateException("기준표에 없는 노선 코드: " + routeCode));

            List<JsonNode> courses = entry.getValue();

            DurunubiGeometryReport.RouteGeometry rg = new DurunubiGeometryReport.RouteGeometry();
            rg.setRouteCode(routeCode);
            rg.setOfficialName(ref.getOfficialName());
            rg.setRouteIdx(DurunubiClient.text(courses.get(0), "routeIdx"));
            rg.setThemeNm(DurunubiClient.text(courses.get(0), "crsKorNm"));
            rg.setCsvPartsAt3km(ref.getCsvPartsAt3km());
            rg.setCsvLengthKm(ref.getCsvLengthKm());
            rg.setOfficialLengthKm(ref.getOfficialLengthKm());
            rg.setCourseCount(courses.size());

            double courseDistanceSum = 0;
            int trksegTotal = 0;
            double gpxLengthTotal = 0;

            for (JsonNode course : courses) {
                DurunubiGeometryReport.Course c = new DurunubiGeometryReport.Course();
                c.setCrsIdx(DurunubiClient.text(course, "crsIdx"));
                c.setCrsKorNm(DurunubiClient.text(course, "crsKorNm"));
                c.setCrsDstnc(DurunubiClient.text(course, "crsDstnc"));
                c.setCrsTotlRqrmHour(DurunubiClient.text(course, "crsTotlRqrmHour"));
                c.setCrsLevel(DurunubiClient.text(course, "crsLevel"));
                c.setCrsCycle(DurunubiClient.text(course, "crsCycle"));
                c.setSigun(DurunubiClient.text(course, "sigun"));
                c.setGpxPath(DurunubiClient.text(course, "gpxpath"));

                courseDistanceSum += parseDouble(c.getCrsDstnc());

                if (downloads < properties.maxGpxDownloads()) {
                    downloads++;
                    String xml = client.downloadGpx(c.getGpxPath());
                    GpxAnalysis analysis = xml == null
                            ? GpxAnalysis.failed("다운로드 실패")
                            : gpxParser.analyze(xml);
                    c.setGpx(analysis);
                    if (analysis.isParsed()) {
                        succeeded++;
                        trksegTotal += analysis.getTrksegCount();
                        gpxLengthTotal += analysis.getTotalLengthKm();
                        if (analysis.getTrksegCount() > 1) {
                            anySegmented = true;
                        }
                    }
                } else {
                    c.setGpx(GpxAnalysis.failed("다운로드 상한(" + properties.maxGpxDownloads() + ") 도달"));
                }
                rg.getCourses().add(c);
            }

            rg.setCourseDistanceSumKm(round1(courseDistanceSum));
            rg.setGpxTrksegTotal(trksegTotal);
            rg.setGpxLengthTotalKm(round1(gpxLengthTotal));
            rg.setNote(routeNote(rg));
            report.getRoutes().add(rg);

            if (trksegTotal > 0) {
                evaluated++;
                if (Math.abs(trksegTotal - ref.getCsvPartsAt3km()) <= SEGMENT_TOLERANCE) {
                    agree++;
                }
            }
        }

        report.setGpxAttempted(downloads);
        report.setGpxSucceeded(succeeded);
        report.setVerdict(geometryVerdict(evaluated, agree, anySegmented, succeeded));
        return report;
    }

    private String routeNote(DurunubiGeometryReport.RouteGeometry rg) {
        if (rg.getGpxTrksegTotal() == 0) {
            return "GPX 분석 실패 — 판정 불가";
        }
        int diff = Math.abs(rg.getGpxTrksegTotal() - rg.getCsvPartsAt3km());
        String segment = diff <= SEGMENT_TOLERANCE
                ? "구간 수 일치(GPX " + rg.getGpxTrksegTotal() + " vs CSV " + rg.getCsvPartsAt3km() + ")"
                : "구간 수 불일치(GPX " + rg.getGpxTrksegTotal() + " vs CSV " + rg.getCsvPartsAt3km() + ")";
        String length = String.format("길이 GPX %.1f / CSV %.1f / 공식 %d",
                rg.getGpxLengthTotalKm(), rg.getCsvLengthKm(), rg.getOfficialLengthKm());
        return segment + " · " + length;
    }

    private String geometryVerdict(int evaluated, int agree, boolean anySegmented, int succeeded) {
        if (succeeded == 0) {
            return "❌ GPX를 하나도 받지 못했다. gpxpath 접근 가능 여부를 먼저 확인할 것 "
                    + "(durunubi.kr이 차단하거나 파일이 없을 수 있다)";
        }
        if (!anySegmented) {
            return "⚠️ 모든 GPX의 trkseg가 1개다 — GPX도 구간 경계를 담고 있지 않다. "
                    + "두루누비를 써도 거리 휴리스틱이 여전히 필요하므로 채택 이유가 크게 준다. "
                    + "다만 crsDstnc·crsLevel·crsTourInfo는 여전히 가치가 있으니 부분 채택을 검토할 것";
        }
        if (evaluated > 0 && agree == evaluated) {
            return "✅ 채택 — GPX가 구간을 명시하고 우리 휴리스틱(3km)과 결과가 일치한다. "
                    + "CSV·임계값·SCHEMA_CHANGE_ROUTE_GEOM 제안서를 폐기하고 GPX를 정본으로 전환할 것";
        }
        return "⚠️ 일부 불일치 — " + evaluated + "개 중 " + agree + "개만 구간 수가 맞는다. "
                + "노선별 note를 확인할 것. 두 소스가 다른 형상을 담고 있다면 "
                + "스파이크를 연장하지 말고 MVP 구간(아라·한강종주)만 보고 판단할 것";
    }

    private double parseDouble(String s) {
        if (s == null || s.isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
