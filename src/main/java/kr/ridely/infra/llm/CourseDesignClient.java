package kr.ridely.infra.llm;

import kr.ridely.dto.route.CandidateDTO;
import kr.ridely.dto.route.CourseDesignDTO;
import kr.ridely.dto.route.RouteCandidatesDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 코스 설계 LLM 클라이언트 (파이프라인 1단계).
 *
 * 후보 목록을 주면 "어디를 어떤 순서로 들를지"를 받는다. 좌표는 받지 않는다 — 실제 경로는 OrsClient가 그린다.
 */
@Component
public class CourseDesignClient {

    private static final Logger log = LoggerFactory.getLogger(CourseDesignClient.class);

    private static final String SYSTEM_PROMPT_FILE = "coach-ridely-system.txt";
    private static final String USER_PROMPT_FILE = "course-design-user.txt";

    /** 후보 목록의 섹션 제목. 프롬프트에서 종류를 구분해 보여줘야 한 종류로 쏠리지 않는다 */
    private static final String SECTION_TOUR = "관광지";
    private static final String SECTION_WATER = "급수대";
    private static final String SECTION_REPAIR_SHOP = "수리소";
    private static final String SECTION_BIKE_STATION = "따릉이 대여소";

    /**
     * 이름을 그대로 쓸 수 없는 종류.
     *
     * 급수대는 원본의 이름 컬럼에 시설명이 아니라 노선명이 들어 있어 한강 구간 전체가 "한강종주길"로 같다 (DATA_SOURCES 6.1의 5번). 이름으로 주면 LLM이 후보를 구분하지 못하고 이유 문장도 무의미해진다. 종류 이름으로 바꿔 부르고 위치는 진행 거리로 알린다.
     */
    private static final String TYPE_WATER = "WATER";

    private final PromptLoader promptLoader;
    private final StructuredLlmCaller llmCaller;
    private final LlmProperties properties;

    public CourseDesignClient(PromptLoader promptLoader, StructuredLlmCaller llmCaller,
                              LlmProperties properties) {
        this.promptLoader = promptLoader;
        this.llmCaller = llmCaller;
        this.properties = properties;
    }

    /**
     * 경유지를 고르게 한다.
     *
     * @param candidates       수집된 후보. 여기 없는 것은 고를 수 없다
     * @param targetDistanceKm 목표 주행 거리
     * @param circular         도착지 없이 출발지로 되돌아오는 코스인지
     */
    public CourseDesignDTO design(RouteCandidatesDTO candidates, double targetDistanceKm,
                                  boolean circular,
                                  BigDecimal convenience, BigDecimal exercise, BigDecimal scenery) {

        Map<String, String> values = new LinkedHashMap<>();
        values.put("TARGET_DISTANCE_KM", trimNumber(targetDistanceKm));
        values.put("ROUTE_SHAPE", circular ? "출발지로 되돌아오는 순환 코스" : "출발지에서 도착지까지 가는 편도 코스");
        values.put("PRIORITY_CONVENIENCE", convenience.toPlainString());
        values.put("PRIORITY_EXERCISE", exercise.toPlainString());
        values.put("PRIORITY_SCENERY", scenery.toPlainString());
        values.put("CANDIDATES", renderCandidates(candidates, targetDistanceKm));

        String userPrompt = promptLoader.render(USER_PROMPT_FILE, values);
        log.debug("코스 설계 프롬프트:\n{}", userPrompt);

        CourseDesignDTO design = llmCaller.call("코스 설계",
                promptLoader.load(SYSTEM_PROMPT_FILE), userPrompt,
                properties.designTemperature(), CourseDesignDTO.class);

        log.info("코스 설계 결과: 경유지 {}곳 — {}",
                design.waypointsOrEmpty().size(), design.getDesignIntent());
        return design;
    }

    /**
     * 후보를 프롬프트용 텍스트로 만든다.
     *
     * 한 줄에 번호·이름·진행 거리·이탈 거리를 넣는다. 진행 거리가 있어야 LLM이 순서대로 배치할 수 있고, 이탈 거리가 있어야 코스에서 얼마나 벗어나는지 판단할 수 있다. 둘은 다른 값이다.
     */
    private String renderCandidates(RouteCandidatesDTO candidates, double targetDistanceKm) {
        StringBuilder sb = new StringBuilder();
        appendSection(sb, SECTION_TOUR, candidates.getTours(), targetDistanceKm);
        appendSection(sb, SECTION_WATER, candidates.getWaters(), targetDistanceKm);
        appendSection(sb, SECTION_REPAIR_SHOP, candidates.getRepairShops(), targetDistanceKm);
        appendSection(sb, SECTION_BIKE_STATION, candidates.getBikeStations(), targetDistanceKm);
        return sb.toString();
    }

    private void appendSection(StringBuilder sb, String sectionTitle,
                               List<CandidateDTO> items, double targetDistanceKm) {
        if (items == null || items.isEmpty()) {
            return;
        }
        sb.append('[').append(sectionTitle).append("]\n");
        for (CandidateDTO c : items) {
            // type을 줄마다 박아야 한다. 섹션 제목만 주면 LLM이 그 한국어를 type에 그대로 써서
            // 후보 대조가 전부 실패한다(실측: "급수대"·"관광지"·"따릉이대여소"가 돌아왔다)
            sb.append("- type=").append(c.getType())
                    .append(" id=").append(c.getId())
                    .append(" | ").append(displayName(c, sectionTitle));

            Double progressKm = c.progressKm(targetDistanceKm);
            if (progressKm != null) {
                sb.append(" | ").append(trimNumber(progressKm)).append("km 지점");
            }
            sb.append(" | 경로에서 ").append(c.getDistanceM()).append("m\n");
        }
        sb.append('\n');
    }

    /** 급수대는 이름이 전부 같아 구분에 쓸 수 없다. 종류 이름으로 대신한다 */
    private String displayName(CandidateDTO candidate, String sectionTitle) {
        if (TYPE_WATER.equals(candidate.getType())) {
            return sectionTitle;
        }
        String name = candidate.getName();
        return name == null || name.isBlank() ? sectionTitle : name;
    }

    /** 12.0km를 "12"로 줄인다. 소수점이 남으면 LLM이 그대로 따라 써서 문장이 어색해진다 */
    private String trimNumber(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }
}
